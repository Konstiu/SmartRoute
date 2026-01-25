package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.DailySummary;
import com.smartroute.smartroute1.entity.ApplicationUser;

import java.util.List;

public interface DailyAggregationService {
    /**
     * Converts raw activities into daily aggregates.
     *
     * @param user a user.
     *
     * @param daysBack how many days for the user should be pulled.
     *
     * @return a list of {@link DailySummary}.
     */
    List<DailySummary> getDailySummaries(ApplicationUser user, int daysBack);
}
