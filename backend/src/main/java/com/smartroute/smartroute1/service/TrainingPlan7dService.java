package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlan7dDto;

public interface TrainingPlan7dService {
    /**
     * Build the training plan for the next 7 days.
     *
     * @param email     email a user is registered with.
     *
     * @param latitude  latitude of the user.
     *
     * @param longitude longitude of the user.
     *
     * @param debug     whether debug mode is enabled.
     *
     * @param sims      number of simulations.
     *
     * @param seed      seed for randomization.
     *
     * @return a {@link TrainingPlan7dDto}.
     */
    TrainingPlan7dDto buildNext7Days(String email, double latitude, double longitude, boolean debug, Integer sims, Long seed);


    default TrainingPlan7dDto buildNext7Days(String email, double latitude, double longitude) {
        return buildNext7Days(
                email,
                latitude,
                longitude,
                false,
                null,
                null
        );
    }
}
