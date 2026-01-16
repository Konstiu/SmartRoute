package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlan7dDto;

public interface TrainingPlan7dService {
    /**
     * Build the training plan for the next 7 days.
     *
     * @param email email a user is registered with.
     *
     * @return a {@link TrainingPlan7dDto}.
     */
    TrainingPlan7dDto buildNext7Days(String email);
}
