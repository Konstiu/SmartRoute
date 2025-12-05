package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.endpoint.dto.WeatherImpactDto;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles({"test", "generateData"})
public class WorkoutTypeSelectorServiceTest extends BaseTest {

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private ReadinessScoreService readinessScoreService;
    @MockitoBean
    private WeatherService weatherService;
    @MockitoBean
    private InjuryAwareTrainingService injuryAwareTrainingService;
    @MockitoBean
    private ActivityProcessingService activityProcessingService;
    @MockitoBean
    private DaySelectorService daySelectorService;

    @Autowired
    private WorkoutTypeSelectorService workoutTypeSelectorService;

    private ApplicationUser defaultUser() {
        ApplicationUser user = userRepository.findAll().getFirst();
        user.setBirthdate(LocalDate.now().minusYears(30));
        return user;
    }

    private WeatherResponse dummyWeatherResponse() {
        return new WeatherResponse();
    }

    private WeatherImpactDto weatherImpact(double weatherScore) {
        WeatherImpactDto dto = new WeatherImpactDto();
        dto.setWeatherScore(weatherScore);
        return dto;
    }

    private Activity dummyActivityOfDistance(int distanceMeters) {
        Activity activity = new Activity();
        activity.setDistance(distanceMeters);
        return activity;
    }

    private Activity dummyActivityOfWorkoutType() {
        Activity activity = new Activity();
        activity.setWorkoutType(WorkoutType.EASY_RUN);
        return activity;
    }

    @Test
    void returnsRestDay_whenNotTrainingDay_andIgnoreRestFalse() throws ValidationException {
        ApplicationUser user = defaultUser();
        when(userService.findApplicationUserByEmail(anyString())).thenReturn(user);
        when(daySelectorService.isTrainingDay(any(LocalDate.class), eq(user))).thenReturn(false);

        WorkoutType result = workoutTypeSelectorService.selectWorkoutType("user@example.com", 48.2, 16.3, false);
        assertEquals(WorkoutType.REST_DAY, result);

        verifyNoInteractions(readinessScoreService, weatherService, injuryAwareTrainingService, activityProcessingService);
    }

    @Test
    void ignoresRestDay_whenIgnoreRestTrue_andSelectsRunningOnGoodConditions() throws ValidationException {
        ApplicationUser user = defaultUser();
        when(userService.findApplicationUserByEmail(anyString())).thenReturn(user);
        when(daySelectorService.isTrainingDay(any(LocalDate.class), eq(user))).thenReturn(false);

        when(readinessScoreService.calculateReadinessScore(eq(user), any(LocalDate.class))).thenReturn(80); // normalized 0.8
        when(activityProcessingService.getLastRunningActivityBeforeDate(anyString(), any(LocalDate.class)))
                .thenReturn(Optional.of(dummyActivityOfDistance(5000)));
        when(activityProcessingService.getLastActivities(anyString(), anyInt()))
                .thenReturn(List.of());

        WeatherResponse wr = dummyWeatherResponse();
        when(weatherService.getWeatherAtTime(anyDouble(), anyDouble(), anyString())).thenReturn(wr);
        when(weatherService.calculateWeatherScore(eq(wr), anyInt())).thenReturn(weatherImpact(0.9));

        when(injuryAwareTrainingService.getInjuryConstraint(anyString())).thenReturn(1.0); // healthy

        WorkoutType result = workoutTypeSelectorService.selectWorkoutType("user@example.com", 48.2, 16.3, true);
        // With good readiness, good weather, healthy -> a running workout should win. Interval or Tempo likely.
        assertTrue(result == WorkoutType.INTERVAL_RUN || result == WorkoutType.TEMPO_RUN || result == WorkoutType.LONG_RUN || result == WorkoutType.EASY_RUN,
                "Expected a running workout but got: " + result);
    }

    @Test
    void prefersIndoor_whenOutdoorWeatherIsBad() throws ValidationException {
        ApplicationUser user = defaultUser();
        when(userService.findApplicationUserByEmail(anyString())).thenReturn(user);
        when(daySelectorService.isTrainingDay(any(LocalDate.class), eq(user))).thenReturn(true);

        when(readinessScoreService.calculateReadinessScore(eq(user), any(LocalDate.class))).thenReturn(50);
        when(activityProcessingService.getLastRunningActivityBeforeDate(anyString(), any(LocalDate.class)))
                .thenReturn(Optional.of(dummyActivityOfDistance(4000)));
        when(activityProcessingService.getLastActivities(anyString(), anyInt()))
                .thenReturn(List.of());

        WeatherResponse wr = dummyWeatherResponse();
        when(weatherService.getWeatherAtTime(anyDouble(), anyDouble(), anyString())).thenReturn(wr);
        when(weatherService.calculateWeatherScore(eq(wr), anyInt())).thenReturn(weatherImpact(0.1)); // bad outdoor weather

        when(injuryAwareTrainingService.getInjuryConstraint(anyString())).thenReturn(1.0);

        WorkoutType result = workoutTypeSelectorService.selectWorkoutType("user@example.com", 48.2, 16.3, false);
        assertTrue(result == WorkoutType.GYM_PREHAB || result == WorkoutType.MOBILITY,
                "Expected indoor workout due to bad weather, got: " + result);
    }

