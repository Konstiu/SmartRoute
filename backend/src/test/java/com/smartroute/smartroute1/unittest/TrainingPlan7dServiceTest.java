package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.DailySummary;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.LoadDistributionDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlan7dDto;
import com.smartroute.smartroute1.endpoint.dto.RouteDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.PlannedDayDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.*;
import com.smartroute.smartroute1.service.impl.TrainingPlan7dServiceImpl;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.util.ForecastState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
                trainingPlanStore
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

        // Forecaster: always return a stable distribution
        when(loadForecaster.forecastLoad(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> new LoadDistributionDto(10, 20, 30, 20, 5));

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
    void includesGymOrMobilityWhenTemplatesContainThem() {
        ApplicationUser user = new ApplicationUser();
        user.setId(2L);
        user.setEmail("y@test.com");
        user.setExperienceLevel(ExperienceLevel.INTERMEDIATE);

        when(userRepository.findUserByEmail("y@test.com")).thenReturn(user);
        when(dailyAggregationService.getDailySummaries(eq(user), eq(60))).thenReturn(fakeHistory(fixedClock, 60, 35));
        when(fatigueAndOverloadService.currentCtl(user)).thenReturn(40.0);
        when(fatigueAndOverloadService.currentAtl(user)).thenReturn(35.0);
        when(daySelectorService.isTrainingDay(any(), eq(user)))
                .thenAnswer(inv -> {
                    LocalDate d = inv.getArgument(0);
                    // e.g. train Mon/Wed/Fri/Sat
                    return switch (d.getDayOfWeek()) {
                        case MONDAY, WEDNESDAY, FRIDAY, SATURDAY -> true;
                        default -> false;
                    };
                });

        when(injuryAwareTrainingService.getInjuryIndex("y@test.com")).thenReturn(0.0);
        when(injuryAwareTrainingService.findInjuriesByEmail("y@test.com")).thenReturn(List.of());
        when(readinessScoreService.calculateReadinessScore(eq(user), any())).thenReturn(80); // no readiness reductions

        when(routeGenerationService.generateRouteDetails(
                any(ApplicationUser.class),
                any(WorkoutType.class),
                anyDouble()
        )).thenAnswer(inv -> {
            WorkoutType wt = inv.getArgument(1);
            double dist = switch (wt) {
                case EASY_RUN -> 8000;
                case TEMPO_RUN -> 10000;
                case INTERVAL_RUN -> 9000;
                case LONG_RUN -> 16000;
                default -> 0;
            };
            return new RouteDto(dist, 5.0, 100.0);
        });

        when(loadForecaster.forecastLoad(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    WorkoutType wt = inv.getArgument(2);
                    // Make gym/mobility non-zero so they appear meaningful
                    double mean = (wt == WorkoutType.MOBILITY) ? 8 :
                            (wt == WorkoutType.GYM_PREHAB) ? 18 :
                                    (wt == WorkoutType.REST_DAY) ? 0 : 40;
                    double std = Math.max(2, mean * 0.2);
                    return new LoadDistributionDto(Math.max(0, mean - 10), mean, mean + 10, mean, std);
                });

        TrainingPlan7dService service = createService();
        TrainingPlan7dDto dto = service.buildNext7Days("y@test.com", latitude, longitude);

        boolean hasGym = dto.getDays().stream().anyMatch(d -> d.getWorkoutType() == WorkoutType.GYM_PREHAB);
        boolean hasMob = dto.getDays().stream().anyMatch(d -> d.getWorkoutType() == WorkoutType.MOBILITY);

        assertTrue( hasGym || hasMob, "Expected at least one gym or mobility day in 7-day plan");
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

        when(loadForecaster.forecastLoad(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    WorkoutType wt = inv.getArgument(2);
                    ForecastState st = inv.getArgument(3);
                    // Under fatigue, make hard sessions huge load to trigger penalties in optimizer
                    double mean;
                    if (wt == WorkoutType.INTERVAL_RUN || wt == WorkoutType.TEMPO_RUN || wt == WorkoutType.LONG_RUN) {
                        mean = 140;
                    } else if (wt == WorkoutType.EASY_RUN) {
                        mean = 60;
                    } else if (wt == WorkoutType.GYM_PREHAB) {
                        mean = 25;
                    } else if (wt == WorkoutType.MOBILITY) {
                        mean = 10;
                    } else {
                        mean = 0;
                    }
                    return new LoadDistributionDto(Math.max(0, mean - 20), mean, mean + 20, mean, 10);
                });

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
