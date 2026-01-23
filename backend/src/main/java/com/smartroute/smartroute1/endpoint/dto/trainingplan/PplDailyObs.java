package com.smartroute.smartroute1.endpoint.dto.trainingplan;

public record PplDailyObs(
        String date,               // yyyy-MM-dd
        String workoutType,         // WorkoutType.name()
        int totalLoad,
        float distanceMeters,
        int movingTimeSeconds,
        float elevationGainMeters,
        Double weatherScore,        // nullable
        Double tsb                 // nullable (or 0 if unknown)
) {}
