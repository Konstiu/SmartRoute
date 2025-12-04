package com.smartroute.smartroute1.unittest;
import com.smartroute.smartroute1.endpoint.dto.ConsistencyScoreResultDto;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Injuries;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.Weekday;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.ConsistencyAnalyzerService;
import com.smartroute.smartroute1.service.DaySelectorService;
import com.smartroute.smartroute1.service.FatigueAndOverloadService;
import com.smartroute.smartroute1.service.InjuryAwareTrainingService;
import com.smartroute.smartroute1.service.ReadinessScoreService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles({"test"})
@Transactional
class DaySelectorServiceTest {
    @Autowired
    private DaySelectorService service;
    @Autowired
    private UserRepository userRepository;
    @MockitoBean
    ActivityRepository activityRepository;
    @MockitoBean
    ConsistencyAnalyzerService  consistencyAnalyzerService;
    @MockitoBean
    ReadinessScoreService readinessScoreService;
    @MockitoBean
    FatigueAndOverloadService fatigueAndOverloadService;
    @MockitoBean
    InjuryAwareTrainingService injuryAwareService;

    private final LocalDate date = LocalDate.now();
    private final ApplicationUser user = new ApplicationUser("User1", "email1@user1.com", "Max", "Muster");

    private Activity activity() {
        Activity activity = new Activity();
        activity.setName("Activity");
        return activity;
    }

    @BeforeEach
    void setUp() {
        userRepository.saveAndFlush(user);
    }

    @AfterEach
    void cleanup() {
        userRepository.deleteAll();
    }

    @Test
    void testIsTrainingDayWithPerfectScoresReturnsTrue() {

        user.setActiveWeekdays(Set.of(Weekday.values()));
        user.setExperienceLevel(ExperienceLevel.BEGINNER);

        when(activityRepository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(any(), any(), any()))
                .thenReturn(List.of(activity()));
        when(consistencyAnalyzerService.computeScore(any(), any(), any(), anyInt()))
                .thenReturn(new ConsistencyScoreResultDto(1.0, 1.0, 1.0));
        when(readinessScoreService.calculateReadinessScore(any(), any()))
                .thenReturn(100);
        when(fatigueAndOverloadService.tsbOn(any(), any()))
                .thenReturn(0.0);
        when(injuryAwareService.findInjuriesByEmail(any()))
                .thenReturn(List.of());

        boolean trainingDay = service.isTrainingDay(date, user);

        assertTrue(trainingDay);
    }

    @Test
    void testIsTrainingDayWithHighImpactInjuryIndexReturnsFalse() {

        user.setActiveWeekdays(Set.of(Weekday.values()));
        user.setExperienceLevel(ExperienceLevel.BEGINNER);

        Injuries injury = new Injuries();
        injury.setInjuryIndex(.7);
        injury.setLastHealthyDate(null);

        when(activityRepository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(any(), any(), any()))
                .thenReturn(List.of(activity()));
        when(consistencyAnalyzerService.computeScore(any(), any(), any(), anyInt()))
                .thenReturn(new ConsistencyScoreResultDto(1.0, 1.0, 1.0));
        when(readinessScoreService.calculateReadinessScore(any(), any()))
                .thenReturn(100);
        when(fatigueAndOverloadService.tsbOn(any(), any()))
                .thenReturn(0.0);
        when(injuryAwareService.findInjuriesByEmail(any()))
                .thenReturn(List.of(injury));

        boolean trainingDay = service.isTrainingDay(date, user);

        assertFalse(trainingDay);
    }

