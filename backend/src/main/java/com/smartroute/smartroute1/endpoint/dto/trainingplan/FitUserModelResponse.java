package com.smartroute.smartroute1.endpoint.dto.trainingplan;

import java.util.List;

public record FitUserModelResponse(
        double b,
        List<Double> m,     // per workout type index
        double sigma0,
        List<Double> sigmaK,
        double betaFat
) {}
