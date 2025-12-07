package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.exception.ValidationException;

public interface WorkoutTypeSelectorService {

    /**
     * Selects the most appropriate workout type for a running-focused user for the current day.
     *
     * <p>
     * This service implements a score-based decision model that evaluates multiple factors
     * to determine which training session is best suited for the user today. The goal is to
     * prioritize running sessions while still recommending supportive work (e.g., gym prehab,
     * mobility) when running is not ideal due to fatigue, weather conditions, or temporary
     * limitations.
     * </p>
     *
     * <p>
     * If the {@link DaySelectorService} specifies today as non training day, the method returns {@link WorkoutType#REST_DAY}.
     * This behavior can be overridden by setting {@code ignoreRestDay} to true, in which case
     * a workout type will be selected regardless of the regular training schedule.
     * </p>
     *
     * <p>
     * The service internally considers the following inputs:
     * </p>
     *
     * <ul>
     *     <li><b>User readiness</b> (normalized daily readiness value) by {@link ReadinessScoreService}</li>
     *     <li><b>Weather score</b> for outdoor running at the user's location, based on the provided latitude and longitude by {@link WeatherService}</li>
     *     <li><b>Current limitation or injury factor</b> affecting training feasibility by {@link InjuryAwareTrainingService}</li>
     *     <li><b>Recent training history</b> (e.g., sessions performed in the last 3 days) by {@link ActivityProcessingService}</li>
     * </ul>
     *
     * <p>
     * Based on these inputs, the service computes a weighted score for each possible
     * workout type (e.g., easy run, tempo run, interval run, long run, gym prehab, mobility)
     * and selects the one with the highest overall score. The scoring model considers:
     * </p>
     *
     * <ul>
     *     <li>Safety and injury feasibility</li>
     *     <li>Match between intended session intensity and current readiness</li>
     *     <li>Weather suitability for outdoor running at the specified coordinates</li>
     *     <li>Priority for running sessions</li>
     *     <li>Session variety to avoid repetitive workloads</li>
     * </ul>
     *
     * <p>
     * The returned workout type represents the system's recommendation for today's
     * training based on a combination of user state, context, and training best practices.
     * </p>
     *
     * @param email the email of the user for whom the workout type should be selected
     * @param latitude the latitude of the user's current or expected training location
     * @param longitude the longitude of the user's current or expected training location
     * @param ignoreRestDay if true, the selection ignores scheduled rest days and returns a workout type regardless
     * @return the recommended {@link WorkoutType} for today, or {@link WorkoutType#REST_DAY} if today is not a training day and ignoreRestDay is false
     * @throws ValidationException if longitude or latitude are invalid
     */
    WorkoutType selectWorkoutType(String email, double latitude, double longitude, boolean ignoreRestDay) throws ValidationException;

}
