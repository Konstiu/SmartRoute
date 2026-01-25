package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.*;
import com.smartroute.smartroute1.endpoint.dto.RouteDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.*;
import com.smartroute.smartroute1.service.impl.TrainingPlan7dServiceImpl;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.util.ForecastState;
import com.smartroute.smartroute1.util.LoadConstraints;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Transactional
@ActiveProfiles({"test", "generateData"})
public class TrainingPlan7dServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    DailyAggregationService dailyAggregationService;
    @Mock
    LoadForecaster loadForecaster;
    @Mock
    FatigueAndOverloadService fatigueAndOverloadService;
    @Mock
    InjuryAwareTrainingService injuryAwareTrainingService;
    @Mock
    ReadinessScoreService readinessScoreService;
    @Mock
    WeatherService weatherService;
    @Mock
    DaySelectorService daySelectorService;
    @Mock
    GymWorkoutSelectorService gymWorkoutSelectorService;
    @Mock
    RouteGenerationService routeGenerationService;
    @Mock
    TrainingPlanStore trainingPlanStore;
    @Mock
    UserModelStore userModelStore;

    private Clock fixedClock;
    private double latitude;
    private double longitude;

    @BeforeEach
    void setup() {
        // Jan 15 2026 in Vienna
        ZoneId zone = ZoneId.of("Europe/Vienna");
        fixedClock = Clock.fixed(
                ZonedDateTime.of(2026, 1, 15, 9, 0, 0, 0, zone).toInstant(),
                zone
        );

        latitude = 48.210033;
        longitude = 16.363449;

        lenient().when(loadForecaster.forecastLoad(
                any(ApplicationUser.class),
                any(LocalDate.class),
                any(WorkoutType.class),
                any(ForecastState.class),
                anyList(),
                any(LoadConstraints.class)
        )).thenAnswer(inv -> {
            WorkoutType wt = inv.getArgument(2);

            if (wt == WorkoutType.REST_DAY) {
                return new LoadDistributionDto(0, 0, 0, 0, 0);
            }

            double mean = switch (wt) {
                case MOBILITY -> 8.0;
                case GYM_PREHAB -> 18.0;
                case EASY_RUN -> 40.0;
                case TEMPO_RUN -> 55.0;
                case INTERVAL_RUN -> 60.0;
                case LONG_RUN -> 75.0;
                default -> 30.0;
            };
            double std = Math.max(5.0, mean * 0.2);

            return new LoadDistributionDto(
                    Math.max(0.0, mean - 10.0),
                    mean,
                    mean + 10.0,
                    mean,
                    std
            );
        });
    }

    private TrainingPlan7dServiceImpl createService() {
        return new TrainingPlan7dServiceImpl(
                userRepository,
                dailyAggregationService,
                loadForecaster,
                fatigueAndOverloadService,
                fixedClock,
                injuryAwareTrainingService,
                readinessScoreService,
                weatherService,
                daySelectorService,
                gymWorkoutSelectorService,
                routeGenerationService,
                trainingPlanStore,
                userModelStore
        );
    }

    @Test
    void returns7ConsecutiveDays() {
        ApplicationUser user = new ApplicationUser();
        user.setId(1L);
        user.setEmail("x@test.com");
        user.setExperienceLevel(ExperienceLevel.CASUAL);

        when(userRepository.findUserByEmail("x@test.com")).thenReturn(user);

        // History: 60 days with some loads
        List<DailySummary> hist = fakeHistory(fixedClock, 60, 40);
        when(dailyAggregationService.getDailySummaries(eq(user), eq(60))).thenReturn(hist);

        // Current state
        when(fatigueAndOverloadService.currentCtl(user)).thenReturn(50.0);
        when(fatigueAndOverloadService.currentAtl(user)).thenReturn(55.0);

        TrainingPlan7dService service = createService();
        TrainingPlan7dDto dto = service.buildNext7Days("x@test.com", latitude, longitude);

        assertAll("7-day training plan",
                () -> assertNotNull(dto, "DTO must not be null"),
                () -> assertEquals(7, dto.getDays().size(), "Must return exactly 7 days"),

                // Dates must be consecutive
                () -> {
                    for (int i = 1; i < dto.getDays().size(); i++) {
                        LocalDate prev = dto.getDays().get(i - 1).getDate();
                        LocalDate cur  = dto.getDays().get(i).getDate();
                        assertEquals(prev.plusDays(1), cur,
                                "Days must be consecutive (" + prev + " -> " + cur + ")");
                    }
                },

                // Load distribution must be sane
                () -> dto.getDays().forEach(d -> assertAll(
                        "Load distribution for " + d.getDate(),
                        () -> assertTrue(
                                d.getLoad().getP10() <= d.getLoad().getP50(),
                                "p10 must be <= p50"
                        ),
                        () -> assertTrue(
                                d.getLoad().getP50() <= d.getLoad().getP90(),
                                "p50 must be <= p90"
                        )
                ))
        );
    }

    @Test
    void prefersLowIntensityPlanWhenFatigueHigh() {
        // This test is strongest if you allow injecting templates.
        // But we can still bias optimization by making intensity days heavily penalized via forecaster.

        ApplicationUser user = new ApplicationUser();
        user.setId(3L);
        user.setEmail("fatigue@test.com");
        user.setExperienceLevel(ExperienceLevel.ADVANCED);

        when(userRepository.findUserByEmail("fatigue@test.com")).thenReturn(user);
        when(dailyAggregationService.getDailySummaries(eq(user), eq(60))).thenReturn(fakeHistory(fixedClock, 60, 60));

        // High fatigue: ATL >> CTL (TSB negative)
        when(fatigueAndOverloadService.currentCtl(user)).thenReturn(60.0);
        when(fatigueAndOverloadService.currentAtl(user)).thenReturn(95.0);


        TrainingPlan7dService service = createService();
        TrainingPlan7dDto dto = service.buildNext7Days("fatigue@test.com", latitude, longitude);

        long hardDays = dto.getDays().stream().filter(d ->
                d.getWorkoutType() == WorkoutType.INTERVAL_RUN ||
                        d.getWorkoutType() == WorkoutType.TEMPO_RUN ||
                        d.getWorkoutType() == WorkoutType.LONG_RUN
        ).count();

        assertTrue(hardDays <= 2, "Under high fatigue, plan should avoid many hard days");
    }

    @Test
    void returnsCachedPlanWithoutRecomputing() {
        ApplicationUser user = new ApplicationUser();
        user.setId(5L);
        user.setEmail("cache@test.com");

        when(userRepository.findUserByEmail("cache@test.com")).thenReturn(user);

        String expectedPlanId = "week:2026-01-12";
        TrainingPlan7dDto cached = new TrainingPlan7dDto(List.of(
                new PlannedDayDto(
                        LocalDate.of(2026, 1, 15),
                        WorkoutType.REST_DAY,
                        new LoadDistributionDto(0, 0, 0, 0, 0),
                        new LoadDistributionDto(0, 0, 0, 0, 0),
                        null,
                        "high",
                        List.of(),
                        null,
                        null
                )
        ));
        cached.setPlanId(expectedPlanId);

        when(trainingPlanStore.get("cache@test.com", expectedPlanId)).thenReturn(Optional.of(cached));

        TrainingPlan7dService service = createService();
        TrainingPlan7dDto dto = service.buildNext7Days("cache@test.com", latitude, longitude);

        assertSame(cached, dto);
        verify(trainingPlanStore).get("cache@test.com", expectedPlanId);
        verify(trainingPlanStore, never()).put(anyString(), anyString(), any());
        verifyNoInteractions(loadForecaster, dailyAggregationService, fatigueAndOverloadService,
                readinessScoreService, weatherService, gymWorkoutSelectorService, routeGenerationService);
    }

    private static List<DailySummary> fakeHistory(Clock clock, int days, int load) {
        ZoneId zone = clock.getZone();
        LocalDate end = LocalDate.now(clock);
        LocalDate start = end.minusDays(days - 1L);

        return start.datesUntil(end.plusDays(1))
                .map(d -> new DailySummary(d, load, 0f, 0, 0f, false))
                .toList();
    }
}
