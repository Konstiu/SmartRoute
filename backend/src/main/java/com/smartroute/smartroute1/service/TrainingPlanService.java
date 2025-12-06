package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.RecommendedActivityDto;

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
}
