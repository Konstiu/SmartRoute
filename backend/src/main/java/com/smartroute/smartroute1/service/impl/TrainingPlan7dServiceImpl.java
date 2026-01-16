package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.LoadDistributionDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.PlannedDayDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlan7dDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.DailySummary;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.DailyAggregationService;
import com.smartroute.smartroute1.service.FatigueAndOverloadService;
import com.smartroute.smartroute1.service.InjuryAwareTrainingService;
import com.smartroute.smartroute1.service.TrainingPlan7dService;
import com.smartroute.smartroute1.service.LoadForecaster;
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

    @Autowired
    public TrainingPlan7dServiceImpl(UserRepository userRepository,
                                     DailyAggregationService dailyAggregationService,
                                     LoadForecaster loadForecaster,
                                     FatigueAndOverloadService fatigueAndOverloadService,
                                     InjuryAwareTrainingService injuryAwareTrainingService) {
        this(userRepository, dailyAggregationService, loadForecaster, fatigueAndOverloadService,
                Clock.system(ZoneId.of("Europe/Vienna")), injuryAwareTrainingService);
    }

    public TrainingPlan7dServiceImpl(UserRepository userRepository,
                                     DailyAggregationService dailyAggregationService,
                                     LoadForecaster loadForecaster,
                                     FatigueAndOverloadService fatigueAndOverloadService,
                                     Clock clock, InjuryAwareTrainingService injuryAwareTrainingService) {
        this.userRepository = userRepository;
        this.dailyAggregationService = dailyAggregationService;
        this.loadForecaster = loadForecaster;
        this.fatigueAndOverloadService = fatigueAndOverloadService;
        this.clock = clock;
        this.injuryAwareTrainingService = injuryAwareTrainingService;
    }

    @Override
    public TrainingPlan7dDto buildNext7Days(String email) {
        ApplicationUser user = userRepository.findUserByEmail(email);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        double injuryIndex = safe(() -> injuryAwareTrainingService.getInjuryIndex(email), 0.0);

        // Historical daily series for personalization (overall load)
        List<DailySummary> history = dailyAggregationService.getDailySummaries(user, 60);
        List<Integer> recentLoads = history.stream().map(DailySummary::getTotalLoad).toList();

        LocalDate today = LocalDate.now(clock);

        // Current state from your existing fatigue service (already computed from history)
        // IMPORTANT: ensure your fatigue model uses overall load (not only Run).
        double ctl = safe(() -> fatigueAndOverloadService.currentCtl(user), 0.0);
        double atl = safe(() -> fatigueAndOverloadService.currentAtl(user), 0.0);
        ForecastState initialState = new ForecastState(ctl, atl);

        // Candidate weekly templates including gym/mobility
        List<List<WorkoutType>> templates = generateTemplates(user);
        templates = applyInjuryConstraints(templates, injuryIndex);

        // Choose best via simple Monte Carlo utility
        PlanResult best = chooseBestPlan(user, today, templates, initialState, recentLoads, injuryIndex);

        return new TrainingPlan7dDto(best.days);
    }

    private List<List<WorkoutType>> generateTemplates(ApplicationUser user) {
        // MVP: a few sensible structures. You can later tailor by activeWeekdays, injuries, etc.
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
                                      double injuryIndex) {

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
                    WorkoutType wt = template.get(i);

                    LoadDistributionDto loadDist = loadForecaster.forecastLoad(user, d, wt, st, recentLoads);

                    // sample daily load
                    double loadSample = sampleNonNegativeNormal(rng, loadDist.getMean(), loadDist.getStd());

                    // forward update
                    st = st.next(loadSample);

                    // record resulting TSB for this simulated path/day
                    tsbSamplesPerDay.get(i).add(st.tsb());

                    utility += trainingReward(wt, loadSample);
                    utility -= fatiguePenalty(st.tsb(), wt);
                    utility -= injuryPenalty(injuryIndex, wt);
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
        List<PlannedDayDto> days = materializePlanWithTsbDists(
                user, startDate, bestTemplate, initialState, recentLoads, bestTsbDists
        );

        return new PlanResult(days, bestTsbDists);
    }


    private List<PlannedDayDto> materializePlanWithTsbDists(ApplicationUser user,
                                                            LocalDate startDate,
                                                            List<WorkoutType> template,
                                                            ForecastState initialState,
                                                            List<Integer> recentLoads,
                                                            List<LoadDistributionDto> tsbDists) {

        List<PlannedDayDto> out = new ArrayList<>(7);
        ForecastState st = initialState;

        for (int i = 0; i < 7; i++) {
            LocalDate d = startDate.plusDays(i);
            WorkoutType wt = template.get(i);

            LoadDistributionDto load = loadForecaster.forecastLoad(user, d, wt, st, recentLoads);

            // update state with mean for “expected trajectory”
            st = st.next(load.getMean());

            out.add(new PlannedDayDto(
                    d,
                    wt,
                    load,
                    tsbDists.get(i),
                    confidenceFromStd(load),
                    explanation(wt, load, tsbDists.get(i).getP50())
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

    private List<String> explanation(WorkoutType wt, LoadDistributionDto load, double tsb) {
        List<String> e = new ArrayList<>();
        e.add("Planned: " + wt.toString());
        e.add("Expected load ≈ " + Math.round(load.getMean()) + " (P10 " + Math.round(load.getP10()) + " – P90 " + Math.round(load.getP90()) + ")");
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


    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    private record PlanResult(List<PlannedDayDto> days,
                              List<LoadDistributionDto> tsbDistributions) {}

}
