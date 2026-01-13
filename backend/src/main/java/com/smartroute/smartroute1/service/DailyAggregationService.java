package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.DailySummary;

import java.util.List;

public interface DailyAggregationService {
    /**
     * Converts raw activities into daily aggregates.
     *
     * @param userId id of a user.
     *
     * @param daysBack how many days for the user should be pulled.
     *
     * @return a list of {@link DailySummary}.
     */
    List<DailySummary> getDailySummaries(Long userId, int daysBack);
}
