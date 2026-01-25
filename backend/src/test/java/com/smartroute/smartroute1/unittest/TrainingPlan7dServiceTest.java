package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.endpoint.dto.AthleteStatusDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.*;
import com.smartroute.smartroute1.endpoint.dto.RouteDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Injuries;
import com.smartroute.smartroute1.entity.enums.BodyPart;
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

    @Test
    void weekShapeScore_penalizesLongRunWithoutRecoveryWithin48h() throws Exception {
        TrainingPlan7dServiceImpl service = createService();

        // long on day 3, but no REST_DAY / GYM_PREHAB within ±2 days
        List<WorkoutType> bad = List.of(
                WorkoutType.EASY_RUN,     // 0
                WorkoutType.EASY_RUN,     // 1
                WorkoutType.TEMPO_RUN,    // 2 (not recovery)
                WorkoutType.LONG_RUN,     // 3
                WorkoutType.EASY_RUN,     // 4 (not recovery)
                WorkoutType.EASY_RUN,     // 5
                WorkoutType.EASY_RUN      // 6
        );

        // Same, but add recovery day within 48h (day 4 = REST)
        List<WorkoutType> good = List.of(
                WorkoutType.EASY_RUN,
                WorkoutType.EASY_RUN,
                WorkoutType.TEMPO_RUN,
                WorkoutType.LONG_RUN,
                WorkoutType.REST_DAY,     // recovery within +1 day
                WorkoutType.EASY_RUN,
                WorkoutType.EASY_RUN
        );

        Object profile = profile(0.9, 0.3); // consistent, low risk aversion
        Object expCfg = expCfgForLevel(ExperienceLevel.INTERMEDIATE);

        double badScore = invokeWeekShapeScore(service, bad, profile, expCfg);
        double goodScore = invokeWeekShapeScore(service, good, profile, expCfg);

        assertAll("long run recovery window",
                () -> assertTrue(goodScore > badScore,
                        "Week with recovery within 48h of LONG_RUN should score higher"),
                () -> assertTrue((goodScore - badScore) >= 10.0,
                        "Expected a substantial gap between bad and good (>= 10) but was " + (goodScore - badScore))
        );
    }

    @Test
    void weekShapeScore_penalizesHardSessionsTooCloseWithin72h() throws Exception {
        TrainingPlan7dServiceImpl service = createService();

        // Hard days at 0 and 2 (distance 2) => stronger penalty
        List<WorkoutType> close = List.of(
                WorkoutType.TEMPO_RUN,    // 0 hard
                WorkoutType.EASY_RUN,     // 1
                WorkoutType.INTERVAL_RUN, // 2 hard (distance 2)
                WorkoutType.EASY_RUN,
                WorkoutType.EASY_RUN,
                WorkoutType.REST_DAY,
                WorkoutType.EASY_RUN
        );

        // Same counts, but hard days at 0 and 4 (distance 4) => no 72h penalty
        List<WorkoutType> spaced = List.of(
                WorkoutType.TEMPO_RUN,
                WorkoutType.EASY_RUN,
                WorkoutType.EASY_RUN,
                WorkoutType.EASY_RUN,
                WorkoutType.INTERVAL_RUN,
                WorkoutType.REST_DAY,
                WorkoutType.EASY_RUN
        );

        Object profile = profile(0.9, 0.3);
        Object expCfg = expCfgForLevel(ExperienceLevel.ADVANCED);

        double closeScore = invokeWeekShapeScore(service, close, profile, expCfg);
        double spacedScore = invokeWeekShapeScore(service, spaced, profile, expCfg);

        assertAll("hard spacing 72h",
                () -> assertTrue(spacedScore > closeScore,
                        "Hard days spaced >=4 days apart should score higher than hard days within 72h"),
                () -> assertTrue((spacedScore - closeScore) >= 5.0,
                        "Expected visible penalty (>= 5) but got " + (spacedScore - closeScore))
        );
    }

    @Test
    void weekShapeScore_prefersDistributedRecoveryForInconsistentOrRiskAverse() throws Exception {
        TrainingPlan7dServiceImpl service = createService();

        // Only late recovery (no recovery in days 0..3)
        List<WorkoutType> lateOnly = List.of(
                WorkoutType.EASY_RUN,
                WorkoutType.TEMPO_RUN,
                WorkoutType.EASY_RUN,
                WorkoutType.EASY_RUN,
                WorkoutType.REST_DAY,     // recovery late
                WorkoutType.EASY_RUN,
                WorkoutType.EASY_RUN
        );

        // Recovery both early and late
        List<WorkoutType> distributed = List.of(
                WorkoutType.REST_DAY,     // early recovery
                WorkoutType.TEMPO_RUN,
                WorkoutType.EASY_RUN,
                WorkoutType.EASY_RUN,
                WorkoutType.REST_DAY,     // late recovery
                WorkoutType.EASY_RUN,
                WorkoutType.EASY_RUN
        );

        // Trigger wantsDistributedRecovery: consistency < 0.60 OR riskAversion > 0.85
        Object inconsistentRisky = profile(0.50, 0.95);
        Object expCfg = expCfgForLevel(ExperienceLevel.INTERMEDIATE);

        double lateScore = invokeWeekShapeScore(service, lateOnly, inconsistentRisky, expCfg);
        double distScore = invokeWeekShapeScore(service, distributed, inconsistentRisky, expCfg);

        assertAll("distributed recovery preference",
                () -> assertTrue(distScore > lateScore,
                        "Inconsistent/risk-averse profile should prefer recovery in both halves"),
                () -> assertTrue((distScore - lateScore) >= 8.0,
                        "Expected notable penalty when missing early/late recovery (>= 8); got " + (distScore - lateScore))
        );
    }

    @Test
    void effectiveWorkoutType_nullWeatherMapsOutdoorRunToGym() throws Exception {
        TrainingPlan7dServiceImpl service = createService();

        var m = TrainingPlan7dServiceImpl.class.getDeclaredMethod(
                "effectiveWorkoutType",
                WorkoutType.class, double.class, int.class, Double.class,
                getInnerExperienceConfigClass()
        );
        m.setAccessible(true);

        Object expCfg = expCfgForLevel(ExperienceLevel.INTERMEDIATE);

        WorkoutType planned = WorkoutType.EASY_RUN;
        double injury = 0.0;
        int readiness = 80;
        Double weatherScore = null;

        WorkoutType effective = (WorkoutType) m.invoke(service, planned, injury, readiness, weatherScore, expCfg);

        assertAll("null weather mapping",
                () -> assertEquals(WorkoutType.GYM_PREHAB, effective,
                        "Null weatherScore should map outdoor run to GYM_PREHAB")
        );
    }

    @Test
    void regenTrue_removesCacheAndStoresFreshPlan() {
        ApplicationUser user = new ApplicationUser();
        user.setId(10L);
        user.setEmail("regen@test.com");
        user.setExperienceLevel(ExperienceLevel.CASUAL);

        when(userRepository.findUserByEmail("regen@test.com")).thenReturn(user);
        when(dailyAggregationService.getDailySummaries(eq(user), eq(60))).thenReturn(fakeHistory(fixedClock, 60, 35));
        when(fatigueAndOverloadService.currentCtl(user)).thenReturn(40.0);
        when(fatigueAndOverloadService.currentAtl(user)).thenReturn(38.0);

        // Day selector: make every day available so template gen isn't empty
        lenient().when(daySelectorService.isTrainingDay(any(LocalDate.class), eq(user))).thenReturn(true);

        TrainingPlan7dServiceImpl service = createService();

        // Call with regen=true (use the full signature)
        TrainingPlan7dDto dto = service.buildNext7Days(
                "regen@test.com",
                latitude,
                longitude,
                false,
                null,
                null,
                null,
                true
        );

        assertAll("regen behavior",
                () -> assertNotNull(dto),
                () -> assertNotNull(dto.getPlanId()),
                () -> assertTrue(dto.getPlanId().endsWith(":live"),
                        "regen should create planId with :live suffix but was " + dto.getPlanId()),
                () -> assertEquals(7, dto.getDays().size(), "regen plan should still have 7 days")
        );

        verify(trainingPlanStore).remove(eq("regen@test.com"), anyString());
        verify(trainingPlanStore).put(eq("regen@test.com"), anyString(), any(TrainingPlan7dDto.class));
    }

    @Test
    void cacheHit_withInjuryChange_patchesFromClampedCutoffAndStoresMerged() {
        TrainingPlan7dServiceImpl service = createService();

        ApplicationUser user = new ApplicationUser();
        user.setId(11L);
        user.setEmail("patch@test.com");
        user.setExperienceLevel(ExperienceLevel.CASUAL);

        when(userRepository.findUserByEmail("patch@test.com")).thenReturn(user);
        when(dailyAggregationService.getDailySummaries(eq(user), eq(60))).thenReturn(fakeHistory(fixedClock, 60, 35));
        when(fatigueAndOverloadService.currentCtl(user)).thenReturn(40.0);
        when(fatigueAndOverloadService.currentAtl(user)).thenReturn(38.0);

        lenient().when(daySelectorService.isTrainingDay(any(LocalDate.class), eq(user))).thenReturn(true);

        LocalDate today = LocalDate.now(fixedClock); // 2026-01-15
        LocalDate weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)); // 2026-01-12
        String planId = "week:" + weekStart;

        // Cached plan: 7 days, all EASY_RUN (marker = EASY_RUN)
        List<PlannedDayDto> cachedDays = java.util.stream.IntStream.range(0, 7)
                .mapToObj(i -> new PlannedDayDto(
                        today.plusDays(i),
                        WorkoutType.EASY_RUN,
                        new LoadDistributionDto(0, 0, 0, 0, 0),
                        new LoadDistributionDto(0, 0, 0, 0, 0),
                        null,
                        "high",
                        List.of(),
                        null,
                        null
                )).toList();

        TrainingPlan7dDto cached = new TrainingPlan7dDto(cachedDays);
        cached.setPlanId(planId);

        // Cached status: empty injuries signature (0)
        AthleteStatusDto cachedStatus = new AthleteStatusDto(null, 50, 0.0, List.of());
        cached.setCurrentAthleteStatus(cachedStatus);

        when(trainingPlanStore.get("patch@test.com", planId)).thenReturn(Optional.of(cached));

        // Now return an injury list which makes signature != 0, forcing patch
        Injuries injury = new Injuries();
        injury.setId(99L);
        injury.setAffectedArea(BodyPart.KNEE_REGION);
        injury.setLastHealthyDate(today.plusDays(2)); // cutoff should become today+2
        injury.setLastInjuryDate(today);              // earlier
        injury.setInjuryIndex(0.6);

        when(injuryAwareTrainingService.findInjuriesByEmail("patch@test.com")).thenReturn(List.of(injury));
        when(injuryAwareTrainingService.getInjuryIndex("patch@test.com")).thenReturn(0.6);
        when(readinessScoreService.calculateReadinessScore(eq(user), any(LocalDate.class))).thenReturn(60);

        // Needed by resolveOverrides
        when(injuryAwareTrainingService.calculateInjuriesMap(anyList())).thenReturn(java.util.Map.of());
        when(injuryAwareTrainingService.calculateIntensityScaling(anyDouble())).thenReturn(1.0);
        when(injuryAwareTrainingService.calculateVolumeScaling(anyDouble())).thenReturn(1.0);
        when(injuryAwareTrainingService.calculateHighImpactPenalty(anyDouble())).thenReturn(1.0);

        TrainingPlan7dDto dto = service.buildNext7Days(
                "patch@test.com",
                latitude,
                longitude,
                false,
                null,
                null,
                null,
                false
        );

        LocalDate cutoff = today.plusDays(2);

        assertAll("cache patching",
                () -> assertNotNull(dto),
                () -> assertEquals(7, dto.getDays().size()),
                () -> assertEquals(planId, dto.getPlanId(), "PlanId should remain base week planId"),
                () -> {
                    for (PlannedDayDto d : dto.getDays()) {
                        if (d.getDate().isBefore(cutoff)) {
                            assertEquals(WorkoutType.EASY_RUN, d.getWorkoutType(),
                                    "Prefix day " + d.getDate() + " should be kept from cache");
                        }
                    }
                }
        );

        verify(trainingPlanStore).put(eq("patch@test.com"), eq(planId), any(TrainingPlan7dDto.class));
    }

    @Test
    void beginnerVsAdvanced_producesDifferentIntensityStructure() {
        TrainingPlan7dServiceImpl service = createService();

        ApplicationUser beginner = new ApplicationUser();
        beginner.setId(100L);
        beginner.setEmail("beg@test.com");
        beginner.setExperienceLevel(ExperienceLevel.BEGINNER);

        ApplicationUser advanced = new ApplicationUser();
        advanced.setId(101L);
        advanced.setEmail("adv@test.com");
        advanced.setExperienceLevel(ExperienceLevel.ADVANCED);

        stubBaselineForUser(beginner, "beg@test.com", 75);
        stubBaselineForUser(advanced, "adv@test.com", 75);

        // Similar fatigue for both
        when(fatigueAndOverloadService.currentCtl(beginner)).thenReturn(45.0);
        when(fatigueAndOverloadService.currentAtl(beginner)).thenReturn(45.0);
        when(fatigueAndOverloadService.currentCtl(advanced)).thenReturn(45.0);
        when(fatigueAndOverloadService.currentAtl(advanced)).thenReturn(45.0);

        // Injury low for both
        when(injuryAwareTrainingService.getInjuryIndex("beg@test.com")).thenReturn(0.1);
        when(injuryAwareTrainingService.getInjuryIndex("adv@test.com")).thenReturn(0.1);

        long seed = 42L;
        int sims = 120;

        TrainingPlan7dDto begPlan = service.buildNext7Days(
                "beg@test.com", latitude, longitude,
                false, sims, seed, null, true
        );
        TrainingPlan7dDto advPlan = service.buildNext7Days(
                "adv@test.com", latitude, longitude,
                false, sims, seed, null, true
        );

        long begQuality = countQuality(begPlan);
        long advQuality = countQuality(advPlan);

        long begHard = countHard(begPlan);
        long advHard = countHard(advPlan);

        long begRest = countRest(begPlan);
        long advRest = countRest(advPlan);

        assertAll("beginner vs advanced",
                () -> assertNotNull(begPlan),
                () -> assertNotNull(advPlan),

                () -> assertEquals(0, begQuality, "Beginner should not get interval/tempo sessions"),

                () -> assertTrue(advHard >= begHard,
                        "Advanced should not have fewer hard days than beginner (adv=" + advHard + ", beg=" + begHard + ")"),
                () -> assertTrue(advQuality > 1,
                        "Advanced should usually include at least one quality session (tempo/interval). Got " + advQuality),

                () -> assertTrue(begRest >= advRest,
                        "Beginner should not have fewer rest days than advanced (beg=" + begRest + ", adv=" + advRest + ")")
        );
    }

    @Test
    void sameExperience_highFatigueYieldsLessHardThanLowFatigue() {
        TrainingPlan7dServiceImpl service = createService();

        ApplicationUser userLow = new ApplicationUser();
        userLow.setId(200L);
        userLow.setEmail("fatigueLow@test.com");
        userLow.setExperienceLevel(ExperienceLevel.ADVANCED);

        ApplicationUser userHigh = new ApplicationUser();
        userHigh.setId(201L);
        userHigh.setEmail("fatigueHigh@test.com");
        userHigh.setExperienceLevel(ExperienceLevel.ADVANCED);

        stubBaselineForUser(userLow, "fatigueLow@test.com", 75);
        stubBaselineForUser(userHigh, "fatigueHigh@test.com", 75);

        when(injuryAwareTrainingService.getInjuryIndex("fatigueLow@test.com")).thenReturn(0.1);
        when(injuryAwareTrainingService.getInjuryIndex("fatigueHigh@test.com")).thenReturn(0.1);

        when(fatigueAndOverloadService.currentCtl(userLow)).thenReturn(60.0);
        when(fatigueAndOverloadService.currentAtl(userLow)).thenReturn(62.0);
        
        when(fatigueAndOverloadService.currentCtl(userHigh)).thenReturn(60.0);
        when(fatigueAndOverloadService.currentAtl(userHigh)).thenReturn(95.0);

        long seed = 123L;
        int sims = 160;

        TrainingPlan7dDto lowFat = service.buildNext7Days(
                "fatigueLow@test.com", latitude, longitude,
                false, sims, seed, null, true
        );
        TrainingPlan7dDto highFat = service.buildNext7Days(
                "fatigueHigh@test.com", latitude, longitude,
                false, sims, seed, null, true
        );

        long lowHard = countHard(lowFat);
        long highHard = countHard(highFat);

        long lowRecovery = countRecovery(lowFat);
        long highRecovery = countRecovery(highFat);

        assertAll("fatigue affects plan shape",
                () -> assertNotNull(lowFat),
                () -> assertNotNull(highFat),

                () -> assertTrue(highHard < lowHard,
                        "High fatigue should not increase hard days (low=" + lowHard + ", high=" + highHard + ")"),

                () -> assertTrue(highRecovery > lowRecovery,
                        "High fatigue should not reduce recovery days (low=" + lowRecovery + ", high=" + highRecovery + ")")
        );
    }

    @Test
    void sameExperience_highInjuryEliminatesQualityAndReducesHard() {
        TrainingPlan7dServiceImpl service = createService();

        ApplicationUser lowInj = new ApplicationUser();
        lowInj.setId(300L);
        lowInj.setEmail("injLow@test.com");
        lowInj.setExperienceLevel(ExperienceLevel.INTERMEDIATE);

        ApplicationUser highInj = new ApplicationUser();
        highInj.setId(301L);
        highInj.setEmail("injHigh@test.com");
        highInj.setExperienceLevel(ExperienceLevel.INTERMEDIATE);

        stubBaselineForUser(lowInj, "injLow@test.com", 75);
        stubBaselineForUser(highInj, "injHigh@test.com", 75);

        // Similar fatigue for both
        when(fatigueAndOverloadService.currentCtl(lowInj)).thenReturn(50.0);
        when(fatigueAndOverloadService.currentAtl(lowInj)).thenReturn(52.0);
        when(fatigueAndOverloadService.currentCtl(highInj)).thenReturn(50.0);
        when(fatigueAndOverloadService.currentAtl(highInj)).thenReturn(52.0);

        // Injury differs
        when(injuryAwareTrainingService.getInjuryIndex("injLow@test.com")).thenReturn(0.1);
        when(injuryAwareTrainingService.getInjuryIndex("injHigh@test.com")).thenReturn(0.85);

        long seed = 77L;
        int sims = 140;

        TrainingPlan7dDto planLow = service.buildNext7Days(
                "injLow@test.com", latitude, longitude,
                false, sims, seed, null, true
        );
        TrainingPlan7dDto planHigh = service.buildNext7Days(
                "injHigh@test.com", latitude, longitude,
                false, sims, seed, null, true
        );

        long lowQuality = countQuality(planLow);
        long highQuality = countQuality(planHigh);

        long lowHard = countHard(planLow);
        long highHard = countHard(planHigh);

        long highGym = planHigh.getDays().stream()
                .map(PlannedDayDto::getWorkoutType)
                .filter(w -> w == WorkoutType.GYM_PREHAB)
                .count();

        assertAll("injury affects plan",
                () -> assertNotNull(planLow),
                () -> assertNotNull(planHigh),

                // mapWorkoutForInjury: >=0.7 converts interval/tempo -> gym, long -> easy
                () -> assertEquals(0, highQuality, "High injury should eliminate quality runs (tempo/interval)"),
                () -> assertTrue(highHard < lowHard,
                        "High injury should not increase hard sessions (lowHard=" + lowHard + ", highHard=" + highHard + ")"),
                () -> assertTrue(highGym > 1,
                        "High injury should usually introduce gym/prehab days. Got " + highGym)
        );
    }



    private static Class<?> getInnerExperienceConfigClass() {
        for (Class<?> c : TrainingPlan7dServiceImpl.class.getDeclaredClasses()) {
            if (c.getSimpleName().equals("ExperienceConfig")) return c;
        }
        throw new IllegalStateException("Could not find ExperienceConfig inner class");
    }

    private Object expCfgForLevel(ExperienceLevel level) throws Exception {
        TrainingPlan7dServiceImpl service = createService();
        var m = TrainingPlan7dServiceImpl.class.getDeclaredMethod("expCfg", ApplicationUser.class);
        m.setAccessible(true);

        ApplicationUser u = new ApplicationUser();
        u.setExperienceLevel(level);

        return m.invoke(service, u);
    }

    private static double invokeWeekShapeScore(
            TrainingPlan7dServiceImpl service,
            List<WorkoutType> week,
            Object profile,
            Object expCfg
    ) throws Exception {
        var m = TrainingPlan7dServiceImpl.class.getDeclaredMethod(
                "weekShapeScore",
                List.class,
                getInnerProfileClass(),
                getInnerExperienceConfigClass()
        );
        m.setAccessible(true);
        return (double) m.invoke(service, week, profile, expCfg);
    }

    private static Class<?> getInnerProfileClass() {
        for (Class<?> c : TrainingPlan7dServiceImpl.class.getDeclaredClasses()) {
            if (c.getSimpleName().equals("PlannerProfile")) return c;
        }
        throw new IllegalStateException("Could not find PlannerProfile inner class");
    }

    private static Object profile(double consistency, double riskAversion) throws Exception {
        // PlannerProfile(meanDailyLoad, stdDailyLoad, cv, consistency, riskAversion, targetWeeklyLoad, uncertaintyScale)
        Class<?> pc = getInnerProfileClass();
        var ctor = pc.getDeclaredConstructors()[0];
        ctor.setAccessible(true);

        // Provide plausible values; only consistency and riskAversion matter for these tests
        double meanDailyLoad = 40.0;
        double stdDailyLoad = 8.0;
        double cv = stdDailyLoad / meanDailyLoad;
        double targetWeeklyLoad = 280.0;
        double uncertaintyScale = 1.0;

        return ctor.newInstance(meanDailyLoad, stdDailyLoad, cv, consistency, riskAversion, targetWeeklyLoad, uncertaintyScale);
    }

    private void stubBaselineForUser(ApplicationUser user, String email, int readiness) {
        when(userRepository.findUserByEmail(email)).thenReturn(user);

        // Make every day available so templates actually generate
        lenient().when(daySelectorService.isTrainingDay(any(LocalDate.class), eq(user))).thenReturn(true);

        // History / profile
        when(dailyAggregationService.getDailySummaries(eq(user), eq(60)))
                .thenReturn(fakeHistory(fixedClock, 60, 40));

        // Injury-aware services: avoid NPEs in resolveOverrides()
        when(injuryAwareTrainingService.findInjuriesByEmail(email)).thenReturn(List.of());
        when(injuryAwareTrainingService.calculateInjuriesMap(anyList())).thenReturn(java.util.Map.of());
        lenient().when(injuryAwareTrainingService.calculateIntensityScaling(anyDouble())).thenReturn(1.0);
        lenient().when(injuryAwareTrainingService.calculateVolumeScaling(anyDouble())).thenReturn(1.0);
        lenient().when(injuryAwareTrainingService.calculateHighImpactPenalty(anyDouble())).thenReturn(1.0);

        // Readiness
        when(readinessScoreService.calculateReadinessScore(eq(user), any(LocalDate.class))).thenReturn(readiness);

        // Don’t let gym selection crash if called
        lenient().when(gymWorkoutSelectorService.getGymWorkout(any(), anyMap(), anyInt())).thenReturn(null);

        // Don’t let route generation crash if called
        lenient().when(routeGenerationService.generateRouteDetails(any(), any(), anyInt())).thenReturn(null);
    }

    private static long countHard(TrainingPlan7dDto dto) {
        return dto.getDays().stream()
                .map(PlannedDayDto::getWorkoutType)
                .filter(w -> w == WorkoutType.INTERVAL_RUN || w == WorkoutType.TEMPO_RUN || w == WorkoutType.LONG_RUN)
                .count();
    }

    private static long countQuality(TrainingPlan7dDto dto) {
        return dto.getDays().stream()
                .map(PlannedDayDto::getWorkoutType)
                .filter(w -> w == WorkoutType.INTERVAL_RUN || w == WorkoutType.TEMPO_RUN)
                .count();
    }

    private static long countRest(TrainingPlan7dDto dto) {
        return dto.getDays().stream()
                .map(PlannedDayDto::getWorkoutType)
                .filter(w -> w == WorkoutType.REST_DAY)
                .count();
    }

    private static long countRecovery(TrainingPlan7dDto dto) {
        return dto.getDays().stream()
                .map(PlannedDayDto::getWorkoutType)
                .filter(w -> w == WorkoutType.REST_DAY || w == WorkoutType.GYM_PREHAB)
                .count();
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
