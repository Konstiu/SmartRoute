package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.service.ReadinessScoreService;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class CalculateReadinessScoreImpl implements ReadinessScoreService {
    @Override
    public int calculateReadinessScore(ApplicationUser user, LocalDate date) {
        throw new NotImplementedException();
    }
}
