package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.entity.ActivityStream;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.enums.ActivityStreamSource;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.ActivityStreamRepository;
import com.smartroute.smartroute1.service.FitnessScoreService;
import com.smartroute.smartroute1.service.ActivityProcessingService;
import jakarta.transaction.Transactional;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles({"test", "generateData"})
@Transactional
class ActivityProcessingServiceTest extends BaseTest {

    @MockitoBean
    private FitnessScoreService fitnessScoreService;
    @MockitoBean
    private TaskScheduler taskScheduler;
    @Autowired
    private ActivityProcessingService activityProcessingService;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private ActivityStreamRepository activityStreamRepository;

    private Activity getStravaActivity() {
        Activity activity = new Activity();

        activity.setName("Strava Activity");
        activity.setDistance(2000);
        activity.setMovingTime(600);
        activity.setElapsedTime(610);
        activity.setTotalElevationGain(0);
        activity.setType("Run");
        activity.setSportType("Run");
        activity.setStartDate(Instant.now());
        activity.setStartDateLocal(Instant.now());
        activity.setAverageSpeed(10);
        activity.setMaxSpeed(20);
        activity.setAverageWatts(null);
        activity.setAverageHeartrate((float) 150);
        activity.setKilojoules(null);
        activity.setSufferScore(null);
        activity.setSummaryPolyline(null);
        activity.setUser(null);

        return activity;
    }

