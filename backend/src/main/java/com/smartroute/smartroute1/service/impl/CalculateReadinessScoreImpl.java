package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Injuries;
import com.smartroute.smartroute1.service.ActivityProcessingService;
import com.smartroute.smartroute1.service.FatigueAndOverloadService;
import com.smartroute.smartroute1.service.InjuryAwareTrainingService;
import com.smartroute.smartroute1.service.ReadinessScoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class CalculateReadinessScoreImpl implements ReadinessScoreService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final FatigueAndOverloadService fatigueAndOverloadService;
    private final InjuryAwareTrainingService injuryAwareTrainingService;
    private final ActivityProcessingService activityProcessingService;

    // constants
    private static final double K_TSB = 3.0;
    private static final double T_0   = 10.0;
    private static final double K_FIT  = 3.0;
    private static final double C_0   = 80.0;
    private static final double GAMMA_INJURY = 3.0;

    // satisfaction score bounds
    private static final double SATISFACTION_MIN = 1.0;
    private static final double SATISFACTION_MAX = 5.0;

    // weights which sum up to 1
    // these weights can be adjusted if some values are missing
    private static final double WEIGHT_FITNESS = 0.25;
    private static final double WEIGHT_FATIGUE = 0.35;
    private static final double WEIGHT_INJURY  = 0.15;
    private static final double WEIGHT_LAST_SATISFACTION  = 0.25;

    // weight indices
    private static final int INDEX_FITNESS = 0;
    private static final int INDEX_FATIGUE = 1;
    private static final int INDEX_INJURY  = 2;
    private static final int INDEX_SATISFACTION = 3;

    public CalculateReadinessScoreImpl(
        FatigueAndOverloadService fatigueAndOverloadService,
        InjuryAwareTrainingService injuryAwareTrainingService,
        ActivityProcessingService activityProcessingService
    ) {
        this.fatigueAndOverloadService = fatigueAndOverloadService;
        this.injuryAwareTrainingService = injuryAwareTrainingService;
        this.activityProcessingService = activityProcessingService;
    }

    @Override
    public int calculateReadinessScore(ApplicationUser user, LocalDate date) {
        LOGGER.trace("calculateReadinessScore({}, {})", user, date);

        // CTL = Chronic Training Load
        double ctl = fatigueAndOverloadService.ctlOn(user, date);

        // TSB = Training Stress Balance
        double tsb = fatigueAndOverloadService.tsbOn(user, date);

        // copy the weights
        double[] weights = new double[]{
            WEIGHT_FITNESS,
            WEIGHT_FATIGUE,
            WEIGHT_INJURY,
            WEIGHT_LAST_SATISFACTION
        };

        // InjuryIndex = 0 (healthy) to 1 (severely injured)
        // if there are multiple injuries, we take the smallest index
        // if no injuries, redistribute weight
        double injuryIndex = Double.POSITIVE_INFINITY;
        List<Injuries> injuries = injuryAwareTrainingService.findInjuriesByEmail(user.getEmail());
        if (!injuries.isEmpty()) {
            // if there are injuries
            for (Injuries injury : injuries) {
                if (injury.getInjuryIndex() < injuryIndex) {
                    injuryIndex = injury.getInjuryIndex();
                }
            }
        } else {
            redistribute(weights, INDEX_INJURY);
            injuryIndex = 0.0;
        }

        // Satisfaction score from last activity before date
        // if no activity found, redistribute weight
        double satisfactionScore;
        Optional<Activity> latestActivityOpt =  activityProcessingService.getLastActivityBeforeDate(user.getEmail(), date);
        if (latestActivityOpt.isPresent() && latestActivityOpt.get().getSatisfactionScore() != null) {
            Activity latestActivity = latestActivityOpt.get();
            satisfactionScore = latestActivity.getSatisfactionScore();
        } else {
            redistribute(weights, INDEX_SATISFACTION);
            satisfactionScore = SATISFACTION_MIN;
        }

        // Calculate the scores
        // Fatigue Penalty: 1 / (1 + exp(-k * TSB/T0))
        double fatiguePenalty = 1.0 / (1.0 + Math.exp(-K_TSB * (tsb / T_0)));

        // Fitness Reward: 1 / (1 + exp(-k * (CTL/C0 - 1)))
        double fitnessReward = 1.0 / (1.0 + Math.exp(-K_FIT * ((ctl / C_0) - 1.0)));

        // Injury Penalty: exp(-gamma * injuryIndex)
        double injuryPenalty = Math.exp(-GAMMA_INJURY * injuryIndex);

        // normalized satisfaction score: (Sraw - Smin) / (Smax - Smin)
        double normalizedSatisfaction = (satisfactionScore - SATISFACTION_MIN) / (SATISFACTION_MAX - SATISFACTION_MIN);

        // calculate the final score
        double readiness =
              weights[INDEX_FITNESS] * fitnessReward
            + weights[INDEX_FATIGUE] * fatiguePenalty
            + weights[INDEX_INJURY]  * injuryPenalty
            + weights[INDEX_SATISFACTION] * normalizedSatisfaction;

        // return as value between 0 and 100
        return (int) Math.round(readiness * 100.0);
    }


    public void redistribute(double[] weights, int removeIndex) {
        LOGGER.trace("redistribute({})", removeIndex);
        // Validate input
        if (weights == null) {
            throw new IllegalArgumentException("Weights array cannot be null");
        }
        if (removeIndex < 0 || removeIndex >= weights.length) {
            throw new IllegalArgumentException("removeIndex is out of bounds");
        }

        // save the removed value
        final double removedValue = weights[removeIndex];

        // Set the removed index to 0
        weights[removeIndex] = 0.0;

        // Count how many other entries are > 0
        int countRecipients = 0;
        for (int i = 0; i < weights.length; i++) {
            if (i != removeIndex && weights[i] > 0.0) {
                countRecipients++;
            }
        }

        // If no one can receive anything, nothing to do
        if (countRecipients == 0) {
            return;
        }

        double share = removedValue / countRecipients;

        // Distribute evenly to all > 0 entries
        for (int i = 0; i < weights.length; i++) {
            if (i != removeIndex && weights[i] > 0.0) {
                weights[i] += share;
            }
        }
    }
}
