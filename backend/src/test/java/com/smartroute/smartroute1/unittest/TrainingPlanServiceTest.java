package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.endpoint.dto.GymWorkoutDto;
import com.smartroute.smartroute1.endpoint.dto.RecommendedActivityDto;
import com.smartroute.smartroute1.endpoint.dto.WeatherSummaryDto;
import com.smartroute.smartroute1.endpoint.mapper.InjuryMapper;
import com.smartroute.smartroute1.entity.ApplicationUser;
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
import com.smartroute.smartroute1.service.WeatherService;
import com.smartroute.smartroute1.service.WorkoutTypeSelectorService;
import com.smartroute.smartroute1.service.impl.TrainingPlanServiceImpl;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles({"test", "generateData"})
@Transactional
class TrainingPlanServiceTest {
    @InjectMocks
    private TrainingPlanServiceImpl service;
    @Mock
    private WorkoutTypeSelectorService typeSelectorService;
    @Mock
    private WeatherService weatherService;
    @Mock
    private FatigueAndOverloadService fatigueAndOverloadService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReadinessScoreService readinessScoreService;
    @Mock
    private InjuryAwareTrainingService injuryAwareTrainingService;
    @Mock
    private InjuryMapper injuryMapper;
    @Mock
    private GymWorkoutSelectorService gymWorkoutSelectorService;
    @Mock
    private RouteGenerationService routeGenerationService;

    private ApplicationUser user;
    private WeatherResponse weather;

    @BeforeEach
    void setup() throws ValidationException {
        user = new ApplicationUser();
        user.setBirthdate(LocalDate.now().minusYears(30));

        weather = new WeatherResponse();
        weather.setTemperature2m(10d);
        weather.setWindSpeed10m(5d);
        weather.setPrecipitation(0d);
        weather.setRelativeHumidity(50d);

        when(userRepository.findUserByEmail(anyString())).thenReturn(user);
        when(weatherService.getWeatherAtTime(anyDouble(), anyDouble(), anyString()))
            .thenReturn(weather);
        when(weatherService.calculateWeatherScore(any())).thenReturn(0.8);
        when(weatherService.estimatePerformancePenalty(any())).thenReturn(0.1);
        when(weatherService.evaluateWeatherScore(anyDouble())).thenReturn("GOOD");
        when(weatherService.buildWeatherDescription(any())).thenReturn(new WeatherSummaryDto("temp", "wind", "precipitation", "uvIndex"));

        when(fatigueAndOverloadService.tsbOn(any(), any()))
            .thenReturn(0.0);
        when(readinessScoreService.calculateReadinessScore(any(), any()))
            .thenReturn(60);

        when(injuryAwareTrainingService.getInjuryIndex(anyString()))
            .thenReturn(0.0);
        when(injuryAwareTrainingService.findInjuriesByEmail(anyString()))
            .thenReturn(List.of());

        when(typeSelectorService.selectWorkoutType(anyString(), anyDouble(), anyDouble(), anyBoolean()))
            .thenReturn(WorkoutType.EASY_RUN);
    }

    @Test
    void getTrainingPlan_userNotFound_throwsNotFound() {
        when(userRepository.findUserByEmail(anyString())).thenReturn(null);

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> service.getTrainingPlan("mail@test.com", 1, 1)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getTrainingPlan_weatherNull_throwsWeatherException() throws ValidationException {
        when(weatherService.getWeatherAtTime(anyDouble(), anyDouble(), anyString()))
            .thenReturn(null);

        assertThrows(
            WeatherException.class,
            () -> service.getTrainingPlan("mail@test.com", 1, 1)
        );
    }

    @Test
    void getTrainingPlan_insufficientTrainingData_usesDefaults() {
        when(fatigueAndOverloadService.tsbOn(any(), any()))
            .thenThrow(new InsufficientTrainingDataException("no data"));

        RecommendedActivityDto dto =
            service.getTrainingPlan("mail@test.com", 1, 1);

        assertEquals(50, dto.getAthleteStatus().getReadinessScore());
        assertEquals(0.0, dto.getAthleteStatus().getTsb());
    }

    @Test
    void getTrainingPlan_lowTsb_forcesGymPrehab() throws ValidationException {
        when(fatigueAndOverloadService.tsbOn(any(), any()))
            .thenReturn(-20.0);
        when(typeSelectorService.selectWorkoutType(any(), anyDouble(), anyDouble(), anyBoolean()))
            .thenReturn(WorkoutType.EASY_RUN);

        when(gymWorkoutSelectorService.getGymWorkout(any(), any(), anyInt()))
            .thenReturn(new GymWorkoutDto());

        RecommendedActivityDto dto =
            service.getTrainingPlan("mail@test.com", 1, 1);

        assertEquals(RecommendedActivityDto.SessionType.GYM, dto.getType());
    }

    @Test
    void getTrainingPlan_runWorkout_generatesRoute() throws ValidationException {
        when(typeSelectorService.selectWorkoutType(any(), anyDouble(), anyDouble(), anyBoolean()))
            .thenReturn(WorkoutType.TEMPO_RUN);

        service.getTrainingPlan("mail@test.com", 1, 1);

        verify(routeGenerationService)
            .generateRouteDetails(eq(user), eq(WorkoutType.TEMPO_RUN), anyDouble());
    }

    @Test
    void getTrainingPlan_validationException_translatedToBadRequest() throws ValidationException {
        when(typeSelectorService.selectWorkoutType(anyString(), anyDouble(), anyDouble(), anyBoolean()))
            .thenThrow(new ValidationException("bad input"));

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> service.getTrainingPlan("mail@test.com", 1, 1)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }
}
