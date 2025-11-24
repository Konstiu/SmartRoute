package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.ConsistencyScoreResultDto;
import com.smartroute.smartroute1.entity.StravaAccount;

import java.time.Instant;

public interface ConsistencyAnalyzerService {

    ConsistencyScoreResultDto computeScore(StravaAccount user, Instant start, Instant end, int plannedTrainingSessions);
}
