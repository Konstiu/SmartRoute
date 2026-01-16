package com.smartroute.smartroute1.util;

public record ForecastState(double ctl, double atl) {
    public double tsb() {
        return ctl - atl;
    }

    public ForecastState next(double dailyLoad) {
        double nextCtl = ctl + (dailyLoad - ctl) / 42.0;
        double nextAtl = atl + (dailyLoad - atl) / 7.0;
        return new ForecastState(nextCtl, nextAtl);
    }
}
