package com.smartroute.smartroute1.entity.enums;

public enum RunType {
    EASY_RUN("Easy Run"),
    TEMPO_RUN("Tempo Run"),
    INTERVAL_RUN("Interval Run"),
    LONG_RUN("Long Run");

    private final String displayName;

    RunType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
