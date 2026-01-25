package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.DailySummary;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.service.impl.DailyAggregationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DailyAggregationServiceTest {

    @Mock ActivityRepository activityRepository;

    private Clock fixedClock;
    private ZoneId zone;

    @BeforeEach
    void setup() {
        zone = ZoneId.of("Europe/Vienna");
        fixedClock = Clock.fixed(
                ZonedDateTime.of(2026, 1, 15, 9, 0, 0, 0, zone).toInstant(),
                zone
        );
    }

    private DailyAggregationServiceImpl svc() {
        return new DailyAggregationServiceImpl(activityRepository, fixedClock);
    }

    private static ApplicationUser user(long id) {
        ApplicationUser u = new ApplicationUser();
        u.setId(id);
        return u;
    }

    private Instant atLocal(LocalDate date, int hour) {
        return date.atTime(hour, 0).atZone(zone).toInstant();
    }

    private Activity act(
            Instant start,
            Float distance,
            Integer movingTime,
            Float elev,
            Integer sessionLoad,
            Integer sufferScore,
            Double garminLoad,
            Float avgHr,
            Float maxHr
    ) {
        Activity a = mock(Activity.class);
        when(a.getStartDateLocal()).thenReturn(start);
        when(a.getDistance()).thenReturn(distance);
        when(a.getMovingTime()).thenReturn(movingTime);
        when(a.getTotalElevationGain()).thenReturn(elev);
        when(a.getSessionLoad()).thenReturn(sessionLoad);
        when(a.getSufferScore()).thenReturn(sufferScore);
        when(a.getGarminActivityTrainingsLoad()).thenReturn(garminLoad);
        when(a.getAverageHeartrate()).thenReturn(avgHr);
        when(a.getMaxHeartrate()).thenReturn(maxHr);
        return a;
    }

    @Test
    void fillsMissingDaysWithZerosAndReturnsDenseWindow() {
        DailyAggregationServiceImpl svc = svc();

        LocalDate today = LocalDate.now(fixedClock); // 2026-01-15
        LocalDate start = today.minusDays(2);
        LocalDate mid = today.minusDays(1);

        Activity a = act(
                atLocal(mid, 10),
                5000f, 1800, 50f,
                40, null, null,
                null, null
        );

        when(activityRepository.findRecentActivitiesForUser(eq(1L), any(Instant.class)))
                .thenReturn(List.of(a));

        List<DailySummary> res = svc.getDailySummaries(user(1L), 3);

        assertAll(
                () -> assertEquals(3, res.size()),
                () -> assertEquals(start, res.get(0).getDate()),
                () -> assertEquals(mid, res.get(1).getDate()),
                () -> assertEquals(today, res.get(2).getDate()),

                () -> assertAll("middle day",
                        () -> assertEquals(40, res.get(1).getTotalLoad()),
                        () -> assertEquals(5000f, res.get(1).getTotalDistanceMeters()),
                        () -> assertEquals(1800, res.get(1).getTotalMovingTimeSeconds()),
                        () -> assertEquals(50f, res.get(1).getTotalElevationGainMeters()),
                        () -> assertFalse(res.get(1).isHasHeartRate())
                ),

                () -> assertAll("zero filled edges",
                        () -> assertEquals(0, res.get(0).getTotalLoad()),
                        () -> assertEquals(0, res.get(2).getTotalLoad())
                )
        );
    }

    @Test
    void aggregatesMultipleActivitiesSameDayAndSumsFields() {
        DailyAggregationServiceImpl svc = svc();

        LocalDate day = LocalDate.now(fixedClock).minusDays(1);

        Activity a1 = act(
                atLocal(day, 9),
                3000f, 1200, 20f,
                50, 999, 999.0,   // sessionLoad used => 50
                140.0f, null
        );

        Activity a2 = act(
                atLocal(day, 18),
                2000f, 800, 30f,
                null, 60, 100.0,  // sessionLoad null => sufferScore used => 60
                null, 180.0f
        );

        when(activityRepository.findRecentActivitiesForUser(eq(1L), any(Instant.class)))
                .thenReturn(List.of(a1, a2));

        List<DailySummary> res = svc.getDailySummaries(user(1L), 2);

        DailySummary d0 = res.get(0);

        assertAll(
                () -> assertEquals(day, d0.getDate()),
                () -> assertAll("sums",
                        () -> assertEquals(110, d0.getTotalLoad()),
                        () -> assertEquals(5000f, d0.getTotalDistanceMeters()),
                        () -> assertEquals(2000, d0.getTotalMovingTimeSeconds()),
                        () -> assertEquals(50f, d0.getTotalElevationGainMeters()),
                        () -> assertTrue(d0.isHasHeartRate())
                )
        );
    }

    @Test
    void loadPrecedenceDoesNotFallBackIfSessionLoadPresentEvenIfZero() {
        DailyAggregationServiceImpl svc = svc();

        LocalDate day = LocalDate.now(fixedClock);

        // sessionLoad = 0 -> your implementation will return 0 and NOT use sufferScore
        Activity a1 = act(
                atLocal(day, 8),
                1000f, 400, 5f,
                0, 33, 77.0,
                null, null
        );

        // sessionLoad null, sufferScore=0 -> returns 0, does NOT use garmin because sufferScore is present (0)
        Activity a2 = act(
                atLocal(day, 12),
                1000f, 400, 5f,
                null, 0, 41.2,
                null, null
        );

        when(activityRepository.findRecentActivitiesForUser(eq(1L), any(Instant.class)))
                .thenReturn(List.of(a1, a2));

        List<DailySummary> res = svc.getDailySummaries(user(1L), 1);

        assertAll(
                () -> assertEquals(day, res.get(0).getDate()),
                () -> assertEquals(0, res.get(0).getTotalLoad())
        );
    }
}
