package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.entity.Activity;

import java.util.List;

/**
 * Interface for handling Strava-related asynchronous operations .
 */
public interface ActivityProcessingService {
    /**
     * Fetches the Strava heartRateStreams for the activities in the
     * provided activities list, calculates the sessionLoad for each activity
     * and saves it to the activity.
     * To avoid hitting Strava API limits, activities are processed in batches.
     *
     * @param maxBatchSize maximum size of batch
     * @param activities   list of activities
     * @param token        Bearer token
     */
    void fetchHeartRateDataForActivities(int maxBatchSize, List<Activity> activities, String token);
}
