package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.RecommendedActivityDto;
import com.smartroute.smartroute1.endpoint.dto.ViewInjuryDto;
import com.smartroute.smartroute1.endpoint.dto.CompactWeatherDto;
import com.smartroute.smartroute1.endpoint.dto.AthleteStatusDto;
import com.smartroute.smartroute1.endpoint.dto.GymWorkoutDto;
import com.smartroute.smartroute1.endpoint.dto.RouteDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.PlannedDayDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlan7dDto;
import com.smartroute.smartroute1.endpoint.mapper.InjuryMapper;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Injuries;
import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.exception.InsufficientTrainingDataException;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.exception.WeatherException;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.FatigueAndOverloadService;
import com.smartroute.smartroute1.service.GymWorkoutSelectorService;
import com.smartroute.smartroute1.service.InjuryAwareTrainingService;
import com.smartroute.smartroute1.service.ReadinessScoreService;
import com.smartroute.smartroute1.service.RouteGenerationService;
import com.smartroute.smartroute1.service.TrainingPlanService;
import com.smartroute.smartroute1.service.WeatherService;
import com.smartroute.smartroute1.service.WorkoutTypeSelectorService;
import com.smartroute.smartroute1.service.TrainingPlan7dService;
import com.smartroute.smartroute1.service.TrainingPlanStore;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@AllArgsConstructor
public class TrainingPlanServiceImpl implements TrainingPlanService {

    private final RouteGenerationService routeGenerationService;
    private WorkoutTypeSelectorService typeSelectorService;
    private WeatherService weatherService;
    private FatigueAndOverloadService fatigueAndOverloadService;
    private UserRepository userRepository;
    private ReadinessScoreService readinessScoreService;
    private InjuryAwareTrainingService injuryAwareTrainingService;
    private InjuryMapper injuryMapper;
    private GymWorkoutSelectorService gymWorkoutSelectorService;

    private final TrainingPlan7dService trainingPlan7dService;
    private final Clock clock = Clock.system(ZoneId.of("Europe/Vienna"));

    @Override
    public RecommendedActivityDto getTrainingPlan(String email, double latitude, double longitude) {
        try {
            // get current time in UTC
            ZonedDateTime utcDateTime = LocalDateTime.now()
                    .atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(ZoneId.of("UTC")).withMinute(0).withSecond(0).withNano(0);
            String utcTimeStr = utcDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));

            // get user and calculate age
            ApplicationUser user = userRepository.findUserByEmail(email);
            if (user == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }
            LocalDate today = LocalDate.now();
            int age = Period.between(user.getBirthdate(), today).getYears();

            // get weather info
            WeatherResponse weatherResponse = weatherService.getWeatherAtTime(latitude, longitude, utcTimeStr);
            if (weatherResponse == null) {
                throw new WeatherException("Failed to get weather at " + utcTimeStr);
            }
            double weatherScore = weatherService.calculateWeatherScore(weatherResponse);

            // get TSB and readiness
            double tsb;
            int readinessScore;
            try {
                tsb = fatigueAndOverloadService.tsbOn(user, today);
                readinessScore = readinessScoreService.calculateReadinessScore(user, today);
            } catch (InsufficientTrainingDataException e) {
                // Not enough data
                tsb = 0;
                readinessScore = 50;
            }

            // get injuries
            double injuryIndex = injuryAwareTrainingService.getInjuryIndex(email);
            List<Injuries> allInjuries = injuryAwareTrainingService.findInjuriesByEmail(email);
            List<Injuries> injuriesActive = allInjuries
                    .stream()
                    .filter(i -> i.getLastInjuryDate() == null || i.getLastInjuryDate()
                            .isAfter(today.minusDays(14))).toList();
            List<ViewInjuryDto> injuries = new ArrayList<>();
            for (Injuries injury : injuriesActive) {
                injuries.add(injuryMapper.entitytoDto(injury));
            }

            // select workout type
            WorkoutType selectedWorkout = typeSelectorService.selectWorkoutType(email, latitude, longitude, false);


            RecommendedActivityDto dto = new RecommendedActivityDto();

            // set weather info
            dto.setWeather(new CompactWeatherDto(
                    weatherScore,
                    weatherResponse.getTemperature2m(),
                    weatherResponse.getWindSpeed10m(),
                    weatherResponse.getPrecipitation(),
                    weatherResponse.getRelativeHumidity(),
                    weatherResponse.getUvIndex(),
                    weatherService.estimatePerformancePenalty(weatherResponse),
                    weatherService.evaluateWeatherScore(weatherScore),
                    weatherService.buildWeatherDescription(weatherResponse)
            ));