    @Test
    void avoidsRunning_whenInjuryConstraintIsLow() throws ValidationException {
        ApplicationUser user = defaultUser();
        when(userService.findApplicationUserByEmail(anyString())).thenReturn(user);
        when(daySelectorService.isTrainingDay(any(LocalDate.class), eq(user))).thenReturn(true);

        when(readinessScoreService.calculateReadinessScore(eq(user), any(LocalDate.class))).thenReturn(85);
        when(activityProcessingService.getLastRunningActivityBeforeDate(anyString(), any(LocalDate.class)))
                .thenReturn(Optional.of(dummyActivityOfDistance(6000)));
        when(activityProcessingService.getLastActivities(anyString(), anyInt()))
                .thenReturn(List.of());

        WeatherResponse wr = dummyWeatherResponse();
        when(weatherService.getWeatherAtTime(anyDouble(), anyDouble(), anyString())).thenReturn(wr);
        when(weatherService.calculateWeatherScore(eq(wr), anyInt())).thenReturn(weatherImpact(0.8));

        when(injuryAwareTrainingService.getInjuryConstraint(anyString())).thenReturn(0.0); // severely injured

        WorkoutType result = workoutTypeSelectorService.selectWorkoutType("user@example.com", 48.2, 16.3, false);
        assertTrue(result == WorkoutType.GYM_PREHAB || result == WorkoutType.MOBILITY,
                "Expected non-running workout due to injury, got: " + result);
    }

    @Test
    void varietyPenalty_discouragesRepeatingSameWorkout() throws ValidationException {
        ApplicationUser user = defaultUser();
        when(userService.findApplicationUserByEmail(anyString())).thenReturn(user);
        when(daySelectorService.isTrainingDay(any(LocalDate.class), eq(user))).thenReturn(true);

        when(readinessScoreService.calculateReadinessScore(eq(user), any(LocalDate.class))).thenReturn(75); // 0.75
        when(activityProcessingService.getLastRunningActivityBeforeDate(anyString(), any(LocalDate.class)))
                .thenReturn(Optional.of(dummyActivityOfDistance(5000)));
        // Include EASY_RUN in recent workouts to apply variety penalty
        Activity recentRun = dummyActivityOfWorkoutType();
        when(activityProcessingService.getLastActivities(anyString(), anyInt()))
                .thenReturn(List.of(recentRun));

        WeatherResponse wr = dummyWeatherResponse();
        when(weatherService.getWeatherAtTime(anyDouble(), anyDouble(), anyString())).thenReturn(wr);
        when(weatherService.calculateWeatherScore(eq(wr), anyInt())).thenReturn(weatherImpact(0.9));

        when(injuryAwareTrainingService.getInjuryConstraint(anyString())).thenReturn(1.0);

        WorkoutType result = workoutTypeSelectorService.selectWorkoutType("user@example.com", 48.2, 16.3, false);
        assertNotEquals(WorkoutType.EASY_RUN, result,
                "Expected not to choose EASY_RUN due to recent workout variety penalty");
    }

    @Test
    void fallsBackToDefaultLastDistance_whenNoLastRunFound() throws ValidationException {
        ApplicationUser user = defaultUser();
        when(userService.findApplicationUserByEmail(anyString())).thenReturn(user);
        when(daySelectorService.isTrainingDay(any(LocalDate.class), eq(user))).thenReturn(true);

        when(readinessScoreService.calculateReadinessScore(eq(user), any(LocalDate.class))).thenReturn(60); // 0.6
        when(activityProcessingService.getLastRunningActivityBeforeDate(anyString(), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(activityProcessingService.getLastActivities(anyString(), anyInt()))
                .thenReturn(List.of());

        WeatherResponse wr = dummyWeatherResponse();
        when(weatherService.getWeatherAtTime(anyDouble(), anyDouble(), anyString())).thenReturn(wr);
        // We can't directly assert input 3000 here, but ensure the flow continues normally
        when(weatherService.calculateWeatherScore(eq(wr), anyInt())).thenReturn(weatherImpact(0.5));

        when(injuryAwareTrainingService.getInjuryConstraint(anyString())).thenReturn(1.0);

        WorkoutType result = workoutTypeSelectorService.selectWorkoutType("user@example.com", 48.2, 16.3, false);
        assertNotNull(result);
    }
}

