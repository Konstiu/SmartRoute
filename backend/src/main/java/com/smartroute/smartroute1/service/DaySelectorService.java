package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.entity.ApplicationUser;

import java.time.LocalDate;

public interface DaySelectorService {
    /**
     * Decide if a given day is a training or rest day for a user by
     * calculating a trainabilityIndex and taking into account injuries and
     * preferred training days.
     *
     * @param date the date to decide if it is a training or rest day
     * @param user the user to select the day for
     * @return true if it is a training day for the user, false otherwise
     */
    boolean isTrainingDay(LocalDate date, ApplicationUser user);

    /**
     * Calculates the trainability index based on the readinessScore, overloadScore, injuryConstraint and
     * consistencyScore.
     *
     * @param readinessScore the readiness score
     * @param overloadScore the overload score
     * @param injuryConstraint the injury constraint
     * @param consistencyScore the consistency score
     * @return the normalized trainabilityIndex
     */
    double calculateTrainabilityIndex(int readinessScore, double overloadScore, double injuryConstraint, double consistencyScore);
}
