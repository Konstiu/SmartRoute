package com.smartroute.smartroute1.service;

/**
 * Defines a contract for enforcing rate limits on specific user actions,
 * such as requesting password resets or resending verification emails.
 * <p>
 * Implementations of this interface are responsible for tracking request
 * frequency and preventing excessive requests from the same user or email
 * address within a given time window.
 * </p>
 *
 * <p>
 * When the rate limit is exceeded, an implementation should throw a
 * {@link com.smartroute.smartroute1.exception.RateLimitExceededException}.
 * </p>
 */
public interface RateLimitCheck {

    /**
     * Checks whether a request of the given {@code type} for the specified
     * {@code email} is allowed under the current rate limit policy.
     * <p>
     * If the maximum number of allowed requests has been reached within the
     * configured time window, this method should throw a
     * {@link com.smartroute.smartroute1.exception.RateLimitExceededException}.
     * </p>
     *
     * @param email the email address associated with the request
     * @param type  the type of action being rate limited (e.g., "verification", "password")
     * @throws com.smartroute.smartroute1.exception.RateLimitExceededException
     *         if the rate limit has been exceeded
     */
    void check(String email, String type);
}
