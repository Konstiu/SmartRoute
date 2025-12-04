package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.entity.enums.WorkoutType;

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
     * The service internally considers the following inputs:
     * </p>
     *
     * <ul>
     *     <li><b>User readiness</b> (normalized daily readiness value)</li>
     *     <li><b>Weather score</b> for outdoor running at the user's location</li>
     *     <li><b>Current limitation or injury factor</b> affecting training feasibility</li>
     *     <li><b>Recent training history</b> (e.g., sessions performed in the last days)</li>
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
     *     <li>Weather suitability for outdoor running</li>
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
     * @return the recommended {@link WorkoutType} for today
     */
    WorkoutType selectWorkoutType(String email);

}
