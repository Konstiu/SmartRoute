package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.LoadDistributionDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.PlannedDayDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlan7dDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.DailyAggregationService;
import com.smartroute.smartroute1.service.FatigueAndOverloadService;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.DailySummary;
import com.smartroute.smartroute1.service.LoadForecaster;
import com.smartroute.smartroute1.service.TrainingPlan7dService;
import com.smartroute.smartroute1.util.ForecastState;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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

    @Autowired
    public TrainingPlan7dServiceImpl(UserRepository userRepository,
                                     DailyAggregationService dailyAggregationService,
                                     LoadForecaster loadForecaster,
                                     FatigueAndOverloadService fatigueAndOverloadService) {
        this(userRepository, dailyAggregationService, loadForecaster, fatigueAndOverloadService,
                Clock.system(ZoneId.of("Europe/Vienna")));
    }

    public TrainingPlan7dServiceImpl(UserRepository userRepository,
                                     DailyAggregationService dailyAggregationService,
                                     LoadForecaster loadForecaster,
                                     FatigueAndOverloadService fatigueAndOverloadService,
                                     Clock clock) {
        this.userRepository = userRepository;
        this.dailyAggregationService = dailyAggregationService;
        this.loadForecaster = loadForecaster;
        this.fatigueAndOverloadService = fatigueAndOverloadService;
        this.clock = clock;
    }

    @Override
    public TrainingPlan7dDto buildNext7Days(String email) {
        ApplicationUser user = userRepository.findUserByEmail(email);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

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

        // Choose best via simple Monte Carlo utility
        PlanResult best = chooseBestPlan(user, today, templates, initialState, recentLoads);

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

    private PlanResult chooseBestPlan(ApplicationUser user, LocalDate startDate, List<List<WorkoutType>> templates, ForecastState initialState, List<Integer> recentLoads) {

        Random rng = new Random(42);

        double bestScore = Double.NEGATIVE_INFINITY;
        List<PlannedDayDto> bestDays = null;

        for (List<WorkoutType> template : templates) {
            // Monte Carlo rollouts
            int sims = 80; // keep small for MVP
            double totalUtility = 0;

            for (int s = 0; s < sims; s++) {
                ForecastState st = initialState;
                double utility = 0;

                for (int i = 0; i < 7; i++) {
                    LocalDate d = startDate.plusDays(i);
                    WorkoutType wt = template.get(i);

                    LoadDistributionDto dist = loadForecaster.forecastLoad(user, d, wt, st, recentLoads);
                    double loadSample = sampleNonNegativeNormal(rng, dist.getMean(), dist.getStd());

                    // Forward update
                    st = st.next(loadSample);

                    // Utility: encourage training but penalize too-negative TSB (fatigue risk)
                    utility += trainingReward(wt, loadSample);
                    utility -= fatiguePenalty(st.tsb(), wt);
                }

                totalUtility += utility;
            }

            double avgUtility = totalUtility / sims;

            if (avgUtility > bestScore) {
                bestScore = avgUtility;
                bestDays = materializePlan(user, startDate, template, initialState, recentLoads);
            }
        }

        return new PlanResult(bestDays);
    }

    private List<PlannedDayDto> materializePlan(ApplicationUser user, LocalDate startDate, List<WorkoutType> template, ForecastState initialState, List<Integer> recentLoads) {

        // Use mean forecast to present a stable plan (you can later present sampled ranges too)
        List<PlannedDayDto> out = new ArrayList<>(7);
        ForecastState st = initialState;

        for (int i = 0; i < 7; i++) {
            LocalDate d = startDate.plusDays(i);
            WorkoutType wt = template.get(i);

            LoadDistributionDto load = loadForecaster.forecastLoad(user, d, wt, st, recentLoads);

            // Update with mean (stable)
            st = st.next(load.getMean());

            LoadDistributionDto loadDto = toDto(load);

            // Derive a simple tsb distribution: we only have deterministic state here (MVP)
            // So return tight range around current predicted TSB
            double tsb = st.tsb();
            LoadDistributionDto tsbDto = new LoadDistributionDto(tsb, tsb, tsb, tsb, 0);

            out.add(new PlannedDayDto(d, wt, loadDto, tsbDto, confidenceFromStd(load), explanation(wt, load, tsb)));
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

    private LoadDistributionDto toDto(LoadDistributionDto d) {
        return new LoadDistributionDto(d.getP10(), d.getP50(), d.getP90(), d.getMean(), d.getStd());
    }

    private double safe(SupplierWithException<Double> s, double fallback) {
        try {
            return s.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    private record PlanResult(List<PlannedDayDto> days) {}
}
