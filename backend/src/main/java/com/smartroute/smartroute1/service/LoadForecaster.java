package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.LoadDistributionDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.util.ForecastState;
import com.smartroute.smartroute1.util.LoadConstraints;

import java.time.LocalDate;
import java.util.List;

public interface LoadForecaster {

    /**
     * Predicts the distribution of training load for a planned workout on a given date.
     *
     * @param user             The athlete for whom the forecast is generated.
     *
     * @param date             The calendar date of the planned session.
     *
     * @param plannedWorkout   The type of workout that is planned (e.g. EASY_RUN, TEMPO_RUN, INTERVAL_RUN, GYM_PREHAB, REST_DAY).
     *
     * @param state            The current forecast state of the athlete, typically containing estimated fitness (CTL) and fatigue (ATL).
     *
     * @param recentDailyLoads List of historical daily training loads (including rest days as 0), ordered from oldest to newest. Usually covers the last 28–60 days.
     *
     * @return A {@link LoadDistributionDto} describing the predicted training load as a probability distribution (e.g. mean, standard deviation, and quantiles such as p10/p50/p90).
     */
    LoadDistributionDto forecastLoad(ApplicationUser user, LocalDate date, WorkoutType plannedWorkout, ForecastState state, List<Integer> recentDailyLoads);

    default LoadDistributionDto forecastLoad(
            ApplicationUser user,
            LocalDate date,
            WorkoutType workoutType,
            ForecastState state,
            List<Integer> recentLoads,
            LoadConstraints constraints
    ) {
        // default behavior: call old method, then apply constraints
        LoadDistributionDto base = forecastLoad(user, date, workoutType, state, recentLoads);
        return applyConstraints(base, workoutType, constraints);
    }

    // helper can live here as static or default private method (Java 9+)
    static LoadDistributionDto applyConstraints(
            LoadDistributionDto base,
            WorkoutType workoutType,
            LoadConstraints c
    ) {
        if (base == null) {
            return null;
        }
        if (c == null) {
            c = LoadConstraints.none();
        }

        boolean run = switch (workoutType) {
            case EASY_RUN, TEMPO_RUN, INTERVAL_RUN, LONG_RUN -> true;
            default -> false;
        };

        if (!run) {
            return base; // don’t scale gym/mobility/rest
        }

        double scale = clamp01(c.combinedScale());

        return scaleDistribution(base, scale);
    }

    static LoadDistributionDto scaleDistribution(LoadDistributionDto d, double scale) {
        // scale mean/std and percentiles consistently
        return new LoadDistributionDto(
                d.getP10() * scale,
                d.getP50() * scale,
                d.getP90() * scale,
                d.getMean() * scale,
                d.getStd() * scale
        );
    }

    static double clamp01(double x) {
        if (x < 0) {
            return 0;
        }
        if (x > 1) {
            return 1;
        }
        return x;
    }
}

