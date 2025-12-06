package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.AthleteStatusDto;
import com.smartroute.smartroute1.endpoint.dto.CompactWeatherDto;
import com.smartroute.smartroute1.endpoint.dto.GymWorkoutDto;
import com.smartroute.smartroute1.endpoint.dto.RecommendedActivityDto;
import com.smartroute.smartroute1.endpoint.dto.RouteDto;
import com.smartroute.smartroute1.endpoint.dto.ViewInjuryDto;
import com.smartroute.smartroute1.endpoint.dto.WeatherImpactDto;
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
import com.smartroute.smartroute1.service.TrainingPlanService;
import com.smartroute.smartroute1.service.WeatherService;
import com.smartroute.smartroute1.service.WorkoutTypeSelectorService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@AllArgsConstructor
public class TrainingPlanServiceImpl implements TrainingPlanService {

    private WorkoutTypeSelectorService typeSelectorService;
    private WeatherService weatherService;
    private FatigueAndOverloadService fatigueAndOverloadService;
    private UserRepository userRepository;
    private ReadinessScoreService readinessScoreService;
    private InjuryAwareTrainingService injuryAwareTrainingService;
    private InjuryMapper injuryMapper;
    private GymWorkoutSelectorService gymWorkoutSelectorService;

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
            WeatherImpactDto weatherImpactDto = weatherService.calculateWeatherScore(weatherResponse, age);

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

            // TODO implement more detailed weather info
            // set weather info
            dto.setWeather(new CompactWeatherDto(
                    weatherImpactDto.getWeatherScore(),
                    weatherResponse.getTemperature2m(),
                    weatherResponse.getWindSpeed10m(),
                    "N",
                    weatherResponse.getPrecipitation(),
                    weatherResponse.getRelativeHumidity(),
                    null
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

                //TODO implement route generation and set route dto
                dto.setRoute(new RouteDto(5000.0, 2.5, 30.0));

            } else if (gymWorkouts.contains(selectedWorkout)) {
                dto.setType(RecommendedActivityDto.SessionType.GYM);
                // TODO take name from selected workout when different gym workouts have been implemented
                dto.setName("Gym Workout");

                // TODO generate only one new workout per day
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
}
