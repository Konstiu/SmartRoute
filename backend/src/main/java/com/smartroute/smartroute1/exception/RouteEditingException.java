package com.smartroute.smartroute1.exception;

/**
 * Domain-specific exception for route editing failures.
 */
public class RouteEditingException extends RuntimeException {
    public RouteEditingException(String message) {
        super(message);
    }

    public RouteEditingException(String message, Throwable cause) {
        super(message, cause);
    }
}