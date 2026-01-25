package com.smartroute.smartroute1.endpoint.dto.trainingplan;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class DailySummary {
    LocalDate date;
    int totalLoad;
    float totalDistanceMeters;
    int totalMovingTimeSeconds;
    float totalElevationGainMeters;
    boolean hasHeartRate;
}
