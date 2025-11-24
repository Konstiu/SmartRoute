package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.ConsistencyScoreResultDto;
import com.smartroute.smartroute1.entity.StravaAccount;
import com.smartroute.smartroute1.entity.StravaActivity;
import com.smartroute.smartroute1.repository.StravaActivityRepository;
import com.smartroute.smartroute1.service.ConsistencyAnalyzerService;
import org.springframework.stereotype.Service;

import java.time.Instant;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ConsistencyAnalyzerServiceImpl implements ConsistencyAnalyzerService {

    private final StravaActivityRepository stravaActivityRepository;
    private final double alphaF = 0.4;
    private final double betaR = 0.4;

    public ConsistencyAnalyzerServiceImpl(StravaActivityRepository stravaActivityRepository) {
        this.stravaActivityRepository = stravaActivityRepository;
    }

    @Override
    public ConsistencyScoreResultDto computeScore(StravaAccount user, Instant start, Instant end, int plannedSessionsPerWeek) {
        List<StravaActivity> sessions = new ArrayList<>(stravaActivityRepository
                .findAllByStravaAccountAndStartDateBetweenOrderByStartDateAsc(user, start, end));


        if (sessions.isEmpty()) {
            return new ConsistencyScoreResultDto(0.0, 0.0, 0.0);
        }
        sessions.sort(Comparator.comparing(StravaActivity::getStartDate));

        // Frequency Consistency
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        long weeks = Math.max(1, days / 7);

        double actualSessionsPerWeek = (double) sessions.size() / (double) weeks;

        double frequencyConsistency = Math.exp(-alphaF * Math.abs(actualSessionsPerWeek - plannedSessionsPerWeek));

        // Session Regularity
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < sessions.size(); i++) {
            long diff = ChronoUnit.DAYS.between(
                    sessions.get(i - 1).getStartDate(),
                    sessions.get(i).getStartDate()
            );
            intervals.add(diff);
        }

        double sessionRegularity = 1.0;
        if (!intervals.isEmpty()) {
            double expectedInterval = 7.0 / plannedSessionsPerWeek;
            if (intervals.size() == 1) {
                double deviation = Math.abs(intervals.getFirst() - expectedInterval);
                sessionRegularity = Math.exp(-betaR * (deviation / expectedInterval));
            } else {
                double avg = intervals.stream().mapToLong(x -> x).average().orElse(0);
                double sumDeviation = intervals.stream().mapToDouble(x -> Math.abs(x - avg)).sum();

                sessionRegularity = Math.exp(-betaR * (sumDeviation / intervals.size()));
            }
        }

        // Composite Score

        double score = 0.5 * frequencyConsistency + 0.5 * sessionRegularity;
        score = Math.max(0, Math.min(1, score));
        return new ConsistencyScoreResultDto(score, frequencyConsistency, sessionRegularity);
    }
}
