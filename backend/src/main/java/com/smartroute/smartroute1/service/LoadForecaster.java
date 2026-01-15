package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.LoadDistributionDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.util.ForecastState;

import java.time.LocalDate;
import java.util.List;

public interface LoadForecaster {

    /**
     * Predicts the distribution of training load for a planned workout on a given date.
     *
     * @param user The athlete for whom the forecast is generated.
     *
     * @param date The calendar date of the planned session.
     *
     * @param plannedWorkout The type of workout that is planned (e.g. EASY_RUN, TEMPO_RUN, INTERVAL_RUN, GYM_PREHAB, REST_DAY).
     *
     * @param state The current forecast state of the athlete, typically containing estimated fitness (CTL) and fatigue (ATL).
     *
     * @param recentDailyLoads List of historical daily training loads (including rest days as 0), ordered from oldest to newest. Usually covers the last 28–60 days.
     *
     * @return A {@link LoadDistributionDto} describing the predicted training load as a probability distribution (e.g. mean, standard deviation, and quantiles such as p10/p50/p90).
     */
    LoadDistributionDto forecastLoad(ApplicationUser user, LocalDate date, WorkoutType plannedWorkout, ForecastState state, List<Integer> recentDailyLoads);
}
