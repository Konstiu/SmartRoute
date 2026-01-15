package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.LoadDistributionDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.service.LoadForecaster;
import com.smartroute.smartroute1.util.ForecastState;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class LoadForecasterService implements LoadForecaster {

    @Override
    public LoadDistributionDto forecastLoad(ApplicationUser user, LocalDate date, WorkoutType plannedWorkout, ForecastState state, List<Integer> recentDailyLoads) {

        if (plannedWorkout == WorkoutType.REST_DAY) {
            return new LoadDistributionDto(0, 0, 0, 0, 0);
        }


        // Robust baselines from last 28 days (or less if not available)
        double baseline = robustBaseline(recentDailyLoads);

        // Cold start detection: very little load history
        boolean coldStart = countNonZero(recentDailyLoads) < 5;

        // Workout multipliers
        double multiplier = switch (plannedWorkout) {
            case REST_DAY -> 0.0;
            case MOBILITY -> 0.15;
            case GYM_PREHAB -> 0.35;
            case EASY_RUN -> 0.90;
            case TEMPO_RUN -> 1.25;
            case INTERVAL_RUN -> 1.45;
            case LONG_RUN -> 1.60;
        };

        // State-based safety adjustment using TSB (very simple MVP rule)
        double tsb = state.tsb();
        double fatiguePenalty = 1.0;

        if (tsb < -20) {
            fatiguePenalty = 0.70;
        } else if (tsb < -10) {
            fatiguePenalty = 0.85;
        }

        // Experience level adjustment (optional but helps cold start)
        double expFactor = experienceFactor(user.getExperienceLevel(), coldStart);

        double mean = baseline * multiplier * fatiguePenalty * expFactor;

        // Uncertainty: bigger for cold start and high intensity workouts
        double baseStdFrac = coldStart ? 0.40 : 0.22;
        double intensityStdBoost = switch (plannedWorkout) {
            case INTERVAL_RUN -> 0.10;
            case TEMPO_RUN -> 0.06;
            case LONG_RUN -> 0.08;
            default -> 0.00;
        };

        double std = Math.max(5.0, mean * (baseStdFrac + intensityStdBoost));

        // Convert mean/std to p10/p50/p90 (normal approx, OK for MVP)
        double p50 = Math.max(0, mean);
        double p10 = Math.max(0, mean - 1.2816 * std);
        double p90 = Math.max(0, mean + 1.2816 * std);

        return new LoadDistributionDto(p10, p50, p90, mean, std);
    }

    private double robustBaseline(List<Integer> loads) {
        if (loads == null || loads.isEmpty()) {
            return 40.0; // safe default
        }

        // Use last 28 (or all)
        List<Integer> slice = loads.size() > 28 ? loads.subList(loads.size() - 28, loads.size()) : loads;

        // median is robust to outliers
        List<Integer> sorted = slice.stream().sorted().toList();
        int n = sorted.size();
        double median = (n % 2 == 1) ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;

        // If median is too tiny (e.g., mostly rest), fall back to mean of non-zero days
        if (median < 10) {
            double meanNonZero = slice.stream().filter(x -> x != null && x > 0).mapToDouble(x -> x).average().orElse(30.0);
            return Math.max(20.0, meanNonZero);
        }

        return Math.max(20.0, median);
    }

    private int countNonZero(List<Integer> loads) {
        if (loads == null) {
            return 0;
        }
        int c = 0;
        for (Integer x : loads) {
            if (x != null && x > 0) {
                c++;
            }
        }
        return c;
    }

    private double experienceFactor(ExperienceLevel exp, boolean coldStart) {
        if (!coldStart) {
            return 1.0;
        }

        if (exp == null) {
            return 0.90;
        }

        return switch (exp) {
            case BEGINNER -> 0.75;
            case CASUAL -> 0.85;
            case INTERMEDIATE -> 0.95;
            case ADVANCED -> 1.05;
            case COMPETITIVE_ATHLETE -> 1.10;
        };
    }
}
