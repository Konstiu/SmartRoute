package com.smartroute.smartroute1.service;

public interface ActivityService {
    /**
     * Fetches activities from all connected services (Strava, Garmin).
     *
     * @param email the user's email address
     * @param count the maximum of activities to fetch
     */
    void synchronize(String email, int count) throws Exception;
}
