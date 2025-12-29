package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ActivityStream;
import com.smartroute.smartroute1.entity.enums.ActivityStreamSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Interface for handling Strava-related asynchronous operations.
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
    void processActivitiesInBatches(int maxBatchSize, List<Activity> activities, String token);

    ActivityStream createActivityStream(List<Double> time, List<Double> distance, List<Double> heartRate, ActivityStreamSource source);

    /**
     * Retrieves all activities belonging to the user identified by the given email.
     *
     * @param email the email address of the user whose activities should be fetched
     * @return a list of all activities associated with the user; never null
     */
    List<Activity> getActivities(String email);

    /**
     * Retrieves a specific activity for the user identified by the given email.
     *
     * @param email the email address of the user requesting the activity
     * @param id    the ID of the activity to retrieve
     * @return the requested activity if found
     */
    Activity getActivity(String email, long id);

    /**
     * Retrieves the last activity before the specified date for the user identified by the given email.
     * If no such activity exists, an empty Optional is returned.
     *
     * @param email the email address of the user
     * @param date the date before which to find the last activity
     * @return the last activity before the specified date
     */
    Optional<Activity> getLastActivityBeforeDate(String email, LocalDate date);

    /**
     * Retrieves the last N activities for the user identified by the given email.
     * If no activities exist an empty list is returned.
     * if only fewer than N activities exist, all available activities are returned.
     *
     * @param email the email address of the user
     * @param n     the number of recent activities to retrieve
     * @return a list of the last N activities associated with the user
     * @throws IllegalArgumentException if n is less than or equal to zero
     */
    List<Activity> getLastActivities(String email, int n) throws IllegalArgumentException;

    /**
     * Retrieves the last running activity before the specified date for the user identified by the given email.
     * If no such activity exists, an empty Optional is returned.
     *
     * @param email the email address of the user
     * @param date the date before which to find the last running activity
     * @return the last running activity before the specified date
     */
    Optional<Activity> getLastRunningActivityBeforeDate(String email, LocalDate date);

    /**
     * Detects heart rate spikes in a running activity.
     *
     * @param activity the activity to analyze
     * @return the number of heart rate spikes detected, or -1 if the activity lacks
     *         required data (missing streams, mismatched array lengths, or insufficient data points)
     * @throws IllegalStateException if heart rate and time arrays have different lengths
     */
    int detectHeartRateSpikes(Activity activity);

    /**
     * Detects pace spikes (rapid accelerations) in a running activity.
     *
     * @param activity the activity to analyze
     * @return the number of pace spikes detected, or -1 if the activity lacks
     *         required data (missing streams, mismatched array lengths, or insufficient data points)
     * @throws IllegalStateException if distance and time arrays have different lengths
     */
    int detectPaceSpikes(Activity activity);
}
