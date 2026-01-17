package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.CompactWeatherDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.LoadDistributionDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.PlannedDayDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlan7dDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.DailySummary;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.DailyAggregationService;
import com.smartroute.smartroute1.service.FatigueAndOverloadService;
import com.smartroute.smartroute1.service.InjuryAwareTrainingService;
import com.smartroute.smartroute1.service.TrainingPlan7dService;
import com.smartroute.smartroute1.service.LoadForecaster;
import com.smartroute.smartroute1.service.ReadinessScoreService;
import com.smartroute.smartroute1.service.WeatherService;
import com.smartroute.smartroute1.util.ForecastState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
public class TrainingPlan7dServiceImpl implements TrainingPlan7dService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Vienna");

    private final UserRepository userRepository;
    private final DailyAggregationService dailyAggregationService;
    private final LoadForecaster loadForecaster;
    private final FatigueAndOverloadService fatigueAndOverloadService;
    private final Clock clock;
    private final InjuryAwareTrainingService injuryAwareTrainingService;
    private final ReadinessScoreService readinessScoreService;
    private final WeatherService weatherService;

    @Autowired
    public TrainingPlan7dServiceImpl(UserRepository userRepository,
                                     DailyAggregationService dailyAggregationService,
                                     LoadForecaster loadForecaster,
                                     FatigueAndOverloadService fatigueAndOverloadService,
                                     InjuryAwareTrainingService injuryAwareTrainingService,
                                     ReadinessScoreService readinessScoreService,
                                     WeatherService weatherService) {
        this(userRepository, dailyAggregationService, loadForecaster, fatigueAndOverloadService,
                Clock.system(ZoneId.of("Europe/Vienna")), injuryAwareTrainingService, readinessScoreService, weatherService);
    }

    public TrainingPlan7dServiceImpl(UserRepository userRepository,
                                     DailyAggregationService dailyAggregationService,
                                     LoadForecaster loadForecaster,
                                     FatigueAndOverloadService fatigueAndOverloadService,
                                     Clock clock,
                                     InjuryAwareTrainingService injuryAwareTrainingService,
                                     ReadinessScoreService readinessScoreService,
                                     WeatherService weatherService) {
        this.userRepository = userRepository;
        this.dailyAggregationService = dailyAggregationService;
        this.loadForecaster = loadForecaster;
        this.fatigueAndOverloadService = fatigueAndOverloadService;
        this.clock = clock;
        this.injuryAwareTrainingService = injuryAwareTrainingService;
        this.readinessScoreService = readinessScoreService;
        this.weatherService = weatherService;
    }

    @Override
    public TrainingPlan7dDto buildNext7Days(String email, double latitude, double longitude) {
        ApplicationUser user = userRepository.findUserByEmail(email);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        LocalDate today = LocalDate.now(clock);

        double injuryIndex = safe(() -> injuryAwareTrainingService.getInjuryIndex(email), 0.0);
        int readiness = safeInt(() -> readinessScoreService.calculateReadinessScore(user, today), 50);
        final List<CompactWeatherDto> weatherPerDay = precomputeWeather(today, latitude, longitude, 18);

        // Historical daily series for personalization (overall load)
        final List<DailySummary> history = dailyAggregationService.getDailySummaries(user, 60);
        final List<Integer> recentLoads = history.stream().map(DailySummary::getTotalLoad).toList();

        // Current state from your existing fatigue service (already computed from history)
        double ctl = safe(() -> fatigueAndOverloadService.currentCtl(user), 0.0);
        double atl = safe(() -> fatigueAndOverloadService.currentAtl(user), 0.0);
        final ForecastState initialState = new ForecastState(ctl, atl);

        // Candidate weekly templates including gym/mobility
        List<List<WorkoutType>> templates = generateTemplates(user);
        templates = applyActiveWeekdayConstraints(user, today, templates, injuryIndex);


        // Choose best via simple Monte Carlo utility
        PlanResult best = chooseBestPlan(user, today, templates, initialState, recentLoads, injuryIndex, readiness, weatherPerDay);

        return new TrainingPlan7dDto(best.days);
    }

    private List<List<WorkoutType>> generateTemplates(ApplicationUser user) {
        return List.of(
                List.of(WorkoutType.EASY_RUN, WorkoutType.MOBILITY, WorkoutType.TEMPO_RUN, WorkoutType.REST_DAY, WorkoutType.GYM_PREHAB, WorkoutType.LONG_RUN, WorkoutType.EASY_RUN),
                List.of(WorkoutType.EASY_RUN, WorkoutType.INTERVAL_RUN, WorkoutType.MOBILITY, WorkoutType.REST_DAY, WorkoutType.TEMPO_RUN, WorkoutType.GYM_PREHAB, WorkoutType.LONG_RUN),
                List.of(WorkoutType.MOBILITY, WorkoutType.EASY_RUN, WorkoutType.GYM_PREHAB, WorkoutType.REST_DAY, WorkoutType.EASY_RUN, WorkoutType.LONG_RUN, WorkoutType.REST_DAY),
                List.of(WorkoutType.EASY_RUN, WorkoutType.GYM_PREHAB, WorkoutType.REST_DAY, WorkoutType.TEMPO_RUN, WorkoutType.MOBILITY, WorkoutType.LONG_RUN, WorkoutType.REST_DAY)
        );
    }

    private PlanResult chooseBestPlan(ApplicationUser user,
                                      LocalDate startDate,
                                      List<List<WorkoutType>> templates,
                                      ForecastState initialState,
                                      List<Integer> recentLoads,
                                      double injuryIndex,
                                      int readiness,
                                      List<CompactWeatherDto> weatherPerDay) {

        Random rng = new Random(42);

        double bestScore = Double.NEGATIVE_INFINITY;
        List<WorkoutType> bestTemplate = null;

        // store the tsb distributions for the best plan
        List<LoadDistributionDto> bestTsbDists = null;

        for (List<WorkoutType> template : templates) {
            int sims = 120; // bump a bit for smoother quantiles

            // Collect TSB samples for each day across simulations
            List<List<Double>> tsbSamplesPerDay = new ArrayList<>(7);
            for (int i = 0; i < 7; i++) {
                tsbSamplesPerDay.add(new ArrayList<>(sims));
            }

            double totalUtility = 0;

            for (int s = 0; s < sims; s++) {
                ForecastState st = initialState;
                double utility = 0;

                for (int i = 0; i < 7; i++) {
                    LocalDate d = startDate.plusDays(i);

                    WorkoutType planned = template.get(i);
                    CompactWeatherDto weatherDto = weatherPerDay.get(i);

                    WorkoutType effective = effectiveWorkoutType(planned, injuryIndex, readiness, weatherDto.getWeatherScore());

                    LoadDistributionDto loadDist = loadForecaster.forecastLoad(user, d, effective, st, recentLoads);

                    double loadSample = sampleNonNegativeNormal(rng, loadDist.getMean(), loadDist.getStd());
                    st = st.next(loadSample);

                    tsbSamplesPerDay.get(i).add(st.tsb());

                    utility += trainingReward(effective, loadSample);
                    utility -= fatiguePenalty(st.tsb(), effective);
                    utility -= injuryPenalty(injuryIndex, effective);
                    utility -= readinessPenalty(readiness, effective);

                    utility -= weatherPenalty(weatherDto.getWeatherScore(), effective);
                }


                totalUtility += utility;
            }

            double avgUtility = totalUtility / sims;

            if (avgUtility > bestScore) {
                bestScore = avgUtility;
                bestTemplate = template;

                // Convert TSB samples -> distributions
                List<LoadDistributionDto> tsbDists = new ArrayList<>(7);
                for (int i = 0; i < 7; i++) {
                    tsbDists.add(toDistribution(tsbSamplesPerDay.get(i)));
                }
                bestTsbDists = tsbDists;
            }
        }

        if (bestTemplate == null) {
            // fallback: rest week
            bestTemplate = List.of(
                    WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY,
                    WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY,
                    WorkoutType.REST_DAY
            );
            bestTsbDists = List.of(
                    new LoadDistributionDto(0, 0, 0, 0, 0),
                    new LoadDistributionDto(0, 0, 0, 0, 0),
                    new LoadDistributionDto(0, 0, 0, 0, 0),
                    new LoadDistributionDto(0, 0, 0, 0, 0),
                    new LoadDistributionDto(0, 0, 0, 0, 0),
                    new LoadDistributionDto(0, 0, 0, 0, 0),
                    new LoadDistributionDto(0, 0, 0, 0, 0)
            );
        }

        // Build display plan using mean loads (stable) BUT attach the sampled TSB distributions
        List<PlannedDayDto> days = materializePlanWithTsbDists(user, startDate, bestTemplate, initialState,
                recentLoads, bestTsbDists, injuryIndex, readiness, weatherPerDay);

        return new PlanResult(days, bestTsbDists);
    }


    private List<PlannedDayDto> materializePlanWithTsbDists(ApplicationUser user,
                                                            LocalDate startDate,
                                                            List<WorkoutType> template,
                                                            ForecastState initialState,
                                                            List<Integer> recentLoads,
                                                            List<LoadDistributionDto> tsbDists,
                                                            double injuryIndex,
                                                            int readiness,
                                                            List<CompactWeatherDto> weatherPerDay) {

        List<PlannedDayDto> out = new ArrayList<>(7);
        ForecastState st = initialState;

        for (int i = 0; i < 7; i++) {
            LocalDate d = startDate.plusDays(i);

            CompactWeatherDto weatherDto = weatherPerDay.get(i);

            WorkoutType planned = template.get(i);

            WorkoutType effective = effectiveWorkoutType(planned, injuryIndex, readiness, weatherDto.getWeatherScore());


            LoadDistributionDto load = loadForecaster.forecastLoad(user, d, effective, st, recentLoads);

            st = st.next(load.getMean());

            out.add(new PlannedDayDto(
                    d,
                    effective,
                    load,
                    tsbDists.get(i),
                    weatherDto,
                    confidenceFromStd(load),
                    explanation(effective, load, tsbDists.get(i).getP50(), injuryIndex, readiness)
            ));
        }

        return out;
    }


    private String confidenceFromStd(LoadDistributionDto d) {
        double frac = d.getStd() / Math.max(1.0, d.getMean());
        if (frac < 0.18) {
            return "high";
        }
        if (frac < 0.32) {
            return "medium";
        }
        return "low";
    }

    private List<String> explanation(WorkoutType wt, LoadDistributionDto load, double tsb, double injuryIndex, int readiness) {
        List<String> e = new ArrayList<>();
        e.add("Planned: " + wt.toString());
        e.add("Expected load ≈ " + Math.round(load.getMean()) + " (P10 " + Math.round(load.getP10()) + " – P90 " + Math.round(load.getP90()) + ")");
        e.add("Readiness today: " + readiness + "/100");
        if (injuryIndex >= 0.7) {
            e.add("Injury-aware: intensity reduced.");
        }
        if (tsb < -20) {
            e.add("Fatigue risk: predicted TSB very low.");
        } else if (tsb < -10) {
            e.add("Fatigue: predicted TSB somewhat low.");
        } else {
            e.add("Recovery: predicted TSB acceptable.");
        }
        return e;
    }


    private double trainingReward(WorkoutType wt, double loadSample) {
        // Small positive reward for doing something, zero for rest
        return switch (wt) {
            case REST_DAY -> 0.0;
            case MOBILITY -> 3.0;
            case GYM_PREHAB -> 5.0;
            default -> 8.0 + 0.02 * loadSample; // runs benefit more with load
        };
    }

    private double fatiguePenalty(double tsb, WorkoutType wt) {
        // Penalize hard sessions when fatigue is high
        double p = 0.0;
        if (tsb < -25) {
            p += 25;
        } else if (tsb < -15) {
            p += 12;
        } else if (tsb < -10) {
            p += 6;
        }

        // extra penalty if intensity day
        if ((wt == WorkoutType.INTERVAL_RUN || wt == WorkoutType.TEMPO_RUN || wt == WorkoutType.LONG_RUN) && tsb < -10) {
            p += 6;
        }
        return p;
    }

    private double sampleNonNegativeNormal(Random rng, double mean, double std) {
        if (std <= 0) {
            return Math.max(0, mean);
        }
        double u1 = Math.max(1e-12, rng.nextDouble());
        double u2 = rng.nextDouble();
        double z = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
        return Math.max(0, mean + std * z);
    }

    private double safe(SupplierWithException<Double> s, double fallback) {
        try {
            return s.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    private LoadDistributionDto toDistribution(List<Double> samples) {
        if (samples == null || samples.isEmpty()) {
            return new LoadDistributionDto(0, 0, 0, 0, 0);
        }
        List<Double> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);

        double mean = sorted.stream().mapToDouble(x -> x).average().orElse(0);
        double std = std(sorted, mean);

        double p10 = quantile(sorted, 0.10);
        double p50 = quantile(sorted, 0.50);
        double p90 = quantile(sorted, 0.90);

        return new LoadDistributionDto(p10, p50, p90, mean, std);
    }

    private double quantile(List<Double> sorted, double q) {
        int n = sorted.size();
        if (n == 1) {
            return sorted.get(0);
        }
        double pos = q * (n - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        if (lo == hi) {
            return sorted.get(lo);
        }
        double w = pos - lo;
        return sorted.get(lo) * (1 - w) + sorted.get(hi) * w;
    }

    private double std(List<Double> values, double mean) {
        if (values.size() < 2) {
            return 0;
        }
        double var = 0;
        for (double v : values) {
            double d = v - mean;
            var += d * d;
        }
        var /= (values.size() - 1);
        return Math.sqrt(var);
    }

    private List<List<WorkoutType>> applyInjuryConstraints(List<List<WorkoutType>> templates, double injuryIndex) {
        if (injuryIndex < 0.4) {
            return templates;
        }

        List<List<WorkoutType>> out = new ArrayList<>();
        for (List<WorkoutType> t : templates) {
            List<WorkoutType> copy = new ArrayList<>(t.size());
            for (WorkoutType wt : t) {
                copy.add(mapWorkoutForInjury(wt, injuryIndex));
            }
            out.add(copy);
        }
        return out;
    }

    private WorkoutType mapWorkoutForInjury(WorkoutType wt, double injuryIndex) {
        if (injuryIndex >= 0.7) {
            return switch (wt) {
                case INTERVAL_RUN, TEMPO_RUN -> WorkoutType.MOBILITY;
                case LONG_RUN -> WorkoutType.EASY_RUN;
                default -> wt;
            };
        } else {
            return switch (wt) {
                case INTERVAL_RUN -> WorkoutType.TEMPO_RUN;
                default -> wt;
            };
        }
    }

    private double injuryPenalty(double injuryIndex, WorkoutType wt) {
        if (injuryIndex < 0.4) {
            return 0.0;
        }

        double p = 0.0;
        boolean hardRun = (wt == WorkoutType.INTERVAL_RUN || wt == WorkoutType.TEMPO_RUN || wt == WorkoutType.LONG_RUN);

        if (injuryIndex >= 0.7) {
            if (hardRun) {
                p += 30;
            }
            if (wt == WorkoutType.EASY_RUN) {
                p += 8; // still some penalty
            }
        } else { // 0.4 - 0.7
            if (wt == WorkoutType.INTERVAL_RUN) {
                p += 18;
            }
            if (wt == WorkoutType.TEMPO_RUN) {
                p += 8;
            }
        }
        return p;
    }

    private boolean isActiveDay(ApplicationUser user, LocalDate date) {
        if (user.getActiveWeekdays() == null || user.getActiveWeekdays().isEmpty()) {
            return true; // no preference set => allow all days
        }

        java.time.DayOfWeek d = date.getDayOfWeek();

        // Map DayOfWeek -> Weekday enum
        com.smartroute.smartroute1.entity.enums.Weekday wd =
                com.smartroute.smartroute1.entity.enums.Weekday.valueOf(d.name());

        return user.getActiveWeekdays().contains(wd);
    }

    private List<List<WorkoutType>> applyActiveWeekdayConstraints(ApplicationUser user, LocalDate startDate, List<List<WorkoutType>> templates, double injuryIndex) {

        List<List<WorkoutType>> out = new ArrayList<>();

        for (List<WorkoutType> t : templates) {
            List<WorkoutType> copy = new ArrayList<>(t);

            for (int i = 0; i < 7; i++) {
                LocalDate date = startDate.plusDays(i);

                if (!isActiveDay(user, date)) {
                    copy.set(i, injuryIndex >= 0.4 ? WorkoutType.MOBILITY : WorkoutType.REST_DAY);
                }
            }
            out.add(copy);
        }
        return out;
    }

    private int safeInt(SupplierWithException<Integer> s, int fallback) {
        try {
            return s.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    private double readinessPenalty(int readiness, WorkoutType wt) {
        // readiness is 0..100
        if (readiness >= 70) {
            return 0.0;
        }

        boolean hardRun = (wt == WorkoutType.INTERVAL_RUN || wt == WorkoutType.TEMPO_RUN || wt == WorkoutType.LONG_RUN);

        if (readiness < 40) {
            // very low readiness: strongly avoid intensity
            if (hardRun) {
                return 25.0;
            }
            if (wt == WorkoutType.EASY_RUN) {
                return 8.0;
            }
            return 0.0; // mobility/gym/rest fine
        }

        // 40..69: moderate caution
        if (hardRun) {
            return 10.0;
        }
        if (wt == WorkoutType.EASY_RUN && readiness < 55) {
            return 3.0;
        }

        return 0.0;
    }

    private List<List<WorkoutType>> applyReadinessConstraints(List<List<WorkoutType>> templates, int readiness) {
        if (readiness >= 55) {
            return templates;
        }

        List<List<WorkoutType>> out = new ArrayList<>();
        for (List<WorkoutType> template : templates) {
            List<WorkoutType> adjusted = new ArrayList<>(template.size());
            for (WorkoutType wt : template) {
                adjusted.add(mapWorkoutForReadiness(wt, readiness));
            }
            out.add(adjusted);
        }
        return out;
    }

    private WorkoutType mapWorkoutForReadiness(WorkoutType workoutType, int readiness) {
        if (readiness < 40) {
            return mapForVeryLowReadiness(workoutType);
        }
        return mapForModerateReadiness(workoutType);
    }

    private WorkoutType mapForVeryLowReadiness(WorkoutType wt) {
        return switch (wt) {
            case INTERVAL_RUN, TEMPO_RUN, LONG_RUN -> WorkoutType.MOBILITY;
            default -> wt;
        };
    }

    private WorkoutType mapForModerateReadiness(WorkoutType wt) {
        return switch (wt) {
            case INTERVAL_RUN -> WorkoutType.MOBILITY;
            case TEMPO_RUN, LONG_RUN -> WorkoutType.EASY_RUN;
            default -> wt;
        };
    }

    private String utcTimeStringFor(LocalDate date, int localHour) {
        // local date/time in Vienna
        var local = date.atTime(localHour, 0).atZone(ZONE);
        var utc = local.withZoneSameInstant(ZoneId.of("UTC"));
        return utc.toLocalDateTime().withMinute(0).withSecond(0).withNano(0)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    }

    private CompactWeatherDto buildCompactWeatherDtoForDay(
            LocalDate day,
            double latitude,
            double longitude,
            int localHour
    ) {
        // default fallback weather response + score if anything fails
        try {
            String timeUtc = utcTimeStringFor(day, localHour);

            WeatherResponse wr = weatherService.getWeatherAtTime(latitude, longitude, timeUtc);
            double weatherScore = weatherService.calculateWeatherScore(wr);

            return new CompactWeatherDto(
                    weatherScore,
                    wr.getTemperature2m(),
                    wr.getWindSpeed10m(),
                    wr.getPrecipitation(),
                    wr.getRelativeHumidity(),
                    weatherService.estimatePerformancePenalty(wr),
                    weatherService.evaluateWeatherScore(weatherScore),
                    weatherService.buildWeatherDescription(wr)
            );
        } catch (Exception e) {
            return new CompactWeatherDto(
                    0.6,
                    null, null, null, null, null,
                    "Weather unavailable",
                    null
            );

        }
    }

    private double weatherPenalty(Double weatherScore, WorkoutType wt) {
        // only penalize outdoor runs
        boolean outdoorRun = (wt == WorkoutType.EASY_RUN || wt == WorkoutType.TEMPO_RUN
                || wt == WorkoutType.INTERVAL_RUN || wt == WorkoutType.LONG_RUN);

        if (!outdoorRun) {
            return 0.0;
        }

        if (weatherScore >= 0.7) {
            return 0.0; // very good
        }
        if (weatherScore >= 0.5) {
            return 2.0; // acceptable
        }
        if (weatherScore >= 0.3) {
            return 8.0; // bad
        }
        return 18.0; // very bad
    }

    private WorkoutType mapWorkoutForWeather(WorkoutType wt, Double weatherScore) {
        boolean run = (wt == WorkoutType.EASY_RUN || wt == WorkoutType.TEMPO_RUN
                || wt == WorkoutType.INTERVAL_RUN || wt == WorkoutType.LONG_RUN);

        if (!run) {
            return wt;
        }

        if (weatherScore < 0.2) {
            return WorkoutType.GYM_PREHAB;  // too dangerous outside
        }
        if (weatherScore < 0.3) {
            return WorkoutType.MOBILITY;    // very unpleasant
        }
        return wt;
    }

    private List<CompactWeatherDto> precomputeWeather(LocalDate startDate, double latitude, double longitude, int localHour) {
        List<CompactWeatherDto> weather = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate d = startDate.plusDays(i);
            weather.add(buildCompactWeatherDtoForDay(d, latitude, longitude, localHour));
        }
        return weather;
    }

    private WorkoutType effectiveWorkoutType(WorkoutType planned, double injuryIndex, int readiness, Double weatherScore) {

        WorkoutType wt = planned;

        wt = mapWorkoutForInjury(wt, injuryIndex);
        wt = mapWorkoutForReadiness(wt, readiness);

        wt = mapWorkoutForWeather(wt, weatherScore);

        return wt;
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    private record PlanResult(List<PlannedDayDto> days,
                              List<LoadDistributionDto> tsbDistributions) {
    }

}
