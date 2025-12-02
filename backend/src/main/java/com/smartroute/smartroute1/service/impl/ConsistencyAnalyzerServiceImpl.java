package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.ConsistencyScoreResultDto;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.ConsistencyScore;
import com.smartroute.smartroute1.exception.CannotCalculateConsistencyScoreException;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.ConsistencyRepository;
import com.smartroute.smartroute1.service.ConsistencyAnalyzerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;
import java.time.Instant;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ConsistencyAnalyzerServiceImpl implements ConsistencyAnalyzerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final ActivityRepository activityRepository;
    private final ConsistencyRepository consistencyRepository;
    private final double alphaF = 0.4;
    private final double betaR = 0.4;
    private final double weightF = 0.5;
    private final double weightR = 0.5;

    public ConsistencyAnalyzerServiceImpl(ActivityRepository activityRepository, ConsistencyRepository consistencyRepository) {
        this.activityRepository = activityRepository;
        this.consistencyRepository = consistencyRepository;
    }

    @Override
    public ConsistencyScoreResultDto computeScore(ApplicationUser user, Instant start, Instant end, int plannedSessionsPerWeek) {
        LOGGER.trace("Computing Consistency Score for User {}", user);
        List<Activity> sessions = new ArrayList<>(activityRepository
                .findAllByUserAndStartDateBetweenOrderByStartDateAsc(user, start, end));


        if (sessions.isEmpty()) {
            throw new CannotCalculateConsistencyScoreException("No sessions found for user " + user);
        }
        if (plannedSessionsPerWeek <= 0) {
            throw new CannotCalculateConsistencyScoreException("Planned sessions per week are negative or zero");
        }

        sessions.sort(Comparator.comparing(Activity::getStartDate));

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

        double score = weightF * frequencyConsistency + weightR * sessionRegularity;
        score = Math.max(0, Math.min(1, score));
        ConsistencyScoreResultDto result = new ConsistencyScoreResultDto(score, frequencyConsistency, sessionRegularity);

        consistencyRepository.save(new ConsistencyScore(user, result));
        LOGGER.debug("Computed and Saved Score for User {}: {}", user, score);
        return result;
    }
}
