package com.smartroute.smartroute1.exception;

/**
 * Exception thrown when a client exceeds the allowed number of requests
 * within a defined time window.
 *
 * <p>
 * This exception is typically used by rate-limiting mechanisms to prevent
 * abuse of API endpoints, such as repeated password reset or verification
 * email requests.
 * </p>
 *
 * <p>
 * When thrown, it should result in an HTTP 429 (Too Many Requests) response
 * being sent to the client.
 * </p>
 */
public class RateLimitExceededException extends RuntimeException {

    /**
     * Constructs a new {@code RateLimitExceededException} with the specified
     * detail message.
     *
     * @param message the detail message explaining the reason for the exception
     */
    public RateLimitExceededException(String message) {
        super(message);
    }
}