    @Test
    void testIsTrainingDayWithLowImpactInjuryIndexAndPerfectScoresReturnsTrue() {

        user.setActiveWeekdays(Set.of(Weekday.values()));
        user.setExperienceLevel(ExperienceLevel.BEGINNER);

        Injuries injury = new Injuries();
        injury.setInjuryIndex(.2);
        injury.setLastHealthyDate(null);

        when(activityRepository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(any(), any(), any()))
                .thenReturn(List.of(activity()));
        when(consistencyAnalyzerService.computeScore(any(), any(), any(), anyInt()))
                .thenReturn(new ConsistencyScoreResultDto(1.0, 1.0, 1.0));
        when(readinessScoreService.calculateReadinessScore(any(), any()))
                .thenReturn(100);
        when(fatigueAndOverloadService.tsbOn(any(), any()))
                .thenReturn(0.0);
        when(injuryAwareService.findInjuriesByEmail(any()))
                .thenReturn(List.of(injury));

        boolean trainingDay = service.isTrainingDay(date, user);

        assertTrue(trainingDay);
    }

    @Test
    void testIsTrainingDayWithHighOverloadReturnsFalse() {

        user.setActiveWeekdays(Set.of(Weekday.values()));
        user.setExperienceLevel(ExperienceLevel.BEGINNER);

        when(activityRepository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(any(), any(), any()))
                .thenReturn(List.of(activity()));
        when(consistencyAnalyzerService.computeScore(any(), any(), any(), anyInt()))
                .thenReturn(new ConsistencyScoreResultDto(1.0, 1.0, 1.0));
        when(readinessScoreService.calculateReadinessScore(any(), any()))
                .thenReturn(100);
        when(fatigueAndOverloadService.tsbOn(any(), any()))
                .thenReturn(-35.0);
        when(injuryAwareService.findInjuriesByEmail(any()))
                .thenReturn(List.of());

        boolean trainingDay = service.isTrainingDay(date, user);

        assertFalse(trainingDay);
    }

    @Test
    void testIsTrainingDayWithGoodConsistencyModerateOverloadReturnsTrue() {

        user.setActiveWeekdays(Set.of(Weekday.values()));
        user.setExperienceLevel(ExperienceLevel.BEGINNER);

        when(activityRepository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(any(), any(), any()))
                .thenReturn(List.of(activity()));
        when(consistencyAnalyzerService.computeScore(any(), any(), any(), anyInt()))
                .thenReturn(new ConsistencyScoreResultDto(1.0, 0.9, 0.95));
        when(readinessScoreService.calculateReadinessScore(any(), any()))
                .thenReturn(90);
        when(fatigueAndOverloadService.tsbOn(any(), any()))
                .thenReturn(-5.0);
        when(injuryAwareService.findInjuriesByEmail(any()))
                .thenReturn(List.of());

        boolean trainingDay = service.isTrainingDay(date, user);

        assertTrue(trainingDay);
    }

    @Test
    void testIsTrainingDayWithTooManySessionsInLastWeekReturnsFalse() {

        user.setActiveWeekdays(Set.of(Weekday.values()));
        user.setExperienceLevel(ExperienceLevel.BEGINNER); // max 3 sessions

        List<Activity> manySessions = List.of(
                activity(), activity(), activity(), activity(), activity()
        );

        when(activityRepository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(any(), any(), any()))
                .thenReturn(manySessions); // > max sessions = 3

        when(consistencyAnalyzerService.computeScore(any(), any(), any(), anyInt()))
                .thenReturn(new ConsistencyScoreResultDto(1.0, 1.0, 1.0));
        when(readinessScoreService.calculateReadinessScore(any(), any()))
                .thenReturn(100);
        when(fatigueAndOverloadService.tsbOn(any(), any()))
                .thenReturn(0.0);
        when(injuryAwareService.findInjuriesByEmail(any()))
                .thenReturn(List.of());

        boolean trainingDay = service.isTrainingDay(date, user);

        assertFalse(trainingDay);
    }

    @Test
    void testIsTrainingDayWithLowReadinessScoreReturnsFalse() {

        user.setActiveWeekdays(Set.of(Weekday.values()));
        user.setExperienceLevel(ExperienceLevel.BEGINNER);

        when(activityRepository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(any(), any(), any()))
                .thenReturn(List.of(activity()));
        when(consistencyAnalyzerService.computeScore(any(), any(), any(), anyInt()))
                .thenReturn(new ConsistencyScoreResultDto(1.0, 1.0, 1.0));
        when(readinessScoreService.calculateReadinessScore(any(), any()))
                .thenReturn(10);
        when(fatigueAndOverloadService.tsbOn(any(), any()))
                .thenReturn(0.0);
        when(injuryAwareService.findInjuriesByEmail(any()))
                .thenReturn(List.of());

        boolean trainingDay = service.isTrainingDay(date, user);

        assertFalse(trainingDay);
    }

