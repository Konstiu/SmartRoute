package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.WeatherImpactDto;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.entity.enums.TrainingEnvironment;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.service.ActivityProcessingService;
import com.smartroute.smartroute1.service.DaySelectorService;
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
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WorkoutTypeSelectorServiceImpl implements WorkoutTypeSelectorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final UserService userService;
    private final ReadinessScoreService readinessScoreService;
    private final WeatherService weatherService;
    private final InjuryAwareTrainingService injuryAwareTrainingService;
    private final ActivityProcessingService activityProcessingService;
    private final DaySelectorService daySelectorService;

    // constants
    private static final int LAST_N_WORKOUTS_FOR_VARIETY = 3;
    private static final double NO_RUN_WORKOUT_PENALTY   = 0.7;
    private static final double NO_VARIETY_PENALTY       = 0.8;

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
        InjuryAwareTrainingService injuryAwareTrainingService,
        ActivityProcessingService activityProcessingService,
        DaySelectorService daySelectorService
    ) {
        this.userService = userService;
        this.readinessScoreService = readinessScoreService;
        this.weatherService = weatherService;
        this.injuryAwareTrainingService = injuryAwareTrainingService;
        this.activityProcessingService = activityProcessingService;
        this.daySelectorService = daySelectorService;
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

    /**
     * Mapping of workout types to their session definitions.
     */
    private static final Map<WorkoutType, SessionDef> sessionDefs = Map.of(
        WorkoutType.EASY_RUN,     new SessionDef(WorkoutType.EASY_RUN,     0.4, TrainingEnvironment.OUTDOOR, 0.6),
        WorkoutType.TEMPO_RUN,    new SessionDef(WorkoutType.TEMPO_RUN,    0.7, TrainingEnvironment.OUTDOOR, 0.8),
        WorkoutType.INTERVAL_RUN, new SessionDef(WorkoutType.INTERVAL_RUN, 0.9, TrainingEnvironment.OUTDOOR, 1.0),
        WorkoutType.LONG_RUN,     new SessionDef(WorkoutType.LONG_RUN,     0.6, TrainingEnvironment.OUTDOOR, 0.9),

        WorkoutType.GYM_PREHAB,   new SessionDef(WorkoutType.GYM_PREHAB,   0.3, TrainingEnvironment.INDOOR,  0.3),
        WorkoutType.MOBILITY,     new SessionDef(WorkoutType.MOBILITY,     0.2, TrainingEnvironment.INDOOR,  0.1)
    );

    @Override
    public WorkoutType selectWorkoutType(String email, double latitude, double longitude, boolean ignoreRestDay) throws ValidationException {
        LOGGER.trace("selectWorkoutType({}, {}, {}, {})", email, latitude, longitude, ignoreRestDay);

        // get user
        ApplicationUser user = userService.findApplicationUserByEmail(email);

        // get today's date
        LocalDate today = LocalDate.now();

        // check if today is a training day
        // if not, return REST_DAY
        if (!ignoreRestDay) {
            boolean isTrainingDay = daySelectorService.isTrainingDay(today, user);
            if (!isTrainingDay) {
                return WorkoutType.REST_DAY;
            }
        }

        // get user age
        int userAge = calculateAge(user);

        // get current time in UTC
        ZonedDateTime utcDateTime = LocalDateTime.now()
            .atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneId.of("UTC"));
        String utcTimeStr = utcDateTime.format(DateTimeFormatter.ISO_INSTANT);

        // get today's readiness score
        // it ranges from 0 to 100, therefore normalization is needed
        int readinessScore = readinessScoreService.calculateReadinessScore(user, today);
        double normalizedReadiness = readinessScore / 100.0;

        // get users last distance run data
        // if no run found, use a default value of 3000m
        Optional<Activity> lastRun = activityProcessingService.getLastRunningActivityBeforeDate(email, today);
        int lastDistance = lastRun.map(activity -> (int) activity.getDistance()).orElse(3000);

        // get today's weather score
        WeatherResponse weatherResponse = weatherService.getWeatherAtTime(latitude, longitude, utcTimeStr);
        WeatherImpactDto weatherImpact = weatherService.calculateWeatherScore(weatherResponse, userAge, lastDistance);
        double weatherScore = weatherImpact.getWeatherScore();

        // Injury constraint (0) severely injured, (1) healthy
        double injuryConstraint = injuryAwareTrainingService.getInjuryConstraint(email);

        // Workout history
        List<WorkoutType> recentWorkouts = activityProcessingService.getLastNActivities(email, LAST_N_WORKOUTS_FOR_VARIETY)
            .stream()
            .map(Activity::getWorkoutType)
            .toList();

        WorkoutType bestType = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        // loop through all workout types and score them
        for (WorkoutType workoutType : WorkoutType.values()) {
            // skip rest day
            if (workoutType == WorkoutType.REST_DAY) {
                continue;
            }
            SessionDef sessionDef = sessionDefs.get(workoutType);
            double score = scoreWorkoutType(sessionDef, normalizedReadiness, weatherScore, injuryConstraint, recentWorkouts);

            if (score > bestScore) {
                bestScore = score;
                bestType = workoutType;
            }
        }

        return bestType;
    }

    /**
     * Helper method to score a workout type based on multiple factors.
     *
     * @param sessionDef the session definition
     * @param readiness the user's normalized readiness score
     * @param weather the weather score for outdoor running
     * @param injuryConstraint the injury constraint factor
     * @param recentWorkouts list of recent workout types for variety consideration
     * @return the computed score for the workout type
     */
    private double scoreWorkoutType(SessionDef sessionDef, double readiness, double weather, double injuryConstraint, List<WorkoutType> recentWorkouts) {

        // Safety / Feasibility
        double feasibility = 1 - Math.max(0, sessionDef.impact - injuryConstraint);

        // Readiness match
        double readinessMatch = 1 - Math.abs(readiness - sessionDef.intensity);

        // Weather match
        double weatherMatch = (sessionDef.env == TrainingEnvironment.OUTDOOR) ? weather : 1.0;

        // Run priority
        double runPriority = !isRunningSession(sessionDef.type) ? NO_RUN_WORKOUT_PENALTY : 1.0;

        // Variety
        double variety = recentWorkouts.contains(sessionDef.type) ? NO_VARIETY_PENALTY : 1.0;

        // Final score as weighted sum
        return WEIGHT_FEASIBILITY * feasibility
            + WEIGHT_READINESS_MATCH * readinessMatch
            + WEIGHT_WEATHER_MATCH * weatherMatch
            + WEIGHT_RUN_PRIORITY * runPriority
            + WEIGHT_VARIETY * variety;
    }

    /**
     * Helper method to determine if a workout type is a running session.
     *
     * @param workoutType the workout type to check
     * @return true if it is a running session, false otherwise
     */
    private boolean isRunningSession(WorkoutType workoutType) {
        return switch (workoutType) {
            case EASY_RUN, TEMPO_RUN, INTERVAL_RUN, LONG_RUN -> true;
            default -> false;
        };
    }

    /**
     * Help method to calculate the age of a user.
     * If the birthdate is not set, a fallback age of 30 is used.
     *
     * @param user the user to calculate the age for
     * @return the age of the user
     */
    private int calculateAge(ApplicationUser user) {
        LocalDate birthDate = user.getBirthdate();

        // fallback, if age is not set
        if (birthDate == null) {
            return 30;
        }

        return Period.between(birthDate, LocalDate.now()).getYears();
    }

}
