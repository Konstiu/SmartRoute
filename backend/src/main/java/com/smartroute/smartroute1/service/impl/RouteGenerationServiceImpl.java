package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.RouteDto;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.service.RouteGenerationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RouteGenerationServiceImpl implements RouteGenerationService {

    private final ActivityRepository activityRepository;
    private static final int historySize = 10;

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final double easyDistanceFactor = 0.8f; // 0.7 - 1.0
    private static final double tempoDistanceFactor = 0.9f; // 0.8 - 1.1
    private static final double longDistanceFactor = 1.1f; // 1.0 - 1.3
    private static final double maxDistanceProgress = 1000; // 1.0 - 1.3

    private static final double readinessPacePenalty = 0.1;
    private static final double easyPaceFactor = 0.1; // 0.05 - 0.15
    private static final double tempoPaceFactor = 0.2; // 0.10 - 0.25
    private static final double longPaceFactor = 0.05; // 0.0 - 0.1

    // https://www.dailymail.co.uk/health/article-14071885/How-long-able-run-without-stopping-age.html
    private static final double[][] firstDistanceMale = {
            { 2800, 2400, 2200, 1600, 1400 }, // 20 - 29
            { 2700, 2300, 1900, 1500, 1300 }, // 30 - 39
            { 2500, 2100, 1700, 1400, 1200 }, // 40 - 49
            { 2400, 2000, 1600, 1300, 1100 }, // 50+
    };
    private static final double[][] firstDistanceFemale = {
            { 2700, 2200, 1800, 1500, 1300 }, // 20 - 29
            { 2500, 2000, 1700, 1400, 1200 }, // 30 - 39
            { 2300, 1900, 1500, 1200, 1000 }, // 40 - 49
            { 2200, 1700, 1400, 1100, 900 }, // 50+
    };

    @Override
    public RouteDto generateRouteDetails(ApplicationUser user, WorkoutType workoutType, double readinessScore) {
        List<Activity> activities = activityRepository.findTop10ByUserAndTypeIsOrderByStartDateDesc(
                user, "Run", PageRequest.of(0, historySize));

        if (activities.isEmpty()) {
            double distanceEstimate = estimateFirstDistance(user);
            return new RouteDto(distanceEstimate * 1.5, distanceEstimate / 30.0, getElevationCeiling(workoutType));
        }

        double targetDistance = calculateTargetDistance(activities, workoutType, readinessScore);
        double targetPace = calculateTargetPace(activities, workoutType, readinessScore);

        return new RouteDto(targetDistance, targetPace, getElevationCeiling(workoutType));
    }

    private double estimateFirstDistance(ApplicationUser user) {
        int levelIndex = switch (user.getExperienceLevel()) {
            case BEGINNER -> 0;
            case CASUAL -> 1;
            case INTERMEDIATE -> 2;
            case ADVANCED -> 3;
            case COMPETITIVE_ATHLETE -> 4;
        };

        long age = ChronoUnit.YEARS.between(user.getBirthdate(), LocalDate.now());
        int ageIndex = 0;
        if (age >= 50) {
            ageIndex = 3;
        } else if (age >= 40) {
            ageIndex = 2;
        } else if (age >= 30) {
            ageIndex = 1;
        }

        if (user.getSex() == Sex.MALE) {
            return firstDistanceMale[ageIndex][levelIndex];
        }

        return firstDistanceFemale[ageIndex][levelIndex];
    }

    private double calculateTargetDistance(List<Activity> activities, WorkoutType workoutType, double readinessScore) {
        double[] distances = activities.stream().map(Activity::getDistance).mapToDouble(Float::doubleValue).sorted().toArray();

        double maxDistance = distances[distances.length - 1];
        double medianDistance = distances[distances.length / 2];
        if (distances.length % 2 == 0) {
            medianDistance = (medianDistance + distances[distances.length / 2 + 1]) / 2;
        }

        double readinessModifier = 0.8 + 0.4 * (readinessScore / 100);

        if (workoutType == WorkoutType.EASY_RUN) {
            return medianDistance * easyDistanceFactor * readinessModifier;
        } else if (workoutType == WorkoutType.TEMPO_RUN || workoutType == WorkoutType.INTERVAL_RUN) {
            return medianDistance * tempoDistanceFactor * readinessModifier;
        } else {
            return Math.min(maxDistance * longDistanceFactor * readinessModifier, maxDistance + maxDistanceProgress);
        }
    }

    private double calculateTargetPace(List<Activity> activities, WorkoutType workoutType, double readinessScore) {
        double[] averagePaces = activities.stream()
                .map(a -> (a.getMovingTime() / 60) / (a.getDistance() / 1000))
                .sorted()
                .mapToDouble(Float::doubleValue).toArray();
        LOGGER.info("paces {}", averagePaces);

        double medianPace = averagePaces[averagePaces.length / 2];
        if (averagePaces.length % 2 == 0) {
            medianPace = (medianPace + averagePaces[averagePaces.length / 2 - 1]) / 2;
        }

        double readinessModifier = (1 + readinessPacePenalty * (1 - readinessScore / 100));

        if (workoutType == WorkoutType.EASY_RUN) {
            return 1000 / (medianPace * (1 + easyPaceFactor) * readinessModifier) / 60;
        } else if (workoutType == WorkoutType.TEMPO_RUN || workoutType == WorkoutType.INTERVAL_RUN) {
            return 1000 / (medianPace * (1 - tempoPaceFactor) * readinessModifier) / 60;
        } else {
            assert workoutType == WorkoutType.LONG_RUN;
            return 1000 / (medianPace * (1 + longPaceFactor) * readinessModifier) / 60;
        }
    }

    private double getElevationCeiling(WorkoutType workoutType) {
        if (workoutType == WorkoutType.EASY_RUN) {
            return 80;
        } else if (workoutType == WorkoutType.TEMPO_RUN || workoutType == WorkoutType.INTERVAL_RUN) {
            return 150;
        } else {
            assert workoutType == WorkoutType.LONG_RUN;
            return 300;
        }
    }
}