    @Test
    void testProcessActivitiesInBatches_setsSessionLoad() {
        activityRepository.deleteAll();
        activityStreamRepository.deleteAll();
        activityRepository.flush();
        activityStreamRepository.flush();

        ApplicationUser user = userRepository.findAll().getFirst();
        Activity act1 = getStravaActivity();
        act1.setUser(user);
        act1.setStravaId(1L);
        act1.setName("TestActivity1");
        Activity act2 = getStravaActivity();
        act2.setUser(user);
        act2.setName("TestActivity2");
        act2.setStravaId(2L);

        List<Activity> activities = List.of(
            act1,
            act2
        );

        activityRepository.saveAll(activities);

        when(fitnessScoreService.calculateSessionLoad(any(), any()))
            .thenReturn(123);

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        mockApiServer.enqueue(
            new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[{\"type\": \"heartrate\", \"data\": [150,151,152], \"original_size\": 3}]")
        );

        mockApiServer.enqueue(
            new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[{\"type\": \"heartrate\", \"data\": [130,131,132], \"original_size\": 3}]")
        );

        activityProcessingService.processActivitiesInBatches(1, activities, "token");

        List<Activity> activitiesWithSessionLoad = activityRepository.findByUser(user);

        assertAll(
            () -> assertEquals(2, activitiesWithSessionLoad.size()),
            () -> assertEquals(123, activitiesWithSessionLoad.get(0).getSessionLoad()),
            () -> assertEquals(123, activitiesWithSessionLoad.get(1).getSessionLoad())
        );
    }

    @Test
    void testProcessActivitiesInBatches_schedulesTasksCorrectly() {
        ApplicationUser user = userRepository.findAll().getFirst();
        Activity act1 = getStravaActivity();
        act1.setUser(user);
        Activity act2 = getStravaActivity();
        act2.setUser(user);

        List<Activity> activities = List.of(
            act1,
            act2
        );

        activityRepository.saveAll(activities);

        when(fitnessScoreService.calculateSessionLoad(any(), any()))
            .thenReturn(123);

        activityProcessingService.processActivitiesInBatches(1, activities, "token");


        verify(taskScheduler, times(2))
            .schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void testGetLastActivityBeforeDate_returnsLatestBefore() {
        activityRepository.deleteAll();
        activityRepository.flush();

        ApplicationUser user = userRepository.findAll().getFirst();

        Activity older = getStravaActivity();
        older.setUser(user);
        older.setStartDate(Instant.now().minusSeconds(5 * 24 * 3600));
        older.setName("older");

        Activity middle = getStravaActivity();
        middle.setUser(user);
        middle.setStartDate(Instant.now().minusSeconds(2 * 24 * 3600));
        middle.setName("middle");

        Activity future = getStravaActivity();
        future.setUser(user);
        future.setStartDate(Instant.now());
        future.setName("future");

        activityRepository.saveAll(List.of(older, middle, future));

        // Choose a date of yesterday -> activities before that are older and middle, latest should be middle
        LocalDate date = LocalDate.now().minusDays(1);

        Optional<Activity> result = activityProcessingService.getLastActivityBeforeDate(user.getEmail(), date);

        assertTrue(result.isPresent());
        assertEquals("middle", result.get().getName());
    }

    // detectHeartRateSpikes tests

    @Test
    void testDetectHeartRateSpikes_With1Spike_returnsCorrectly() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        List<Double> time = IntStream.rangeClosed(1, 60)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        List<Double> hr = List.of(
            // steady state
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,

            // sharp spike (interval)
            160.0, 165.0, 170.0, 168.0, 165.0,

            // recovery
            150.0, 148.0, 146.0, 145.0, 144.0,
            143.0, 142.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            141.0, 140.0, 141.0, 140.0, 141.0
        );

        ActivityStream as = activityProcessingService.createActivityStream(
            time,
            null,
            hr,
            ActivityStreamSource.STRAVA
        ); //TODO also test this method

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int hrSpikes = activityProcessingService.detectHeartRateSpikes(a);

        assertEquals(1, hrSpikes);
    }

    @Test
    void testDetectHeartRateSpikes_With1SpikeAndIrregularTimeStream_returnsCorrectly() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        List<Double> time = new ArrayList<>();
        double t = 0;
        Random random = new Random(42);

        for (int i = 0; i < 60; i++) {
            t += 0.8 + random.nextDouble() * 0.6; // Random interval between 0.8 and 1.4 seconds
            if (i == 15 || i == 35) {
                t += 2.0; // Occasional larger gap
            }
            time.add(t);
        }

        List<Double> hr = List.of(
            // steady state
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,

            // sharp spike (interval)
            160.0, 165.0, 170.0, 168.0, 165.0,

            // recovery
            150.0, 148.0, 146.0, 145.0, 144.0,
            143.0, 142.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            141.0, 140.0, 141.0, 140.0, 141.0
        );

        ActivityStream as = activityProcessingService.createActivityStream(
            time,
            null,
            hr,
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int hrSpikes = activityProcessingService.detectHeartRateSpikes(a);

        assertEquals(1, hrSpikes);
    }

    @Test
    void testDetectHeartRateSpikes_With0Spikes_returnsCorrectly() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        List<Double> time = IntStream.rangeClosed(1, 60)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        List<Double> hr = List.of(
            // steady state
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 142.0,
            144.0, 144.0, 145.0, 144.0, 146.0,
            150.0, 148.0, 146.0, 145.0, 144.0,
            143.0, 142.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            141.0, 140.0, 141.0, 140.0, 141.0
        );

        ActivityStream as = activityProcessingService.createActivityStream(
            time,
            null,
            hr,
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int hrSpikes = activityProcessingService.detectHeartRateSpikes(a);

        assertEquals(0, hrSpikes);
    }

    @Test
    void testDetectHeartRateSpikes_With0SpikesAndOutlier_returnsCorrectly() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        List<Double> time = IntStream.rangeClosed(1, 60)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        List<Double> hr = List.of(
            // steady state
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 142.0,
            144.0, 144.0, 145.0, 144.0, 146.0,
            150.0, 148.0, 146.0, 145.0, 144.0,
            143.0, 142.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0,

            //single outlier
            170.0,

            //steady state
            141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            141.0, 140.0, 141.0, 140.0, 141.0
        );

        ActivityStream as = activityProcessingService.createActivityStream(
            time,
            null,
            hr,
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int hrSpikes = activityProcessingService.detectHeartRateSpikes(a);

        assertEquals(0, hrSpikes);
    }

    @Test
    void testDetectHeartRateSpikes_With2Spikes_returnsCorrectly() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        List<Double> time = IntStream.rangeClosed(1, 60)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        List<Double> hr = List.of(
            // steady state
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,

            // sharp spike
            160.0, 165.0, 170.0, 168.0, 165.0,

            // recovery
            150.0, 148.0, 146.0, 145.0, 144.0,
            143.0, 142.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 142.0, 142.0, 143.0,
            142.0, 143.0, 145.0, 146.0, 149.0,

            // sharp spike
            157.0, 164.0, 165.0, 166.0, 165.0,

            //recovery
            156.0, 155.0, 155.0, 152.0, 151.0,
            151.0, 150.0, 148.0, 148.0, 147.0
        );

        ActivityStream as = activityProcessingService.createActivityStream(
            time,
            null,
            hr,
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int hrSpikes = activityProcessingService.detectHeartRateSpikes(a);

        assertEquals(2, hrSpikes);
    }

    @Test
    void testDetectHeartRateSpikes_WithEmptyData_returnsErrorValue() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();
        ActivityStream as = activityProcessingService.createActivityStream(
            List.of(),
            List.of(),
            List.of(),
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int hrSpikes = activityProcessingService.detectHeartRateSpikes(a);

        assertEquals(-1, hrSpikes);
    }

    @Test
    void testDetectHeartRateSpikes_WithMissingTimeStream_returnsErrorValue() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();
        ActivityStream as = activityProcessingService.createActivityStream(
            null,
            List.of(1.0),
            List.of(1.0),
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int hrSpikes = activityProcessingService.detectHeartRateSpikes(a);

        assertEquals(-1, hrSpikes);
    }

    @Test
    void testDetectHeartRateSpikes_WithMissingHeartRateStream_returnsErrorValue() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();
        ActivityStream as = activityProcessingService.createActivityStream(
            List.of(1.0),
            List.of(1.0),
            null,
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int hrSpikes = activityProcessingService.detectHeartRateSpikes(a);

        assertEquals(-1, hrSpikes);
    }

    @Test
    void testDetectHeartRateSpikes_WithStreamSizeMismatch_throws() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();
        ActivityStream as = new ActivityStream();
        as.setTimeStream(new byte[111111111]);
        as.setDistanceStream(new byte[1]);
        as.setHeartrateStream(new byte[1]);
        as.setSource(ActivityStreamSource.STRAVA);

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        assertThrows(IllegalStateException.class, () -> activityProcessingService.detectHeartRateSpikes(a));
    }

    @Test
    void testDetectHeartRateSpikes_WithMultipleConsecutiveSpikes_countsEachSeparately() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        List<Double> time = IntStream.rangeClosed(1, 120)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        List<Double> hr = List.of(
            // steady state
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            141.0, 140.0, 141.0, 140.0, 141.0,

            // spike 1
            150.0, 158.0, 165.0, 167.0, 165.0,
            163.0, 160.0, 158.0, 155.0, 150.0,

            // brief recovery
            145.0, 142.0, 141.0, 140.0, 141.0,
            144.0, 142.0, 141.0, 140.0, 141.0,

            // spike 2
            148.0, 155.0, 162.0, 168.0, 170.0,
            169.0, 167.0, 165.0, 162.0, 158.0,

            // brief recovery
            150.0, 145.0, 142.0, 141.0, 140.0,
            143.0, 142.0, 142.0, 141.0, 140.0,

            // spike 3
            147.0, 154.0, 160.0, 166.0, 168.0,
            167.0, 165.0, 162.0, 158.0, 155.0,

            // final recovery
            150.0, 148.0, 145.0, 143.0, 142.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            141.0, 140.0, 141.0, 140.0, 141.0
        );

        ActivityStream as = activityProcessingService.createActivityStream(
            time, null, hr, ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int hrSpikes = activityProcessingService.detectHeartRateSpikes(a);

        assertEquals(3, hrSpikes);
    }

    @Test
    void testDetectHeartRateSpikes_WithGradualIncrease_doesNotDetectSpike() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        List<Double> time = IntStream.rangeClosed(1, 120)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        // Gradual increase from 140 to 170 over 2 minutes (not a spike)
        List<Double> hr = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            hr.add(140.0 + (i * 0.5)); // slow increase: 140 -> 170 over 60 seconds
        }
        // hold at elevated HR
        for (int i = 0; i < 30; i++) {
            hr.add(170.0 + (i % 2 == 0 ? 1.0 : -1.0));
        }
        // gradual decrease
        for (int i = 0; i < 30; i++) {
            hr.add(170.0 - (i * 1.0));
        }

