package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlan7dDto;

import java.util.Optional;

public interface TrainingPlanStore {

    void put(String email, String planId, TrainingPlan7dDto plan);

    Optional<TrainingPlan7dDto> get(String email, String planId);

    void remove(String email, String planId);

    void removeAllForUser(String email);
}
