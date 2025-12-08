package com.smartroute.smartroute1.entity.enums;

public enum WorkoutType {
    EASY_RUN("Easy Run"),
    TEMPO_RUN("Tempo Run"),
    INTERVAL_RUN("Interval Run"),
    LONG_RUN("Long Run"),
    GYM_PREHAB("Gym / Prehab"),
    MOBILITY("Mobility"),
    REST_DAY("Rest Day");

    private final String displayName;

    WorkoutType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
