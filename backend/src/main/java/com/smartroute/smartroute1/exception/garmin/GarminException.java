package com.smartroute.smartroute1.exception.garmin;

public class GarminException extends RuntimeException {
    public GarminException(String message) {
        super(message);
    }

    public GarminException(String message, Throwable cause) {
        super(message, cause);
    }
}