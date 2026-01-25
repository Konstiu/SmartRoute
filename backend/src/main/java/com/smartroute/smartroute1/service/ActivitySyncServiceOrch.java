package com.smartroute.smartroute1.service;

import java.util.UUID;

public interface ActivitySyncServiceOrch {
    /**
     * Calls the @code{} activities from all connected services (Strava, Garmin).
     *
     * @param email the user's email address
     * @param count the maximum of activities to fetch
     * @param uuid  unique id of this sync request (used for validation/status tracking)
     */
    void synchronize(String email, int count, UUID uuid) throws Exception;
}
