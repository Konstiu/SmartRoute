package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.entity.ApplicationUser;

import java.time.LocalDate;

public interface ReadinessScoreService {
    int calculateReadinessScore(ApplicationUser user, LocalDate date);
}
