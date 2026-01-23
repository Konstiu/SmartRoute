package com.smartroute.smartroute1.exception;

import java.util.Map;

public class StopTooFarFromRouteException extends RuntimeException {
    private final double maxAllowedMeters;
    private final double actualMeters;

    public StopTooFarFromRouteException(double maxAllowedMeters, double actualMeters) {
        super("Point is too far from the route");
        this.maxAllowedMeters = maxAllowedMeters;
        this.actualMeters = actualMeters;
    }

    public double getMaxAllowedMeters() {
        return maxAllowedMeters;
    }

    public double getActualMeters() {
        return actualMeters;
    }

    public Map<String, Object> toDetails() {
        return Map.of(
                "maxAllowedMeters", maxAllowedMeters,
                "actualMeters", actualMeters
        );
    }
}
