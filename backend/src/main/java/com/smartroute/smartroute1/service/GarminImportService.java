package com.smartroute.smartroute1.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Service for synchronizing Garmin activities for a given user.
 *
 * <p>On the first synchronization, the implementation typically requires
 * the user's Garmin credentials (email and password) in order to obtain
 * OAuth tokens. On subsequent synchronizations, stored tokens can be
 * reused and {@code email} / {@code password} may be {@code null}.
 *
 * <p>The method is expected to:
 * <ul>
 *   <li>Authenticate against Garmin (via credentials or stored tokens).</li>
 *   <li>Fetch the latest activities up to the requested count.</li>
 *   <li>Update any stored token information if it has changed.</li>
 *   <li>Return the fetched activities as a list of JSON structures.</li>
 * </ul>
 */

public interface GarminImportService {

    /**
     * Synchronizes Garmin activities for the given user.
     *
     * @param userId        the ID of the application user whose Garmin account should be synchronized
     * @param activityCount the maximum number of recent activities to fetch (e.g. last N runs)
     * @param email         the Garmin account email; required on first login, may be {@code null} when using stored tokens
     * @param password      the Garmin account password; required on first login, may be {@code null} when using stored tokens
     * @return a list of activities as {@link JsonNode} objects, typically containing summary and detail information
     * @throws IllegalArgumentException if the user cannot be found or required credentials are missing on first login
     * @throws RuntimeException         if synchronization with Garmin fails for any reason (authentication, I/O, parsing, etc.)
     */
    List<JsonNode> syncActivities(Long userId, int activityCount, String email, String password);

}
