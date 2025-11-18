package com.smartroute.smartroute1.entity.weather;

public record WeatherImpactResult(
        double penaltyPercent,
        long adjustedTimeSeconds,
        HeatRiskCategory risk
) {}
