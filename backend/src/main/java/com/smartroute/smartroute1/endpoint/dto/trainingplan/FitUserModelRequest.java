package com.smartroute.smartroute1.endpoint.dto.trainingplan;

import java.util.List;

public record FitUserModelRequest(
        String userId,
        String experienceLevel,
        List<PplDailyObs> days,
        double ctl0,
        double atl0,
        long seed
) {}
