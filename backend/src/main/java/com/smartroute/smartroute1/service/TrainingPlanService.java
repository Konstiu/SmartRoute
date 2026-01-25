package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.RecommendedActivityDto;

import java.time.LocalDate;

public interface TrainingPlanService {
    /**
     * Gets the training plan for a user for today.
     *
     * @param email the email of the user to get the training plan for.
     * @param latitude the latitude of the location of the user.
     * @param longitude the longitude of the location of the user.
     * @return the recommended activity for the user for today including the current weather.
     */
    RecommendedActivityDto getTrainingPlan(String email, double latitude, double longitude);

    /**
     * Gets the planned day for today.
     *
     * @param email email of a user.
     * @param planId planId of a user.
     * @param date concrete date that should be fetched.
     * @return the recommended activity for the user for today including the current weather.
     */
    RecommendedActivityDto getPlannedDay(String email, String planId, LocalDate date);
}
