package com.smartroute.smartroute1.unittest;


import com.smartroute.smartroute1.endpoint.dto.ConsistencyScoreResultDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.service.ConsistencyAnalyzerService;
import com.smartroute.smartroute1.service.impl.ConsistencyAnalyzerServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ActiveProfiles({"test", "generateData"})
public class ConsistencyAnalyzerTest {

    private final ApplicationUser user = new ApplicationUser();
    private ActivityRepository repository;
    private ConsistencyAnalyzerService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ActivityRepository.class);
        service = new ConsistencyAnalyzerServiceImpl(repository);
    }

    private Activity activity(String isoTime) {
        Activity act = new Activity();
        act.setStartDate(Instant.parse(isoTime));
        return act;
    }

    @Test
    void test_WhenPerfectFrequencyConsistency_ThenScoreIsPerfect() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-31T23:59:59Z");

        List<Activity> activities = List.of(
                activity("2025-01-24T12:00:00Z"),
                activity("2025-01-17T12:00:00Z"),
                activity("2025-01-10T12:00:00Z"),
                activity("2025-01-03T12:00:00Z")
        );

        when(repository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(user, start, end))
                .thenReturn(activities);

        ConsistencyScoreResultDto result = service.computeScore(user, start, end, 1);

        assertAll(
                () -> Assertions.assertEquals(1.0, result.getFrequencyConsistency()),
                () -> Assertions.assertEquals(1.0, result.getRegularityConsistency()),
                () -> Assertions.assertEquals(1.0, result.getFinalScore())
        );
    }

    @Test
    void test_WhenUnderTrainingPenalty_ThenReduceScoreAccordingly() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-31T23:59:59Z");

        List<Activity> activities = List.of(
                activity("2025-01-24T12:00:00Z")
        );

        when(repository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(user, start, end))
                .thenReturn(activities);

        ConsistencyScoreResultDto result = service.computeScore(user, start, end, 2);
        assertAll(
                () -> Assertions.assertTrue(result.getFrequencyConsistency() < 0.8),
                () -> Assertions.assertEquals(1.0, result.getRegularityConsistency()),
                () -> Assertions.assertTrue(result.getFinalScore() < 1.0)
        );
    }

    @Test
    void test_WhenNoActivities_ThenScoreIsZero() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-31T23:59:59Z");

        when(repository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(
                user, start, end)).thenReturn(List.of());

        ConsistencyScoreResultDto result = service.computeScore(user, start, end, 3);

        assertAll(
                () -> Assertions.assertEquals(0.0, result.getFinalScore()),
                () -> Assertions.assertEquals(0.0, result.getFrequencyConsistency()),
                () -> Assertions.assertEquals(0.0, result.getRegularityConsistency())
        );
    }

    @Test
    void test_WhenPerfectConsistencyWeeklyInOneSession_ThenScoreIsPerfect() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-31T23:59:59Z");

        List<Activity> activities = List.of(
                activity("2025-01-24T12:00:00Z"),
                activity("2025-01-17T12:00:00Z"),
                activity("2025-01-10T12:00:00Z"),
                activity("2025-01-03T12:00:00Z")
        );

        when(repository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(
                user, start, end)).thenReturn(activities);

        ConsistencyScoreResultDto result = service.computeScore(user, start, end, 1);

        assertAll(
                () -> Assertions.assertEquals(1.0, result.getFrequencyConsistency()),
                () -> Assertions.assertEquals(1.0, result.getRegularityConsistency()),
                () -> Assertions.assertEquals(1.0, result.getFinalScore())
        );
    }

    @Test
    void test_WhenUnderTrainingWithOneSession_ThenFrequencyConsistencyIsReduced() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-31T23:59:59Z");

        List<Activity> activities = List.of(
                activity("2025-01-20T12:00:00Z")
        );

        when(repository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(
                user, start, end)).thenReturn(activities);

        ConsistencyScoreResultDto result = service.computeScore(user, start, end, 5);
        assertAll(
                () -> Assertions.assertTrue(result.getFrequencyConsistency() < 0.5),
                () -> Assertions.assertEquals(1.0, result.getRegularityConsistency()),
                () -> Assertions.assertTrue(result.getFinalScore() < 1.0)
        );
    }

    @Test
    void testWhenOverTrainingThenScoreIsReduced() {
        Instant start = Instant.parse("2025-01-24T00:00:00Z");
        Instant end = Instant.parse("2025-01-31T23:59:59Z");

        List<Activity> activities = List.of(
                activity("2025-01-30T12:00:00Z"),
                activity("2025-01-29T12:00:00Z"),
                activity("2025-01-28T12:00:00Z"),
                activity("2025-01-27T12:00:00Z"),
                activity("2025-01-26T12:00:00Z"),
                activity("2025-01-25T12:00:00Z")
        );

        when(repository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(
                user, start, end)).thenReturn(activities);

        ConsistencyScoreResultDto result = service.computeScore(user, start, end, 2);
        assertAll(
                () -> Assertions.assertTrue(result.getFrequencyConsistency() < 0.5),
                () -> Assertions.assertEquals(1.0, result.getRegularityConsistency()),
                () -> Assertions.assertTrue(result.getFinalScore() < 0.8)
        );
    }

    @Test
    void test_WhenIrregularSpacingWithLargeGaps_ThenRegularityConsistencyIsReduced() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-31T23:59:59Z");

        // Very irregular spacing
        List<Activity> activities = List.of(
                activity("2025-01-30T12:00:00Z"),
                activity("2025-01-25T12:00:00Z"),
                activity("2025-01-10T12:00:00Z"),
                activity("2025-01-03T12:00:00Z")
        );

        when(repository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(
                user, start, end)).thenReturn(activities);

        ConsistencyScoreResultDto result = service.computeScore(user, start, end, 1);
        assertAll(
                () -> Assertions.assertEquals(1.0, result.getFrequencyConsistency()),
                () -> Assertions.assertTrue(result.getRegularityConsistency() < 0.5),
                () -> Assertions.assertTrue(result.getFinalScore() < 1.0)
        );
    }

    @Test
    void test_WhenTwoSessionsWithPerfectSpacing_ThenScoreIsPerfect() {
        Instant start = Instant.parse("2025-01-11T00:00:00Z");
        Instant end = Instant.parse("2025-01-25T23:59:59Z");

        List<Activity> activities = List.of(
                activity("2025-01-18T12:00:00Z"),
                activity("2025-01-11T12:00:00Z")
        );

        when(repository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(
                user, start, end)).thenReturn(activities);

        ConsistencyScoreResultDto result = service.computeScore(user, start, end, 1);
        assertAll(
                () -> Assertions.assertEquals(1.0, result.getFrequencyConsistency()),
                () -> Assertions.assertEquals(1.0, result.getRegularityConsistency()),
                () -> Assertions.assertEquals(1.0, result.getFinalScore())
        );
    }

    @Test
    void test_WhenThreeSessionsWithIrregularSpacing_ThenRegularityConsistencyIsReduced() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-31T23:59:59Z");

        List<Activity> activities = List.of(
                activity("2025-01-30T12:00:00Z"),
                activity("2025-01-25T12:00:00Z"),
                activity("2025-01-05T12:00:00Z")
        );

        when(repository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(
                user, start, end)).thenReturn(activities);

        ConsistencyScoreResultDto result = service.computeScore(user, start, end, 1);
        assertAll(
                () -> Assertions.assertTrue(result.getFrequencyConsistency() < 1.0),
                () -> Assertions.assertTrue(result.getRegularityConsistency() < 0.5),
                () -> Assertions.assertTrue(result.getFinalScore() < 1.0)
        );
    }

    @Test
    void test_WhenLargeTrainingGap_ThenScoreIsReduced() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-31T23:59:59Z");

        List<Activity> activities = List.of(
                activity("2025-01-28T12:00:00Z"),
                activity("2025-01-03T12:00:00Z")
        );

        when(repository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(
                user, start, end)).thenReturn(activities);

        ConsistencyScoreResultDto result = service.computeScore(user, start, end, 1);
        assertAll(
                () -> Assertions.assertTrue(result.getFrequencyConsistency() < 0.9),
                () -> Assertions.assertTrue(result.getRegularityConsistency() < 0.4),
                () -> Assertions.assertTrue(result.getFinalScore() < 1.0)
        );
    }

    @Test
    void test_WhenZeroPlannedSessions_ThenScoreIsReduced() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-31T23:59:59Z");
        List<Activity> activities = List.of(
                activity("2025-01-28T12:00:00Z"),
                activity("2025-01-03T12:00:00Z")
        );
        when(repository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(
                user, start, end)).thenReturn(activities);

        ConsistencyScoreResultDto result = service.computeScore(user, start, end, 0);

        assertAll(
                () -> assertEquals(0, result.getFinalScore()),
                () -> assertEquals(0, result.getRegularityConsistency()),
                () -> assertEquals(0, result.getFrequencyConsistency())
        );
    }

}
