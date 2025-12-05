package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Injuries;
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

        double injuryConstraint = calculateInjuryConstraint(injuryAwareTrainingService.findInjuriesByEmail(user.getEmail()));

        double trainabilityIndex = calculateTrainabilityIndex(readinessScore, overloadScore, injuryConstraint, consistencyScore);

        if (injuryConstraint < .4) {
            return false;
        }

        List<Activity> activitiesLast7Days = activityRepository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(user, from, to);

        if (activitiesLast7Days.size() > maxWeeklySessions) {
            return false;
        }

        double threshold = isPreferredDay(date, preferredDays) ? .25 : .35;
        return trainabilityIndex >= threshold;
    }

    // Checks if the selected training date is a preferred training day
    private boolean isPreferredDay(LocalDate date, Set<Weekday> preferredDays) {
        Weekday weekday = Weekday.valueOf(date.getDayOfWeek().name());
        return preferredDays.contains(weekday);
    }

    // Calculates the normalized overload score from the TSB
    private double calculateOverload(double tsb) {
        double clipped = Math.clamp(tsb, -40, 40);

        // map to 0..1
        return (40 - clipped) / 80.0;
    }

    // Returns the lowest injury constraint (= highest injuryIndex) from all active injuries
    private double calculateInjuryConstraint(List<Injuries> injuriesList) {
        return (1 - injuriesList.stream()
                .filter(i -> i.getLastHealthyDate() == null)
                .map(Injuries::getInjuryIndex)
                .reduce(0.0, Double::max));
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
        /*
        T_r = R_t/100 * C_t * (1 - O_t) * (1 + 0.5K_t)
        R_t...Readiness score
        O_t...Overload score
        C_t...Injury constraint
        K_t...Consistency score
         */

        double wrecovery = 0.50;
        double wconsistency = 0.50;

        double recoveryFactor = 1.0 - overloadScore;

        double baseScore = (wrecovery * recoveryFactor)
                + (wconsistency * consistencyScore);

        // === PART 2: Critical Gates (Multiplicative) ===
        // Readiness and injury gate the base score

        double readinessFactor = readinessScore / 100.0;

        // Apply square root to readiness for softer curve
        // This prevents very low readiness from completely zeroing out
        double readinessGate = Math.sqrt(readinessFactor);

        // Final score: base modified by gates
        double trainabilityIndex = baseScore * readinessGate * injuryConstraint;

        System.out.println("=== Trainability Calculation ===");
        System.out.println("readinessScore: " + readinessScore + " → gate: " + String.format("%.3f", readinessGate));
        System.out.println("overloadScore: " + String.format("%.3f", overloadScore) + " → recovery: " + String.format("%.3f", recoveryFactor));
        System.out.println("injuryConstraint: " + injuryConstraint);
        System.out.println("consistencyScore: " + String.format("%.3f", consistencyScore));
        System.out.println();
        System.out.println("Base score (recovery + consistency): " + String.format("%.3f", baseScore));
        System.out.println("Readiness gate: " + String.format("%.3f", readinessGate));
        System.out.println("Injury gate: " + String.format("%.3f", injuryConstraint));
        System.out.println("trainabilityIndex: " + String.format("%.3f", trainabilityIndex));
        System.out.println();

        return Math.clamp(trainabilityIndex, 0.0, 1.0);
    }
}