        ActivityStream as = activityProcessingService.createActivityStream(
            time, null, hr, ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int hrSpikes = activityProcessingService.detectHeartRateSpikes(a);

        assertEquals(0, hrSpikes);
    }

    @Test
    void testDetectHeartRateSpikes_WithShortUnsustainedSpike_doesNotDetect() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        List<Double> time = IntStream.rangeClosed(1, 60)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        List<Double> hr = List.of(
            // steady state
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,

            // brief unsustained spike (only 2 seconds elevated)
            160.0, 165.0,

            // immediate drop back
            142.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            141.0, 140.0, 141.0, 140.0, 141.0
        );

        ActivityStream as = activityProcessingService.createActivityStream(
            time, null, hr, ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int hrSpikes = activityProcessingService.detectHeartRateSpikes(a);

        assertEquals(0, hrSpikes);
    }

    @Test
    void testDetectHeartRateSpikes_WithMultipleOutliers_filtersCorrectly() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        List<Double> time = IntStream.rangeClosed(1, 75)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        List<Double> hr = List.of(
            // steady state with scattered outliers
            140.0, 141.0, 190.0, 141.0, 140.0,  // outlier at position 2
            141.0, 140.0, 141.0, 140.0, 141.0,
            140.0, 200.0, 140.0, 141.0, 140.0,  // outlier at position 11
            141.0, 140.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,

            // real spike
            150.0, 158.0, 165.0, 170.0, 168.0,
            165.0, 163.0, 160.0, 155.0, 150.0,

            // recovery with outlier
            145.0, 142.0, 195.0, 140.0, 141.0,  // outlier at position 37
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            140.0, 141.0, 140.0, 141.0, 140.0,
            141.0, 140.0, 141.0, 140.0, 141.0,
            141.0, 140.0, 141.0, 140.0, 141.0
        );

        ActivityStream as = activityProcessingService.createActivityStream(
            time, null, hr, ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int hrSpikes = activityProcessingService.detectHeartRateSpikes(a);

        assertEquals(1, hrSpikes); // Only the real spike, outliers filtered
    }

    @Test
    void testDetectHeartRateSpikes_LongRun_With5MinuteIntervals() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        // 30-minute run with 5-minute intervals
        List<Double> time = IntStream.rangeClosed(1, 1800)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        List<Double> hr = new ArrayList<>();

        // 0-5 min: easy warmup (130-135 bpm)
        for (int i = 0; i < 300; i++) {
            hr.add(130.0 + (i % 10) * 0.5);
        }

        // 5-10 min: spike to interval pace (165-170 bpm) - SPIKE 1
        hr.add(140.0); hr.add(150.0); hr.add(160.0); hr.add(168.0); hr.add(170.0);
        for (int i = 0; i < 295; i++) {
            hr.add(168.0 + (i % 4 - 1.5));
        }

        // 10-12 min: recovery (145-150 bpm)
        for (int i = 0; i < 120; i++) {
            hr.add(150.0 - i * 0.04);
        }

        // 12-17 min: spike to interval pace - SPIKE 2
        hr.add(140.0); hr.add(152.0); hr.add(162.0); hr.add(167.0); hr.add(170.0);
        for (int i = 0; i < 295; i++) {
            hr.add(168.0 + (i % 4 - 1.5));
        }

