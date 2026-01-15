package com.smartroute.smartroute1.util;

public record ForecastState(double ctl, double atl) {
    public double tsb() {
        return ctl - atl;
    }
}
