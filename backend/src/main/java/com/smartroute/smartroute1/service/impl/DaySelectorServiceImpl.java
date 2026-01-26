package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.Weekday;
import com.smartroute.smartroute1.exception.CannotCalculateConsistencyScoreException;
import com.smartroute.smartroute1.exception.InsufficientTrainingDataException;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.service.ConsistencyAnalyzerService;
import com.smartroute.smartroute1.service.DaySelectorService;
import com.smartroute.smartroute1.service.FatigueAndOverloadService;
import com.smartroute.smartroute1.service.InjuryAwareTrainingService;
import com.smartroute.smartroute1.service.ReadinessScoreService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@Service
@AllArgsConstructor
public class DaySelectorServiceImpl implements DaySelectorService {
    public static final double PREFERRED_DAY_THRESHOLD = .25;
    public static final double REGULAR_DAY_THRESHOLD = .35;
    private static final double INJURY_CONSTRAINT_THRESHOLD = .5;
    public static final double WEEKLY_SESSION_BALANCE_WEIGHT = .1;
    public static final double MAX_WEEKLY_SESSIONS_EXCEEDED_PENALTY = 3;

    private ActivityRepository activityRepository;
    private ReadinessScoreService readinessScoreService;
    private ConsistencyAnalyzerService consistencyAnalyzerService;
    private InjuryAwareTrainingService injuryAwareTrainingService;
    private FatigueAndOverloadService fatigueAndOverloadService;

    @Override
    public boolean isTrainingDay(LocalDate date, ApplicationUser user) {
        ExperienceLevel experienceLevel = user.getExperienceLevel();
        if (experienceLevel == null) {
            experienceLevel = ExperienceLevel.CASUAL;
        }

        Instant from = date.minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant to = date.atStartOfDay(ZoneId.systemDefault()).toInstant();

        Set<Weekday> preferredDays = user.getActiveWeekdays();
        int plannedWeeklySessions = preferredDays.size();
        int minWeeklySessions = getMinWeeklySessions(experienceLevel);
        int maxWeeklySessions = getMaxWeeklySessions(experienceLevel);


        double consistencyScore;
        try {
            consistencyScore = consistencyAnalyzerService.computeScore(
                            user,
                            from,
                            to,
                            Math.clamp(plannedWeeklySessions, minWeeklySessions, maxWeeklySessions))
                    .getFinalScore();
        } catch (CannotCalculateConsistencyScoreException e) {
            // fall back to default value
            consistencyScore = 0.0;
        }

        int readinessScore;
        double overloadScore;
        try {
            readinessScore = readinessScoreService.calculateReadinessScore(user, date);
            overloadScore = calculateOverload(fatigueAndOverloadService.tsbOn(user, date));

        } catch (InsufficientTrainingDataException e) {
            // fall back to default values
            readinessScore = 50;
            overloadScore = 0;
        }
        double injuryConstraint = injuryAwareTrainingService.getInjuryConstraint(user.getEmail());

        if (injuryConstraint < INJURY_CONSTRAINT_THRESHOLD) {
            return false;
        }

        List<Activity> activitiesLast7Days = activityRepository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(user, from, to);

        double weeklySessionBalance = calculateWeeklySessionBalance(minWeeklySessions, maxWeeklySessions, activitiesLast7Days);
        double trainabilityIndex = calculateTrainabilityIndex(readinessScore, overloadScore, injuryConstraint, consistencyScore);

        double weightedWeeklySessionBalance = weeklySessionBalance * WEEKLY_SESSION_BALANCE_WEIGHT;

        double threshold = isPreferredDay(date, preferredDays) ? PREFERRED_DAY_THRESHOLD : REGULAR_DAY_THRESHOLD;
        return (trainabilityIndex + weightedWeeklySessionBalance) >= threshold;
    }

    // Checks if the selected training date is a preferred training day
    private boolean isPreferredDay(LocalDate date, Set<Weekday> preferredDays) {
        Weekday weekday = Weekday.valueOf(date.getDayOfWeek().name());
        return preferredDays.contains(weekday);
    }

    // Returns a value [-1, MAX_WEEKLY_SESSIONS_EXCEEDED_PENALTY].
    // -1: far above max (scaled based on distance)
    //  0: within optimal range [min, max] (inclusive)
    //  MAX_WEEKLY_SESSIONS_EXCEEDED_PENALTY: far below min (scaled based on distance)
    private double calculateWeeklySessionBalance(int minWeeklySessions, int maxWeeklySessions, List<Activity> activitiesLast7Days) {
        int countLast7Days = activitiesLast7Days.size();

        if (countLast7Days >= minWeeklySessions && countLast7Days <= maxWeeklySessions) {
            return 0.0;
        }

        if (countLast7Days < minWeeklySessions) {
            // Distance from min (closer to min = smaller distance)
            double distance = minWeeklySessions - countLast7Days;
            return Math.min(1.0, distance / minWeeklySessions);
        } else {
            // Distance from max
            double distance = countLast7Days - maxWeeklySessions;
            return -Math.min(1.0, (distance / maxWeeklySessions)) * MAX_WEEKLY_SESSIONS_EXCEEDED_PENALTY;
        }
    }

    // Calculates the normalized overload score from the TSB
    private double calculateOverload(double tsb) {
        double clipped = Math.clamp(tsb, -40, 40);

        // map to 0..1
        return (40 - clipped) / 80.0;
    }

    // Min weekly sessions by experience (recommendations for beginner, intermediate, advanced from: https://pubmed.ncbi.nlm.nih.gov/19204579/)
    private int getMinWeeklySessions(ExperienceLevel experienceLevel) {
        return switch (experienceLevel) {
            case BEGINNER -> 2;
            case CASUAL -> 2;
            case INTERMEDIATE -> 3;
            case ADVANCED -> 4;
            case COMPETITIVE_ATHLETE -> 4;
        };
    }

    // Max weekly sessions by experience (recommendations for beginner, intermediate, advanced from: https://pubmed.ncbi.nlm.nih.gov/19204579/)
    private int getMaxWeeklySessions(ExperienceLevel experienceLevel) {
        return switch (experienceLevel) {
            case BEGINNER -> 3;
            case CASUAL -> 4;
            case INTERMEDIATE -> 4;
            case ADVANCED -> 5;
            case COMPETITIVE_ATHLETE -> 6;
        };
    }

    @Override
    public double calculateTrainabilityIndex(int readinessScore, double overloadScore, double injuryConstraint, double consistencyScore) {
        double normalizedReadiness = readinessScore / 100.0;
        double recoveryFactor = (1 - overloadScore);
        double baseTrainability = (.55 * normalizedReadiness) + (.45 * recoveryFactor);
        double injuryAdjusted = baseTrainability * injuryConstraint;
        double trainabilityIndex = injuryAdjusted * (1 + .15 *  consistencyScore);

        return Math.clamp(trainabilityIndex, 0.0, 1.0);
    }
}
