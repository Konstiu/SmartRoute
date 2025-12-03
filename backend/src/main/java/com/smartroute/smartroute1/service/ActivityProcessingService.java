package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.entity.Activity;

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
    void fetchHeartRateDataForActivities(int maxBatchSize, List<Activity> activities, String token);

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
}
