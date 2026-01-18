package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.CompactWeatherDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.LoadDistributionDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.PlannedDayDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlan7dDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.DailySummary;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlanDebugDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.TemplateScoreDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.DayDebugDto;
import com.smartroute.smartroute1.endpoint.dto.GymWorkoutDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Injuries;
import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.entity.enums.BodyPart;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.DailyAggregationService;
import com.smartroute.smartroute1.service.FatigueAndOverloadService;
import com.smartroute.smartroute1.service.InjuryAwareTrainingService;
import com.smartroute.smartroute1.service.TrainingPlan7dService;
import com.smartroute.smartroute1.service.LoadForecaster;
import com.smartroute.smartroute1.service.ReadinessScoreService;
import com.smartroute.smartroute1.service.WeatherService;
import com.smartroute.smartroute1.service.DaySelectorService;
import com.smartroute.smartroute1.service.GymWorkoutSelectorService;
import com.smartroute.smartroute1.service.TrainingPlanStore;
import com.smartroute.smartroute1.util.ForecastState;
import com.smartroute.smartroute1.util.LoadConstraints;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.smartroute.smartroute1.endpoint.dto.RouteDto;
import com.smartroute.smartroute1.service.RouteGenerationService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

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
    private final DaySelectorService daySelectorService;
    private final GymWorkoutSelectorService gymWorkoutSelectorService;
    private final RouteGenerationService routeGenerationService;
    private final TrainingPlanStore trainingPlanStore;

    @Autowired
    public TrainingPlan7dServiceImpl(UserRepository userRepository,
                                     DailyAggregationService dailyAggregationService,
                                     LoadForecaster loadForecaster,
                                     FatigueAndOverloadService fatigueAndOverloadService,
                                     InjuryAwareTrainingService injuryAwareTrainingService,
                                     ReadinessScoreService readinessScoreService,
                                     WeatherService weatherService,
                                     DaySelectorService daySelectorService,
                                     GymWorkoutSelectorService gymWorkoutSelectorService,
                                     RouteGenerationService routeGenerationService,
                                     TrainingPlanStore trainingPlanStore) {
        this(userRepository, dailyAggregationService, loadForecaster, fatigueAndOverloadService,
                Clock.system(ZoneId.of("Europe/Vienna")), injuryAwareTrainingService, readinessScoreService,
                weatherService, daySelectorService, gymWorkoutSelectorService, routeGenerationService, trainingPlanStore);
    }

    public TrainingPlan7dServiceImpl(UserRepository userRepository,
                                     DailyAggregationService dailyAggregationService,
                                     LoadForecaster loadForecaster,
                                     FatigueAndOverloadService fatigueAndOverloadService,
                                     Clock clock,
                                     InjuryAwareTrainingService injuryAwareTrainingService,
                                     ReadinessScoreService readinessScoreService,
                                     WeatherService weatherService,
                                     DaySelectorService daySelectorService,
                                     GymWorkoutSelectorService gymWorkoutSelectorService,
                                     RouteGenerationService routeGenerationService,
                                     TrainingPlanStore trainingPlanStore) {
        this.userRepository = userRepository;
        this.dailyAggregationService = dailyAggregationService;
        this.loadForecaster = loadForecaster;
        this.fatigueAndOverloadService = fatigueAndOverloadService;
        this.clock = clock;
        this.injuryAwareTrainingService = injuryAwareTrainingService;
        this.readinessScoreService = readinessScoreService;
        this.weatherService = weatherService;
        this.daySelectorService = daySelectorService;
        this.gymWorkoutSelectorService = gymWorkoutSelectorService;
        this.routeGenerationService = routeGenerationService;
        this.trainingPlanStore = trainingPlanStore;
    }

    @Override
    public TrainingPlan7dDto buildNext7Days(String email, double latitude, double longitude, boolean debug, Integer simsParam, Long seedParam) {
        ApplicationUser user = userRepository.findUserByEmail(email);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        int sims = clampSims(simsParam);
        long seed = defaultSeed(seedParam);

        LocalDate today = LocalDate.now(clock);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        String planId = "week:" + weekStart;

        Optional<TrainingPlan7dDto> cached = trainingPlanStore.get(email, planId);
        if (cached.isPresent()) {
            return cached.get();
        }

        double injuryIndex = safe(() -> injuryAwareTrainingService.getInjuryIndex(email), 0.0);

        List<Injuries> injuries = safeList(() -> injuryAwareTrainingService.findInjuriesByEmail(email));
        Map<BodyPart, Double> injuriesMap = injuryAwareTrainingService.calculateInjuriesMap(injuries);

        LoadConstraints constraints = new LoadConstraints(
                injuryAwareTrainingService.calculateIntensityScaling(injuryIndex),
                injuryAwareTrainingService.calculateVolumeScaling(injuryIndex),
                injuryAwareTrainingService.calculateHighImpactPenalty(injuryIndex)
        );

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


        // Choose best via simple Monte Carlo utility
        PlanChoice choice = chooseBestPlan(
                user,
                today,
                templates,
                initialState,
                recentLoads,
                injuryIndex,
                readiness,
                weatherPerDay,
                constraints,
                sims,
                seed,
                debug
        );

        List<PlannedDayDto> days = materializePlanWithTsbDists(
                user,
                today,
                choice.bestTemplate(),
                initialState,
                recentLoads,
                choice.bestTsbDists(),
                injuryIndex,
                injuriesMap,
                readiness,
                weatherPerDay,
                constraints,
                planId
        );

        TrainingPlan7dDto dto = new TrainingPlan7dDto(days);
        dto.setPlanId(planId);
        dto.setDebug(choice.debug());

        trainingPlanStore.put(email, planId, dto);
        return dto;
    }

    private List<List<WorkoutType>> generateTemplates(ApplicationUser user) {
        return List.of(
                List.of(WorkoutType.EASY_RUN, WorkoutType.MOBILITY, WorkoutType.TEMPO_RUN, WorkoutType.REST_DAY, WorkoutType.GYM_PREHAB, WorkoutType.LONG_RUN, WorkoutType.EASY_RUN),
                List.of(WorkoutType.EASY_RUN, WorkoutType.INTERVAL_RUN, WorkoutType.MOBILITY, WorkoutType.REST_DAY, WorkoutType.TEMPO_RUN, WorkoutType.GYM_PREHAB, WorkoutType.LONG_RUN),
                List.of(WorkoutType.MOBILITY, WorkoutType.EASY_RUN, WorkoutType.GYM_PREHAB, WorkoutType.REST_DAY, WorkoutType.EASY_RUN, WorkoutType.LONG_RUN, WorkoutType.REST_DAY),
                List.of(WorkoutType.EASY_RUN, WorkoutType.GYM_PREHAB, WorkoutType.REST_DAY, WorkoutType.TEMPO_RUN, WorkoutType.MOBILITY, WorkoutType.LONG_RUN, WorkoutType.REST_DAY)
        );
    }

    private PlanChoice chooseBestPlan(ApplicationUser user,
                                      LocalDate startDate,
                                      List<List<WorkoutType>> templates,
                                      ForecastState initialState,
                                      List<Integer> recentLoads,
                                      double injuryIndex,
                                      int readiness,
                                      List<CompactWeatherDto> weatherPerDay,
                                      LoadConstraints constraints,
                                      int sims,
                                      long seed,
                                      boolean debug) {

        Random rng = new Random(seed);

        List<TemplateScoreDto> templateScores = debug ? new ArrayList<>() : null;

        double bestScore = Double.NEGATIVE_INFINITY;
        int bestTemplateIndex = -1;
        List<WorkoutType> bestTemplate = null;
        List<LoadDistributionDto> bestTsbDists = null;

        for (int templateIndex = 0; templateIndex < templates.size(); templateIndex++) {
            List<WorkoutType> template = templates.get(templateIndex);

            // Collect TSB samples for each day across simulations
            List<List<Double>> tsbSamplesPerDay = new ArrayList<>(7);
            for (int i = 0; i < 7; i++) {
                tsbSamplesPerDay.add(new ArrayList<>(sims));
            }

            double totalUtility = 0.0;

            for (int s = 0; s < sims; s++) {
                ForecastState st = initialState;
                double utility = 0.0;

                for (int i = 0; i < 7; i++) {
                    LocalDate d = startDate.plusDays(i);

                    CompactWeatherDto weatherDto = weatherPerDay.get(i);

                    // 1) decide if user should train that day
                    boolean trainDay = daySelectorService.isTrainingDay(d, user);

                    // 2) planned workout comes from template only if training day
                    WorkoutType planned = trainDay
                            ? template.get(i)
                            : (injuryIndex >= 0.4 ? WorkoutType.MOBILITY : WorkoutType.REST_DAY);

                    // 3) apply injury/readiness/weather mapping
                    WorkoutType effective = effectiveWorkoutType(
                            planned,
                            injuryIndex,
                            readiness,
                            weatherDto == null ? null : weatherDto.getWeatherScore()
                    );

                    LoadDistributionDto loadDist = loadForecaster.forecastLoad(user, d, effective, st, recentLoads, constraints);

                    double loadSample = sampleNonNegativeNormal(rng, loadDist.getMean(), loadDist.getStd());

                    // forward update
                    st = st.next(loadSample);

                    // record tsb distribution
                    tsbSamplesPerDay.get(i).add(st.tsb());

                    // utility
                    utility += trainingReward(effective, loadSample);
                    utility -= fatiguePenalty(st.tsb(), effective);
                    utility -= injuryPenalty(injuryIndex, effective);
                    utility -= readinessPenalty(readiness, effective);
                    utility -= weatherPenalty(weatherDto == null ? null : weatherDto.getWeatherScore(), effective);
                }

                totalUtility += utility;
            }

            double avgUtility = totalUtility / sims;

            // Build effective template for debug output (deterministic, based on inputs)
            if (debug) {
                List<WorkoutType> effectiveTemplate = new ArrayList<>(7);
                List<WorkoutType> plannedTemplate = new ArrayList<>(7);

                for (int i = 0; i < 7; i++) {
                    LocalDate d = startDate.plusDays(i);
                    CompactWeatherDto w = weatherPerDay.get(i);
                    Double ws = (w == null) ? null : w.getWeatherScore();

                    boolean trainDay = daySelectorService.isTrainingDay(d, user);

                    WorkoutType planned = trainDay
                            ? template.get(i)
                            : (injuryIndex >= 0.4 ? WorkoutType.MOBILITY : WorkoutType.REST_DAY);

                    plannedTemplate.add(planned);
                    effectiveTemplate.add(effectiveWorkoutType(planned, injuryIndex, readiness, ws));
                }

                templateScores.add(new TemplateScoreDto(templateIndex, avgUtility, plannedTemplate, effectiveTemplate));
            }

            if (avgUtility > bestScore) {
                bestScore = avgUtility;
                bestTemplateIndex = templateIndex;
                bestTemplate = template;

                // Convert TSB samples -> distributions for this template
                List<LoadDistributionDto> tsbDists = new ArrayList<>(7);
                for (int i = 0; i < 7; i++) {
                    tsbDists.add(toDistribution(tsbSamplesPerDay.get(i)));
                }
                bestTsbDists = tsbDists;
            }
        }

        // Fallback if nothing chosen
        if (bestTemplate == null) {
            bestTemplateIndex = -1;
            bestScore = 0.0;

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

            if (debug && templateScores != null && templateScores.isEmpty()) {
                templateScores = new ArrayList<>();
            }
        }

        TrainingPlanDebugDto debugDto = null;
        if (debug) {
            List<DayDebugDto> dayDebug = buildDayDebug(
                    user,
                    startDate,
                    bestTemplate,
                    initialState,
                    recentLoads,
                    bestTsbDists,
                    injuryIndex,
                    readiness,
                    weatherPerDay,
                    constraints
            );

            debugDto = new TrainingPlanDebugDto(
                    sims,
                    seed,
                    bestTemplateIndex,
                    bestScore,
                    templateScores == null ? List.of() : templateScores,
                    dayDebug
            );
        }

        return new PlanChoice(bestTemplateIndex, bestScore, bestTemplate, bestTsbDists, debugDto);
    }

    private List<DayDebugDto> buildDayDebug(ApplicationUser user,
                                            LocalDate startDate,
                                            List<WorkoutType> bestTemplate,
                                            ForecastState initialState,
                                            List<Integer> recentLoads,
                                            List<LoadDistributionDto> bestTsbDists,
                                            double injuryIndex,
                                            int readiness,
                                            List<CompactWeatherDto> weatherPerDay,
                                            LoadConstraints constraints) {

        List<DayDebugDto> out = new ArrayList<>(7);
        ForecastState st = initialState;

        for (int i = 0; i < 7; i++) {
            LocalDate d = startDate.plusDays(i);

            CompactWeatherDto w = weatherPerDay.get(i);

            boolean trainDay = daySelectorService.isTrainingDay(d, user);

            WorkoutType planned = trainDay
                    ? bestTemplate.get(i)
                    : (injuryIndex >= 0.4 ? WorkoutType.MOBILITY : WorkoutType.REST_DAY);

            Double wsObj = (w == null) ? null : w.getWeatherScore();
            double ws = (wsObj == null) ? 0.6 : wsObj;

            WorkoutType effective = effectiveWorkoutType(planned, injuryIndex, readiness, wsObj);

            LoadDistributionDto load = loadForecaster.forecastLoad(user, d, effective, st, recentLoads, constraints);

            // advance expected trajectory using mean
            st = st.next(load.getMean());

            double fatP = fatiguePenalty(st.tsb(), effective);
            double injP = injuryPenalty(injuryIndex, effective);
            double readyP = readinessPenalty(readiness, effective);
            double weathP = weatherPenalty(wsObj, effective);

            out.add(new DayDebugDto(
                    d,
                    planned,
                    effective,
                    ws,
                    load.getMean(),
                    load.getStd(),
                    bestTsbDists.get(i).getP50(),
                    fatP,
                    injP,
                    readyP,
                    weathP
            ));
        }

        return out;
    }

    private List<PlannedDayDto> materializePlanWithTsbDists(ApplicationUser user,
                                                            LocalDate startDate,
                                                            List<WorkoutType> template,
                                                            ForecastState initialState,
                                                            List<Integer> recentLoads,
                                                            List<LoadDistributionDto> tsbDists,
                                                            double injuryIndex,
                                                            Map<BodyPart, Double> injuriesMap,
                                                            int readiness,
                                                            List<CompactWeatherDto> weatherPerDay,
                                                            LoadConstraints constraints,
                                                            String planId) {

        List<PlannedDayDto> out = new ArrayList<>(7);
        ForecastState st = initialState;

        for (int i = 0; i < 7; i++) {
            LocalDate d = startDate.plusDays(i);

            CompactWeatherDto weatherDto = weatherPerDay.get(i);
            Double ws = (weatherDto == null) ? null : weatherDto.getWeatherScore();

            boolean trainDay = daySelectorService.isTrainingDay(d, user);

            WorkoutType planned = trainDay
                    ? template.get(i)
                    : (injuryIndex >= 0.4 ? WorkoutType.MOBILITY : WorkoutType.REST_DAY);

            WorkoutType effective = effectiveWorkoutType(planned, injuryIndex, readiness, ws);

            LoadDistributionDto load = loadForecaster.forecastLoad(user, d, effective, st, recentLoads, constraints);

            st = st.next(load.getMean());

            GymWorkoutDto gym = null;
            if (effective == WorkoutType.GYM_PREHAB || effective == WorkoutType.MOBILITY) {
                gym = gymWorkoutSelectorService.getGymWorkout(user, d, injuriesMap, readiness);
            }

            RouteDto routeDto = null;
            boolean isRun =
                    effective == WorkoutType.EASY_RUN
                            || effective == WorkoutType.TEMPO_RUN
                            || effective == WorkoutType.INTERVAL_RUN
                            || effective == WorkoutType.LONG_RUN;

            if (isRun) {
                routeDto = computeRouteDtoForDay(user, d, effective, load, readiness, planId);
            }

            out.add(new PlannedDayDto(
                    d,
                    effective,
                    load,
                    tsbDists.get(i),
                    weatherDto,
                    confidenceFromStd(load),
                    explanation(effective, load, tsbDists.get(i).getP50(), injuryIndex, readiness),
                    gym,
                    routeDto
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

        if (weatherScore == null) {
            return 0.6;
        }

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

        if (weatherScore == null) {
            return WorkoutType.GYM_PREHAB;
        }

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

    private int clampSims(Integer sims) {
        if (sims == null) {
            return 120;
        }
        return Math.max(20, Math.min(400, sims));
    }

    private long defaultSeed(Long seed) {
        return seed == null ? 42L : seed;
    }

    private <T> List<T> safeList(SupplierWithException<List<T>> s) {
        try {
            return s.get();
        } catch (Exception e) {
            return List.of();
        }
    }

    private RouteDto computeRouteDtoForDay(
            ApplicationUser user,
            LocalDate date,
            WorkoutType wt,
            LoadDistributionDto load,
            int readiness,
            String planId
    ) {
        // Only runs get a route
        boolean isRun = switch (wt) {
            case EASY_RUN, TEMPO_RUN, INTERVAL_RUN, LONG_RUN -> true;
            default -> false;
        };

        if (!isRun) {
            return null;
        }

        // 1) base distance from existing logic
        RouteDto base = routeGenerationService.generateRouteDetails(user, wt, readiness);
        if (base == null || base.getDistance() == null) {
            return null;
        }
        double baseMeters = base.getDistance();


        // 2) determine jitter %
        double jitterPct = switch (confidenceFromStd(load)) {
            case "high" -> 0.05;
            case "medium" -> 0.08;
            default -> 0.12;
        };

        // 3) tighten bands for harder workouts
        if (wt == WorkoutType.INTERVAL_RUN || wt == WorkoutType.TEMPO_RUN) {
            jitterPct = Math.min(jitterPct, 0.06);
        }
        if (wt == WorkoutType.LONG_RUN) {
            jitterPct = Math.max(jitterPct, 0.07);
        }

        // 4) stable RNG per day
        long seed = stableSeed(planId, date, wt);
        Random rng = new Random(seed);

        double factor = 1.0 + (rng.nextDouble() * 2.0 - 1.0) * jitterPct; // [1-j, 1+j]
        double meters = baseMeters * factor;

        // 5) clamp to sensible bounds
        meters = clampRunDistanceMeters(wt, meters);

        // 6) keep pace/elevation estimates from your existing service
        int s = orsSeedFromLong(seed);
        s = (s == Integer.MIN_VALUE) ? 0 : Math.abs(s);

        return new RouteDto(meters, base.getPace(), base.getElevation(), s);
    }

    private long stableSeed(String planId, LocalDate date, WorkoutType wt) {
        // simple stable hash -> long
        String key = planId + ":" + date + ":" + wt.name();
        return key.hashCode() * 2654435761L; // stable enough, deterministic
    }

    private int orsSeedFromLong(long seed) {
        long z = seed;
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return (int) z; // 32-bit signed, ORS accepts int
    }

    private double clampRunDistanceMeters(WorkoutType wt, double meters) {
        double min;
        double max;
        switch (wt) {
            case INTERVAL_RUN -> {
                min = 4000;
                max = 12000;
            }
            case TEMPO_RUN -> {
                min = 5000;
                max = 16000;
            }
            case EASY_RUN -> {
                min = 3000;
                max = 14000;
            }
            case LONG_RUN -> {
                min = 9000;
                max = 28000;
            }
            default -> {
                min = 0;
                max = 100000;
            }
        }
        return Math.max(min, Math.min(max, meters));
    }


    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    private record PlanResult(List<PlannedDayDto> days,
                              List<LoadDistributionDto> tsbDistributions,
                              TrainingPlanDebugDto debug) {
    }

    private record PlanChoice(
            int bestTemplateIndex,
            double bestScore,
            List<WorkoutType> bestTemplate,
            List<LoadDistributionDto> bestTsbDists,
            TrainingPlanDebugDto debug
    ) {
    }
}