    @Test
    void testIsTrainingDayWhenNoRecentActivitiesButAllScoresGoodReturnsTrue() {

        user.setActiveWeekdays(Set.of(Weekday.values()));
        user.setExperienceLevel(ExperienceLevel.BEGINNER);

        when(activityRepository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(consistencyAnalyzerService.computeScore(any(), any(), any(), anyInt()))
                .thenReturn(new ConsistencyScoreResultDto(1.0, 1.0, 1.0));
        when(readinessScoreService.calculateReadinessScore(any(), any()))
                .thenReturn(100);
        when(fatigueAndOverloadService.tsbOn(any(), any()))
                .thenReturn(0.0);
        when(injuryAwareService.findInjuriesByEmail(any()))
                .thenReturn(List.of());

        boolean trainingDay = service.isTrainingDay(date, user);

        assertTrue(trainingDay);
    }

    @Test
    void testDependenciesAreCalled() {

        user.setActiveWeekdays(Set.of(Weekday.values()));
        user.setExperienceLevel(ExperienceLevel.BEGINNER);

        when(activityRepository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(any(), any(), any()))
                .thenReturn(List.of(activity()));
        when(consistencyAnalyzerService.computeScore(any(), any(), any(), anyInt()))
                .thenReturn(new ConsistencyScoreResultDto(1.0, 1.0, 1.0));
        when(readinessScoreService.calculateReadinessScore(any(), any()))
                .thenReturn(100);
        when(fatigueAndOverloadService.tsbOn(any(), any()))
                .thenReturn(0.0);
        when(injuryAwareService.findInjuriesByEmail(any()))
                .thenReturn(List.of());

        service.isTrainingDay(date, user);

        verify(activityRepository).findAllByUserAndStartDateBetweenOrderByStartDateAsc(any(), any(), any());
        verify(consistencyAnalyzerService).computeScore(any(), any(), any(), anyInt());
        verify(readinessScoreService).calculateReadinessScore(any(), any());
        verify(fatigueAndOverloadService).tsbOn(any(), any());
        verify(injuryAwareService).findInjuriesByEmail(any());
    }

    @ParameterizedTest
    @CsvSource({
            // readiness, overload, injury, consistency, expected
            "100, 0.0, 0.0, 1.0, 1.0",          // ideal case
            "0,   0.0, 0.0, 1.0, 0.0",          // no readiness
            "50,  0.0, 0.0, 1.0, 0.75",         // reduced readiness full consistency
            "50,  0.0, 0.0, 0.5, 0.625",        // reduced readiness reduced consistency
            "50,  0.0, 0.0, 0.0, 0.5",          // reduced readiness no consistency
            "100, 1.0, 0.0, 1.0, 0.0",          // overload max
            "100, 0.0, 1.0, 1.0, 0.0",          // injured max
            "100, 0.0, 0.25, 1.0, 1",           // injured low impact full consistency
            "100, 0.0, 0.25, 0.5, 0.9375",      // injured low impact reduced consistency
            "100, 0.0, 0.25, 0.0, 0.75",        // injured low impact no consistency
            "100, 0.0, 0.75, 1.0, 0.375",       // injured high impact full consistency
            "100, 0.0, 0.75, 0.5, 0.3125",      // injured high impact reduced consistency
            "100, 0.0, 0.75, 0.0, 0.25",        // injured high impact no consistency
    })
    void testTrainabilityIndex(
            int readiness,
            double overload,
            double injury,
            double consistency,
            double expected
    ) {
        double result = service.calculateTrainabilityIndex(
                readiness, overload, injury, consistency
        );

        assertEquals(expected, result, 0.0001);
    }
}
