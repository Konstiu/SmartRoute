package com.smartroute.smartroute1.exception.garmin;


public class GarminAuthenticationException extends GarminException {
    public GarminAuthenticationException(String message) {
        super(message);
    }

    public GarminAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}