            // set athlete status
            dto.setAthleteStatus(new AthleteStatusDto(
                    tsb,
                    readinessScore,
                    injuryIndex,
                    injuries
            ));
            Set<WorkoutType> runWorkouts = Set.of(WorkoutType.EASY_RUN, WorkoutType.TEMPO_RUN, WorkoutType.INTERVAL_RUN, WorkoutType.LONG_RUN);
            Set<WorkoutType> gymWorkouts = Set.of(WorkoutType.GYM_PREHAB, WorkoutType.MOBILITY);
            if (runWorkouts.contains(selectedWorkout)) {
                if (tsb < -15) {
                    selectedWorkout = WorkoutType.GYM_PREHAB;
                }
            }
            // set workout type
            if (selectedWorkout == WorkoutType.REST_DAY) {
                dto.setType(RecommendedActivityDto.SessionType.REST);
                dto.setName(selectedWorkout.toString());
            } else if (runWorkouts.contains(selectedWorkout)) {
                dto.setType(RecommendedActivityDto.SessionType.RUN);
                dto.setName(selectedWorkout.toString());

                dto.setRoute(routeGenerationService.generateRouteDetails(user, selectedWorkout, readinessScore));

            } else if (gymWorkouts.contains(selectedWorkout)) {
                dto.setType(RecommendedActivityDto.SessionType.GYM);
                dto.setName("Gym Workout");

                // get gym workout
                GymWorkoutDto gymWorkout = gymWorkoutSelectorService.getGymWorkout(
                        user,
                        injuryAwareTrainingService.calculateInjuriesMap(allInjuries),
                        readinessScore
                );
                dto.setGymSession(gymWorkout);
            }

            return dto;
        } catch (ValidationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // plan retrieval (cache/db)
    private final TrainingPlanStore trainingPlanStore;

    @Override
    public RecommendedActivityDto getPlannedDay(String email, String planId, LocalDate date) {
        ApplicationUser user = userRepository.findUserByEmail(email);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        TrainingPlan7dDto plan = trainingPlanStore.get(email, planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found"));

        AthleteStatusDto status = buildCurrentAthleteStatus(email, user, LocalDate.now());

        PlannedDayDto day = plan.getDays().stream()
                .filter(d -> date.equals(d.getDate()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Day not in plan"));

        return project(day, status);
    }

    private RecommendedActivityDto project(PlannedDayDto day, AthleteStatusDto status) {
        RecommendedActivityDto dto = new RecommendedActivityDto();
        dto.setAthleteStatus(status);
        dto.setWeather(day.getWeatherDto());
        dto.setName(day.getWorkoutType().toString());

        WorkoutType wt = day.getWorkoutType();
        if (wt == WorkoutType.REST_DAY) {
            dto.setType(RecommendedActivityDto.SessionType.REST);
            return dto;
        }

        if (isRun(wt)) {
            dto.setType(RecommendedActivityDto.SessionType.RUN);
            dto.setRoute(day.getRouteDto());
            return dto;
        }

        dto.setType(RecommendedActivityDto.SessionType.GYM);
        dto.setGymSession(day.getGymWorkout());
        return dto;
    }

    private boolean isRun(WorkoutType wt) {
        return wt == WorkoutType.EASY_RUN || wt == WorkoutType.TEMPO_RUN
                || wt == WorkoutType.INTERVAL_RUN || wt == WorkoutType.LONG_RUN;
    }

    private AthleteStatusDto buildCurrentAthleteStatus(String email, ApplicationUser user, LocalDate today) {
        double tsb;
        int readiness;

        try {
            tsb = fatigueAndOverloadService.tsbOn(user, today);
            readiness = readinessScoreService.calculateReadinessScore(user, today);
        } catch (InsufficientTrainingDataException e) {
            tsb = 0.0;
            readiness = 50;
        }

        double injuryIndex = safeDouble(() -> injuryAwareTrainingService.getInjuryIndex(email), 0.0);

        List<Injuries> all = safeList(() -> injuryAwareTrainingService.findInjuriesByEmail(email));
        List<Injuries> active = all.stream()
                .filter(i -> i.getLastInjuryDate() == null || i.getLastInjuryDate().isAfter(today.minusDays(14)))
                .toList();

        List<ViewInjuryDto> dto = new ArrayList<>();
        for (Injuries i : active) {
            dto.add(injuryMapper.entitytoDto(i));
        }

        return new AthleteStatusDto(tsb, readiness, injuryIndex, dto);
    }

    private double safeDouble(SupplierWithException<Double> s, double fallback) {
        try {
            return s.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    private <T> List<T> safeList(SupplierWithException<List<T>> s) {
        try {
            return s.get();
        } catch (Exception e) {
            return List.of();
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }
}

