package com.smartroute.smartroute1.endpoint.dto;

import lombok.Data;

@Data
public class RecommendedActivityDto {
    String name;
    SessionType type;
    RouteDto route;
    GymWorkoutDto gymSession;
    CompactWeatherDto weather;
    AthleteStatusDto athleteStatus;

    public enum SessionType {
        RUN,
        GYM,
        REST,
        MOBILITY
    }
}

