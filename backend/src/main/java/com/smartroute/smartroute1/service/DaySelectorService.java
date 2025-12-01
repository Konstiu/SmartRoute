package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.entity.ApplicationUser;

import java.time.LocalDate;

public interface DaySelectorService {
    boolean isTrainingDay(LocalDate date, ApplicationUser user);

    double calculateTrainabilityIndex(int readinessScore, double overloadScore, double injuryConstraint, double consistencyScore);
}
