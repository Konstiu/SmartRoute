package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.TrainingEnvironment;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.service.InjuryAwareTrainingService;
import com.smartroute.smartroute1.service.ReadinessScoreService;
import com.smartroute.smartroute1.service.UserService;
import com.smartroute.smartroute1.service.WeatherService;
import com.smartroute.smartroute1.service.WorkoutTypeSelectorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class WorkoutTypeSelectorServiceImpl implements WorkoutTypeSelectorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final UserService userService;
    private final ReadinessScoreService readinessScoreService;
    private final WeatherService weatherService;
    private final InjuryAwareTrainingService injuryAwareTrainingService;

    // weights
    private static final double WEIGHT_FEASIBILITY     = 0.30;
    private static final double WEIGHT_READINESS_MATCH = 0.25;
    private static final double WEIGHT_WEATHER_MATCH   = 0.15;
    private static final double WEIGHT_RUN_PRIORITY    = 0.20;
    private static final double WEIGHT_VARIETY         = 0.10;

    public WorkoutTypeSelectorServiceImpl(
        UserService userService,
        ReadinessScoreService readinessScoreService,
        WeatherService weatherService,
        InjuryAwareTrainingService injuryAwareTrainingService
    ) {
        this.userService = userService;
        this.readinessScoreService = readinessScoreService;
        this.weatherService = weatherService;
        this.injuryAwareTrainingService = injuryAwareTrainingService;
    }

    /**
     * Definition of a workout session type.
     * @param type      WorkoutType
     * @param intensity I(a)
     * @param env       E(a)
     * @param impact    P(a)
     */
    private record SessionDef(
        WorkoutType type,
        double intensity,
        TrainingEnvironment env,
        double impact
    ) {}

    private static final Map<WorkoutType, SessionDef> sessionDefs = Map.of(
        WorkoutType.EASY_RUN,     new SessionDef(WorkoutType.EASY_RUN,     0.4, TrainingEnvironment.OUTDOOR, 0.6),
        WorkoutType.TEMPO_RUN,    new SessionDef(WorkoutType.TEMPO_RUN,    0.7, TrainingEnvironment.OUTDOOR, 0.8),
        WorkoutType.INTERVAL_RUN, new SessionDef(WorkoutType.INTERVAL_RUN, 0.9, TrainingEnvironment.OUTDOOR, 1.0),
        WorkoutType.LONG_RUN,     new SessionDef(WorkoutType.LONG_RUN,     0.6, TrainingEnvironment.OUTDOOR, 0.9),

        WorkoutType.GYM_PREHAB,   new SessionDef(WorkoutType.GYM_PREHAB,   0.3, TrainingEnvironment.INDOOR,  0.3),
        WorkoutType.MOBILITY,     new SessionDef(WorkoutType.MOBILITY,     0.2, TrainingEnvironment.INDOOR,  0.1)
    );

    @Override
    public WorkoutType selectWorkoutType(String email) {
        LOGGER.trace("selectWorkoutType({})", email);

        // get user
        ApplicationUser user = userService.findApplicationUserByEmail(email);

        // get today's date
        LocalDate today = LocalDate.now();

        // get today's readiness score
        // it ranges from 0 to 100, therefore normalization is needed
        int readinessScore = readinessScoreService.calculateReadinessScore(user, today);
        double normalizedReadiness = readinessScore / 100.0;

        // get today's weather score
        // TODO: when weather service is ready
        double weatherScore = 1.0;

        // Injury constraint (0) severely injured, (1) healthy
        // TODO: when injury service is ready
        double injuryConstraint = 1.0;

        // Workout history
        // TODO: when activity service is ready
        List<WorkoutType> recentWorkouts = List.of();

        WorkoutType bestType = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        // loop through all workout types and score them
        for (WorkoutType workoutType : WorkoutType.values()) {
            SessionDef sessionDef = sessionDefs.get(workoutType);
            double score = scoreWorkoutType(sessionDef, normalizedReadiness, weatherScore, injuryConstraint, recentWorkouts);

            if (score > bestScore) {
                bestScore = score;
                bestType = workoutType;
            }
        }

        return bestType;
    }

    private double scoreWorkoutType(SessionDef sessionDef, double readiness, double weather, double injuryConstraint, List<WorkoutType> recentWorkouts) {

        // Safety / Feasibility
        double feasibility = 1 - Math.max(0, sessionDef.impact - injuryConstraint);

        // Readiness match
        double readinessMatch = 1 - Math.abs(readiness - sessionDef.intensity);

        // Weather match
        double weatherMatch = (sessionDef.env == TrainingEnvironment.OUTDOOR) ? weather : 1.0;

        // Run priority
        double runPriority = isRunningSession(sessionDef.type) ? 1.0 : 0.7;

        // Variety
        double variety = recentWorkouts.contains(sessionDef.type) ? 0.8 : 1.0;

        // Final score as weighted sum
        return WEIGHT_FEASIBILITY * feasibility
            + WEIGHT_READINESS_MATCH * readinessMatch
            + WEIGHT_WEATHER_MATCH * weatherMatch
            + WEIGHT_RUN_PRIORITY * runPriority
            + WEIGHT_VARIETY * variety;
    }

    private boolean isRunningSession(WorkoutType workoutType) {
        return switch (workoutType) {
            case EASY_RUN, TEMPO_RUN, INTERVAL_RUN, LONG_RUN -> true;
            default -> false;
        };
    }

}