        // 17-19 min: recovery
        for (int i = 0; i < 120; i++) {
            hr.add(150.0 - i * 0.05);
        }

        // 19-24 min: spike to interval pace - SPIKE 3
        hr.add(138.0); hr.add(148.0); hr.add(158.0); hr.add(165.0); hr.add(170.0);
        for (int i = 0; i < 295; i++) {
            hr.add(168.0 + (i % 4 - 1.5));
        }

        // 24-30 min: cool down
        for (int i = 0; i < 360; i++) {
            hr.add(145.0 - i * 0.04);
        }

        ActivityStream as = activityProcessingService.createActivityStream(
            time, null, hr, ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int hrSpikes = activityProcessingService.detectHeartRateSpikes(a);

        assertEquals(3, hrSpikes);
    }

    @Test
    void testDetectHeartRateSpikes_TempoRun_NoSpikes() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        // 20-minute tempo run - steady increase to tempo, hold, then decrease
        List<Double> time = IntStream.rangeClosed(1, 1200)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        List<Double> hr = new ArrayList<>();

        // 0-3 min: warmup (130 -> 150)
        for (int i = 0; i < 180; i++) {
            hr.add(130.0 + (i / 180.0) * 20.0);
        }

        // 3-17 min: tempo pace (stable 160-165 bpm, gradual increase)
        for (int i = 0; i < 840; i++) {
            hr.add(160.0 + (i / 840.0) * 5.0 + (i % 5 - 2) * 0.5);
        }

        // 17-20 min: cool down (165 -> 140)
        for (int i = 0; i < 180; i++) {
            hr.add(165.0 - (i / 180.0) * 25.0);
        }

        ActivityStream as = activityProcessingService.createActivityStream(
            time, null, hr, ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int hrSpikes = activityProcessingService.detectHeartRateSpikes(a);

        assertEquals(0, hrSpikes); // Tempo run has no spikes, just steady effort
    }

    @Test
    void testDetectHeartRateSpikes_HillRepeats_DetectsEachClimb() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        List<Double> time = IntStream.rangeClosed(1, 600)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        List<Double> hr = new ArrayList<>();

        // 4 hill repeats: sharp increase, sustained high, then recovery
        for (int rep = 0; rep < 4; rep++) {
            // Easy jog between hills (30 seconds)
            for (int i = 0; i < 30; i++) {
                hr.add(135.0 + i * 0.2);
            }

            // Hill climb - sharp spike (10 seconds)
            hr.add(142.0); hr.add(150.0); hr.add(158.0); hr.add(165.0); hr.add(172.0);
            hr.add(175.0); hr.add(177.0); hr.add(178.0); hr.add(178.0); hr.add(178.0);

            // Sustained at top (20 seconds)
            for (int i = 0; i < 20; i++) {
                hr.add(176.0 + (i % 4 - 1.5));
            }

            // Downhill recovery (60 seconds)
            for (int i = 0; i < 60; i++) {
                hr.add(175.0 - i * 0.65);
            }
        }

        // Final cooldown
        for (int i = 0; i < 120; i++) {
            hr.add(135.0 - i * 0.15);
        }

        ActivityStream as = activityProcessingService.createActivityStream(
            time, null, hr, ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int hrSpikes = activityProcessingService.detectHeartRateSpikes(a);

        assertEquals(4, hrSpikes); // One spike per hill climb
    }

    @Test
    void testDetectHeartRateSpikes_VeryLongRun_60Minutes() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        // 60-minute long run with 3 surges
        List<Double> time = IntStream.rangeClosed(1, 3600)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        List<Double> hr = new ArrayList<>();

        // 0-20 min: easy pace building (135 -> 155 gradually)
        for (int i = 0; i < 1200; i++) {
            hr.add(135.0 + (i / 1200.0) * 20.0 + (i % 7 - 3) * 0.3);
        }

        // 20-21 min: surge 1
        for (int i = 0; i < 5; i++) {
            hr.add(155.0 + i * 3.0);
        }
        for (int i = 0; i < 55; i++) {
            hr.add(168.0 + (i % 4 - 1.5));
        }

        // 21-35 min: back to easy
        for (int i = 0; i < 840; i++) {
            hr.add(155.0 + (i % 7 - 3) * 0.3);
        }

        // 35-36 min: surge 2
        for (int i = 0; i < 5; i++) {
            hr.add(155.0 + i * 3.2);
        }
        for (int i = 0; i < 55; i++) {
            hr.add(170.0 + (i % 4 - 1.5));
        }

        // 36-50 min: back to easy
        for (int i = 0; i < 840; i++) {
            hr.add(157.0 + (i % 7 - 3) * 0.3);
        }

        // 50-51 min: surge 3
        for (int i = 0; i < 5; i++) {
            hr.add(157.0 + i * 2.8);
        }
        for (int i = 0; i < 55; i++) {
            hr.add(171.0 + (i % 4 - 1.5));
        }

        // 51-60 min: cooldown
        for (int i = 0; i < 540; i++) {
            hr.add(160.0 - i * 0.04);
        }

