package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.entity.ApplicationUser;

import java.time.LocalDate;

public interface ReadinessScoreService {

    /**
     * Calculates the Readiness Score for a given user on a specific date.
     * The Readiness Score is a value between 0 and 100 that indicates how prepared
     * a user is for training, based on four components:
     *
     * <ul>
     *   <li><b>Fitness (CTL-based)</b>: Reward for long-term training load (Chronic Training Load, CTL).
     *       Higher CTL increases readiness. Computed using a logistic function:
     *       {@code Ffitness = 1 / (1 + exp(-K_FIT * (CTL/C_0 - 1)))}.</li>
     *   <li><b>Fatigue (TSB-based)</b>: Penalty for recent training load (Training Stress Balance, TSB).
     *       High fatigue reduces readiness. Computed using a logistic function:
     *       {@code Ffatigue = 1 / (1 + exp(-K_TSB * (TSB/T_0)))}.</li>
     *   <li><b>Injury Penalty</b>: Reduces readiness based on the severity of injuries.
     *       Injury index ranges from 0 (healthy) to 1 (severe injury), and the penalty is:
     *       {@code Finjury = exp(-GAMMA_INJURY * injuryIndex)}. If no injury is reported,
     *       the weight of this component is redistributed to the other components.</li>
     *   <li><b>Satisfaction Factor</b>: Normalized user-reported score from the last activity
     *       before the given date. Scaled to [0,1] and weighted in the final score. If no
     *       activity is available, its weight is redistributed.</li>
     * </ul>
     *
     * <p>The final Readiness Score is the weighted sum of these components:
     * <pre>
     *     readiness = wf * Ffitness + wt * Ffatigue + wi * Finjury + ws * Fsatisfaction
     * </pre>
     * and scaled to 0–100.</p>
     *
     * <p><b>Constants used:</b></p>
     * <ul>
     *   <li>{@code K_TSB}, {@code T_0}: Parameters for the fatigue logistic function</li>
     *   <li>{@code K_FIT}, {@code C_0}: Parameters for the fitness logistic function</li>
     *   <li>{@code GAMMA_INJURY}: Controls the severity of the injury penalty</li>
     *   <li>{@code SATISFACTION_MIN}, {@code SATISFACTION_MAX}: Bounds for normalizing satisfaction scores</li>
     *   <li>{@code WEIGHT_FITNESS}, {@code WEIGHT_FATIGUE}, {@code WEIGHT_INJURY}, {@code WEIGHT_LAST_SATISFACTION}: Default weights for the components (sum to 1)</li>
     * </ul>
     *
     * <p>If either the injury index or satisfaction score is missing, their weight is
     * redistributed evenly among the remaining components using the {@code redistribute} method.</p>
     *
     * @param user the {@link ApplicationUser} for whom the readiness is calculated
     * @param date the date for which readiness should be computed
     * @return an integer readiness score between 0 and 100, where higher values indicate
     *         better preparedness for training
     */
    int calculateReadinessScore(ApplicationUser user, LocalDate date);

    /**
     * Redistributes the value at the specified index across all other elements
     * in the array that have a value greater than zero. The element at the given
     * index is set to zero, and its former value is evenly divided among all
     * remaining positive elements.
     *
     * <p>If none of the other elements are greater than zero, the method performs
     * no redistribution and simply sets the targeted element to zero.</p>
     *
     * <p>This method modifies the provided array in-place and does not return a value.</p>
     *
     * @param weights      an array of doubles where each element is expected to be in the range [0, 1]
     * @param removeIndex the index whose value will be set to zero and redistributed
     * @throws IllegalArgumentException if the index is out of bounds or the array is null
     */
    void redistribute(double[] weights, int removeIndex);
}
