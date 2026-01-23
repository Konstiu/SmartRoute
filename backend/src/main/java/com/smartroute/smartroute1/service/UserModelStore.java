package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.FitUserModelResponse;

import java.util.Optional;

public interface UserModelStore {
    Optional<FitUserModelResponse> get(String email, String key);

    void put(String email, String key, FitUserModelResponse model);

    void remove(String email, String key);
}