        ActivityStream as = activityProcessingService.createActivityStream(
            time, null, hr, ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int hrSpikes = activityProcessingService.detectHeartRateSpikes(a);

        assertEquals(3, hrSpikes);
    }

    @Test
    void testDetectHeartRateSpikes_EdgeCase_MinimumDataLength() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        // Only 5 data points (minimum for algorithm)
        List<Double> time = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        List<Double> hr = List.of(140.0, 141.0, 160.0, 165.0, 142.0);

        ActivityStream as = activityProcessingService.createActivityStream(
            time, null, hr, ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int hrSpikes = activityProcessingService.detectHeartRateSpikes(a);

        // Should handle without crashing (likely 0 spikes due to insufficient context)
        assertTrue(hrSpikes >= 0);
    }

    @Test
    void testDetectHeartRateSpikes_EdgeCase_TooFewDataPoints() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        // Only 3 data points (below minimum)
        List<Double> time = List.of(1.0, 2.0, 3.0);
        List<Double> hr = List.of(140.0, 165.0, 142.0);

        ActivityStream as = activityProcessingService.createActivityStream(
            time, null, hr, ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int hrSpikes = activityProcessingService.detectHeartRateSpikes(a);

        assertEquals(-1, hrSpikes); // Should return error code
    }

    @Test
    void testDetectHeartRateSpikes_AllSameValue_NoSpikes() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        List<Double> time = IntStream.rangeClosed(1, 100)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        // Flat line at 150 bpm
        List<Double> hr = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            hr.add(150.0);
        }

        ActivityStream as = activityProcessingService.createActivityStream(
            time, null, hr, ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int hrSpikes = activityProcessingService.detectHeartRateSpikes(a);

        assertEquals(0, hrSpikes);
    }

    @Test
    void testDetectHeartRateSpikes_StaircasePattern_NoSpikes() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        List<Double> time = IntStream.rangeClosed(1, 200)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        // Staircase: increases 2 bpm every 30 seconds (too gradual)
        List<Double> hr = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            hr.add(130.0 + (i / 30) * 2.0);
        }

        ActivityStream as = activityProcessingService.createActivityStream(
            time, null, hr, ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int hrSpikes = activityProcessingService.detectHeartRateSpikes(a);

        assertEquals(0, hrSpikes); // Too gradual to be spikes
    }

    // detectPaceSpikes tests

    @Test
    void testDetectPaceSpikes_With1Spike_returnsCorrectly() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        List<Double> time = IntStream.rangeClosed(1, 60)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        List<Double> distance = new ArrayList<>();
        double dist = 0;

        // Steady pace at 3.0 m/s for 20 seconds
        for (int i = 0; i < 20; i++) {
            dist += 3.0;
            distance.add(dist);
        }

        // Sharp acceleration to 4.5 m/s for 5 seconds
        for (int i = 0; i < 5; i++) {
            dist += 4.5;
            distance.add(dist);
        }

        // Steady at higher pace
        for (int i = 0; i < 35; i++) {
            dist += 3.2;
            distance.add(dist);
        }

        ActivityStream as = activityProcessingService.createActivityStream(
            time,
            distance,
            null,
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int paceSpikes = activityProcessingService.detectPaceSpikes(a);

        assertEquals(1, paceSpikes);
    }

    @Test
    void testDetectPaceSpikes_With0Spikes_returnsCorrectly() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        List<Double> time = IntStream.rangeClosed(1, 60)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        List<Double> distance = new ArrayList<>();
        double dist = 0;

        // Gradual pace increase from 3.0 to 4.0 m/s over 60 seconds (not a spike)
        for (int i = 0; i < 60; i++) {
            double speed = 3.0 + (i / 60.0) * 1.0;
            dist += speed;
            distance.add(dist);
        }

        ActivityStream as = activityProcessingService.createActivityStream(
            time,
            distance,
            null,
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int paceSpikes = activityProcessingService.detectPaceSpikes(a);

        assertEquals(0, paceSpikes);
    }

    @Test
    void testDetectPaceSpikes_With0SpikesAndOutlier_returnsCorrectly() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        List<Double> time = IntStream.rangeClosed(1, 60)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        List<Double> distance = new ArrayList<>();
        double dist = 0;

        // Steady pace with single GPS outlier
        for (int i = 0; i < 60; i++) {
            if (i == 30) {
                // GPS outlier - sudden spike in distance
                dist += 10.0; // Unrealistic jump
            } else {
                dist += 3.0; // Normal 3 m/s
            }
            distance.add(dist);
        }

        ActivityStream as = activityProcessingService.createActivityStream(
            time,
            distance,
            null,
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int paceSpikes = activityProcessingService.detectPaceSpikes(a);

        assertEquals(0, paceSpikes);
    }

    @Test
    void testDetectPaceSpikes_With2Spikes_returnsCorrectly() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        List<Double> time = IntStream.rangeClosed(1, 90)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        List<Double> distance = new ArrayList<>();
        double dist = 0;

        // Easy pace
        for (int i = 0; i < 20; i++) {
            dist += 3.0;
            distance.add(dist);
        }

        // Spike 1: acceleration to 4.5 m/s
        for (int i = 0; i < 5; i++) {
            dist += 4.5;
            distance.add(dist);
        }

        // Recovery
        for (int i = 0; i < 25; i++) {
            dist += 3.2;
            distance.add(dist);
        }

        // Spike 2: acceleration to 5.0 m/s
        for (int i = 0; i < 5; i++) {
            dist += 5.0;
            distance.add(dist);
        }

        // Cool down
        for (int i = 0; i < 35; i++) {
            dist += 2.8;
            distance.add(dist);
        }

        ActivityStream as = activityProcessingService.createActivityStream(
            time,
            distance,
            null,
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int paceSpikes = activityProcessingService.detectPaceSpikes(a);

        assertEquals(2, paceSpikes);
    }

    @Test
    void testDetectPaceSpikes_WithEmptyData_returnsErrorValue() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();
        ActivityStream as = activityProcessingService.createActivityStream(
            List.of(),
            List.of(),
            List.of(),
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int paceSpikes = activityProcessingService.detectPaceSpikes(a);

        assertEquals(-1, paceSpikes);
    }

    @Test
    void testDetectPaceSpikes_WithMissingTimeStream_returnsErrorValue() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();
        ActivityStream as = activityProcessingService.createActivityStream(
            null,
            List.of(1.0),
            List.of(1.0),
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int paceSpikes = activityProcessingService.detectPaceSpikes(a);

        assertEquals(-1, paceSpikes);
    }

    @Test
    void testDetectPaceSpikes_WithMissingDistanceStream_returnsErrorValue() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();
        ActivityStream as = activityProcessingService.createActivityStream(
            List.of(1.0),
            null,
            List.of(1.0),
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int paceSpikes = activityProcessingService.detectPaceSpikes(a);

        assertEquals(-1, paceSpikes);
    }

    @Test
    void testDetectPaceSpikes_WithStreamSizeMismatch_throws() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        ActivityStream as = new ActivityStream();
        as.setTimeStream(new byte[111111111]);
        as.setDistanceStream(new byte[1]);
        as.setHeartrateStream(new byte[1]);
        as.setSource(ActivityStreamSource.STRAVA);

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        assertThrows(IllegalStateException.class, () -> activityProcessingService.detectPaceSpikes(a));
    }

    @Test
    void testDetectPaceSpikes_WithMultipleConsecutiveSpikes_countsEachSeparately() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        List<Double> time = IntStream.rangeClosed(1, 120)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        List<Double> distance = new ArrayList<>();
        double dist = 0;

        // Easy pace
        for (int i = 0; i < 20; i++) {
            dist += 3.0;
            distance.add(dist);
        }

        // Spike 1
        for (int i = 0; i < 6; i++) {
            dist += 4.5;
            distance.add(dist);
        }

        // Brief recovery
        for (int i = 0; i < 14; i++) {
            dist += 3.2;
            distance.add(dist);
        }

        // Spike 2
        for (int i = 0; i < 6; i++) {
            dist += 4.8;
            distance.add(dist);
        }

        // Brief recovery
        for (int i = 0; i < 14; i++) {
            dist += 3.1;
            distance.add(dist);
        }

        // Spike 3
        for (int i = 0; i < 6; i++) {
            dist += 5.0;
            distance.add(dist);
        }

        // Final recovery
        for (int i = 0; i < 54; i++) {
            dist += 2.9;
            distance.add(dist);
        }

        ActivityStream as = activityProcessingService.createActivityStream(
            time,
            distance,
            null,
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int paceSpikes = activityProcessingService.detectPaceSpikes(a);

        assertEquals(3, paceSpikes);
    }

    @Test
    void testDetectPaceSpikes_WithShortUnsustainedSpike_doesNotDetect() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        List<Double> time = IntStream.rangeClosed(1, 60)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        List<Double> distance = new ArrayList<>();
        double dist = 0;

        // Steady pace
        for (int i = 0; i < 30; i++) {
            dist += 3.0;
            distance.add(dist);
        }

        // Very brief unsustained spike (only 2 seconds)
        dist += 4.5;
        distance.add(dist);
        dist += 4.5;
        distance.add(dist);

        // Back to normal immediately
        for (int i = 0; i < 28; i++) {
            dist += 3.0;
            distance.add(dist);
        }

        ActivityStream as = activityProcessingService.createActivityStream(
            time,
            distance,
            null,
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int paceSpikes = activityProcessingService.detectPaceSpikes(a);

        assertEquals(0, paceSpikes);
    }

    @Test
    void testDetectPaceSpikes_IntervalWorkout_400mRepeats() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        // 20-minute workout: 5x400m intervals at 4 m/s with 200m recovery at 2.5 m/s
        List<Double> time = new ArrayList<>();
        List<Double> distance = new ArrayList<>();
        double dist = 0;
        int t = 1;

        // Warmup: 5 minutes at 3 m/s (900m)
        for (int i = 0; i < 300; i++) {
            time.add((double) t++);
            dist += 3.0;
            distance.add(dist);
        }

        // 5x400m intervals
        for (int rep = 0; rep < 5; rep++) {
            // 400m interval at 4 m/s (100 seconds)
            for (int i = 0; i < 100; i++) {
                time.add((double) t++);
                dist += 4.0;
                distance.add(dist);
            }

            // 200m recovery at 2.5 m/s (80 seconds)
            if (rep < 4) { // No recovery after last interval
                for (int i = 0; i < 80; i++) {
                    time.add((double) t++);
                    dist += 2.5;
                    distance.add(dist);
                }
            }
        }

        // Cooldown: remaining time at 2.8 m/s
        while (t <= 1200) {
            time.add((double) t++);
            dist += 2.8;
            distance.add(dist);
        }

        ActivityStream as = activityProcessingService.createActivityStream(
            time,
            distance,
            null,
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int paceSpikes = activityProcessingService.detectPaceSpikes(a);

        assertEquals(5, paceSpikes); // One spike per interval
    }

    @Test
    void testDetectPaceSpikes_ProgressionRun_NoSpikes() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        // 15-minute progression run: gradually increase from 3.0 to 4.5 m/s
        List<Double> time = IntStream.rangeClosed(1, 900)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        List<Double> distance = new ArrayList<>();
        double dist = 0;

        for (int i = 0; i < 900; i++) {
            // Gradual speed increase
            double speed = 3.0 + (i / 900.0) * 1.5;
            dist += speed;
            distance.add(dist);
        }

        ActivityStream as = activityProcessingService.createActivityStream(
            time,
            distance,
            null,
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int paceSpikes = activityProcessingService.detectPaceSpikes(a);

        assertEquals(0, paceSpikes); // Progression run has no spikes
    }

    @Test
    void testDetectPaceSpikes_EdgeCase_MinimumDataLength() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        // Only 5 data points (minimum for algorithm)
        List<Double> time = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        List<Double> distance = List.of(3.0, 6.0, 10.0, 15.0, 18.0);

        ActivityStream as = activityProcessingService.createActivityStream(
            time,
            distance,
            null,
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int paceSpikes = activityProcessingService.detectPaceSpikes(a);

        // Should handle without crashing (likely 0 spikes due to insufficient context)
        assertTrue(paceSpikes >= 0);
    }

    @Test
    void testDetectPaceSpikes_EdgeCase_TooFewDataPoints() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        // Only 3 data points (below minimum)
        List<Double> time = List.of(1.0, 2.0, 3.0);
        List<Double> distance = List.of(3.0, 7.0, 10.0);

        ActivityStream as = activityProcessingService.createActivityStream(
            time,
            distance,
            null,
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int paceSpikes = activityProcessingService.detectPaceSpikes(a);

        assertEquals(-1, paceSpikes); // Should return error code
    }

    @Test
    void testDetectPaceSpikes_AllSameSpeed_NoSpikes() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        List<Double> time = IntStream.rangeClosed(1, 100)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        List<Double> distance = new ArrayList<>();
        double dist = 0;

        // Constant 3.5 m/s for 100 seconds
        for (int i = 0; i < 100; i++) {
            dist += 3.5;
            distance.add(dist);
        }

        ActivityStream as = activityProcessingService.createActivityStream(
            time,
            distance,
            null,
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int paceSpikes = activityProcessingService.detectPaceSpikes(a);

        assertEquals(0, paceSpikes);
    }

    @Test
    void testDetectPaceSpikes_WithMultipleOutliers_filtersCorrectly() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        List<Double> time = IntStream.rangeClosed(1, 80)
            .mapToDouble(i -> i)
            .boxed()
            .toList();

        List<Double> distance = new ArrayList<>();
        double dist = 0;

        // Steady pace with scattered GPS outliers
        for (int i = 0; i < 80; i++) {
            if (i == 10 || i == 25 || i == 50) {
                // GPS outliers - unrealistic jumps
                dist += 15.0;
            } else if (i >= 40 && i < 45) {
                // Real spike
                dist += 4.8;
            } else {
                // Normal pace
                dist += 3.0;
            }
            distance.add(dist);
        }

        ActivityStream as = activityProcessingService.createActivityStream(
            time,
            distance,
            null,
            ActivityStreamSource.STRAVA
        );

        activityStreamRepository.save(as);
        a.setActivityStream(as);
        activityRepository.save(a);

        int paceSpikes = activityProcessingService.detectPaceSpikes(a);

        assertEquals(1, paceSpikes); // Only the real spike, outliers filtered
    }



    @Test
    void testGetLastActivityBeforeDate_returnsEmptyWhenNoActivityBeforeDate() {
        activityRepository.deleteAll();
        activityRepository.flush();

        ApplicationUser user = userRepository.findAll().getFirst();

        Activity act = getStravaActivity();
        act.setUser(user);
        act.setStartDate(Instant.now());
        act.setName("only");

        activityRepository.save(act);

        // Choose a date far in the past so there are no activities before it
        LocalDate date = LocalDate.now().minusDays(10);

        Optional<Activity> result = activityProcessingService.getLastActivityBeforeDate(user.getEmail(), date);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetLastNActivities_returnsLastN() {
        activityRepository.deleteAll();
        activityRepository.flush();

        ApplicationUser user = userRepository.findAll().getFirst();

        Activity a1 = getStravaActivity();
        a1.setUser(user);
        a1.setStartDate(Instant.now().minusSeconds(300));
        a1.setName("a1");

        Activity a2 = getStravaActivity();
        a2.setUser(user);
        a2.setStartDate(Instant.now().minusSeconds(200));
        a2.setName("a2");

        Activity a3 = getStravaActivity();
        a3.setUser(user);
        a3.setStartDate(Instant.now().minusSeconds(100));
        a3.setName("a3");

        activityRepository.saveAll(List.of(a1, a2, a3));

        List<Activity> result = activityProcessingService.getLastActivities(user.getEmail(), 2);

        assertAll(
            () -> assertEquals(2, result.size()),
            () -> assertEquals("a3", result.get(0).getName()),
            () -> assertEquals("a2", result.get(1).getName())
        );
    }

    @Test
    void testGetLastNActivities_returnsAllIfLessThanN() {
        activityRepository.deleteAll();
        activityRepository.flush();

        ApplicationUser user = userRepository.findAll().getFirst();

        Activity a1 = getStravaActivity();
        a1.setUser(user);
        a1.setStartDate(Instant.now().minusSeconds(200));
        a1.setName("only1");

        activityRepository.save(a1);

        List<Activity> result = activityProcessingService.getLastActivities(user.getEmail(), 5);

        assertAll(
            () -> assertEquals(1, result.size()),
            () -> assertEquals("only1", result.get(0).getName())
        );
    }

    @Test
    void testGetLastNActivities_returnsEmptyWhenNoActivities() {
        activityRepository.deleteAll();
        activityRepository.flush();

        ApplicationUser user = userRepository.findAll().getFirst();

        List<Activity> result = activityProcessingService.getLastActivities(user.getEmail(), 3);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetLastNActivities_throwsOnInvalidN() {
        ApplicationUser user = userRepository.findAll().getFirst();

        assertThrows(IllegalArgumentException.class, () -> activityProcessingService.getLastActivities(user.getEmail(), 0));
        assertThrows(IllegalArgumentException.class, () -> activityProcessingService.getLastActivities(user.getEmail(), -1));
    }

    @Test
    void testGetLastRunningActivityBeforeDate_returnsLatestRunningBefore() {
        activityRepository.deleteAll();
        activityRepository.flush();

        ApplicationUser user = userRepository.findAll().getFirst();

        Activity runOld = getStravaActivity();
        runOld.setUser(user);
        runOld.setWorkoutType(com.smartroute.smartroute1.entity.enums.WorkoutType.EASY_RUN);
        runOld.setStartDate(Instant.now().minusSeconds(5 * 24 * 3600));
        runOld.setName("runOld");

        Activity runRecent = getStravaActivity();
        runRecent.setUser(user);
        runRecent.setWorkoutType(com.smartroute.smartroute1.entity.enums.WorkoutType.TEMPO_RUN);
        runRecent.setStartDate(Instant.now().minusSeconds(2 * 24 * 3600));
        runRecent.setName("runRecent");

        Activity nonRun = getStravaActivity();
        nonRun.setUser(user);
        nonRun.setWorkoutType(com.smartroute.smartroute1.entity.enums.WorkoutType.GYM_PREHAB);
        nonRun.setStartDate(Instant.now().minusSeconds(24 * 3600));
        nonRun.setName("gym");

        activityRepository.saveAll(List.of(runOld, runRecent, nonRun));

        LocalDate date = LocalDate.now().minusDays(1);

        Optional<Activity> result = activityProcessingService.getLastRunningActivityBeforeDate(user.getEmail(), date);

        assertTrue(result.isPresent());
        assertEquals("runRecent", result.get().getName());
    }

    @Test
    void testGetLastRunningActivityBeforeDate_returnsEmptyWhenNoRunningBefore() {
        activityRepository.deleteAll();
        activityRepository.flush();

        ApplicationUser user = userRepository.findAll().getFirst();

        Activity gym1 = getStravaActivity();
        gym1.setUser(user);
        gym1.setWorkoutType(com.smartroute.smartroute1.entity.enums.WorkoutType.GYM_PREHAB);
        gym1.setStartDate(Instant.now().minusSeconds(5 * 24 * 3600));
        gym1.setName("gym1");

        Activity mobility = getStravaActivity();
        mobility.setUser(user);
        mobility.setWorkoutType(com.smartroute.smartroute1.entity.enums.WorkoutType.MOBILITY);
        mobility.setStartDate(Instant.now().minusSeconds(2 * 24 * 3600));
        mobility.setName("mob");

        activityRepository.saveAll(List.of(gym1, mobility));

        LocalDate date = LocalDate.now().minusDays(1);

        Optional<Activity> result = activityProcessingService.getLastRunningActivityBeforeDate(user.getEmail(), date);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetLastRunningActivityBeforeDate_ignoresRunningAfterDate() {
        activityRepository.deleteAll();
        activityRepository.flush();

        ApplicationUser user = userRepository.findAll().getFirst();

        Activity runAfter = getStravaActivity();
        runAfter.setUser(user);
        runAfter.setWorkoutType(com.smartroute.smartroute1.entity.enums.WorkoutType.EASY_RUN);
        runAfter.setStartDate(Instant.now().plusSeconds(24 * 3600));
        runAfter.setName("after");

        activityRepository.save(runAfter);

        LocalDate date = LocalDate.now();

        Optional<Activity> result = activityProcessingService.getLastRunningActivityBeforeDate(user.getEmail(), date);

        assertTrue(result.isEmpty());
    }
}
