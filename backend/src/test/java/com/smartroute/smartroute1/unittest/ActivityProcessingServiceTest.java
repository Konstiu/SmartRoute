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
import com.smartroute.smartroute1.util.Codec;
import jakarta.transaction.Transactional;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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
    @SpyBean
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

    @BeforeEach
    void setup() {
        doNothing().when(activityProcessingService).fetchWeatherForActivity(Mockito.any()); //Avoid API calls in testing
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

    // createActivityStream tests

    @Test
    void testCreateActivityStream_returnsCorrectly() {
        List<Double> time = List.of(0.0, 1.0);
        List<Double> distance = List.of(0.0, 3.0);
        List<Double> heartRate = List.of(143.0, 144.0);
        ActivityStreamSource source = ActivityStreamSource.SMART_ROUTE;

        ActivityStream as = activityProcessingService.createActivityStream(time, distance, heartRate, source);

        assertAll(
                () -> assertNotNull(as),
                () -> assertEquals(source, as.getSource()),
                () -> assertArrayEquals(Codec.encodeDoubleArray(time.stream().mapToDouble(Double::doubleValue).toArray()), as.getTimeStream()),
                () -> assertArrayEquals(Codec.encodeDoubleArray(distance.stream().mapToDouble(Double::doubleValue).toArray()), as.getDistanceStream()),
                () -> assertArrayEquals(Codec.encodeDoubleArray(heartRate.stream().mapToDouble(Double::doubleValue).toArray()), as.getHeartrateStream())
        );
    }

    @Test
    void testCreateActivityStream_WithAllStreamsNull_returnsCorrectly() {
        List<Double> time = null;
        List<Double> distance = null;
        List<Double> heartRate = null;
        ActivityStreamSource source = ActivityStreamSource.SMART_ROUTE;

        ActivityStream as = activityProcessingService.createActivityStream(time, distance, heartRate, source);

        assertAll(
                () -> assertNotNull(as),
                () -> assertEquals(source, as.getSource()),
                () -> assertNull(as.getTimeStream()),
                () -> assertNull(as.getDistanceStream()),
                () -> assertNull(as.getHeartrateStream())
        );
    }

    @Test
    void testCreateActivityStream_WithSomeStreamsNull_returnsCorrectly() {
        List<Double> time = null;
        List<Double> distance = null;
        List<Double> heartRate = List.of(143.0, 144.0);
        ActivityStreamSource source = ActivityStreamSource.SMART_ROUTE;

        ActivityStream as = activityProcessingService.createActivityStream(time, distance, heartRate, source);

        assertAll(
                () -> assertNotNull(as),
                () -> assertEquals(source, as.getSource()),
                () -> assertNull(as.getTimeStream()),
                () -> assertNull(as.getDistanceStream()),
                () -> assertArrayEquals(Codec.encodeDoubleArray(heartRate.stream().mapToDouble(Double::doubleValue).toArray()), as.getHeartrateStream())
        );
    }

    @Test
    void testCreateActivityStream_WithSizeMismatch_returnsNull() {
        List<Double> time = List.of(0.0, 1.0, 2.0, 3.0);
        List<Double> distance = List.of(0.0, 3.0);
        List<Double> heartRate = List.of(143.0, 144.0);
        ActivityStreamSource source = ActivityStreamSource.SMART_ROUTE;

        ActivityStream as = activityProcessingService.createActivityStream(time, distance, heartRate, source);

        assertAll(
                () -> assertNull(as)
        );
    }

    @Test
    void testCreateActivityStream_WithSizeMismatchAndNullStream_returnsNull() {
        List<Double> time = List.of(0.0, 1.0, 2.0, 3.0);
        List<Double> distance = List.of(0.0, 3.0);
        List<Double> heartRate = null;
        ActivityStreamSource source = ActivityStreamSource.SMART_ROUTE;

        ActivityStream as = activityProcessingService.createActivityStream(time, distance, heartRate, source);

        assertAll(
                () -> assertNull(as)
        );
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
        );

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
        hr.add(140.0);
        hr.add(150.0);
        hr.add(160.0);
        hr.add(168.0);
        hr.add(170.0);
        for (int i = 0; i < 295; i++) {
            hr.add(168.0 + (i % 4 - 1.5));
        }

        // 10-12 min: recovery (145-150 bpm)
        for (int i = 0; i < 120; i++) {
            hr.add(150.0 - i * 0.04);
        }

        // 12-17 min: spike to interval pace - SPIKE 2
        hr.add(140.0);
        hr.add(152.0);
        hr.add(162.0);
        hr.add(167.0);
        hr.add(170.0);
        for (int i = 0; i < 295; i++) {
            hr.add(168.0 + (i % 4 - 1.5));
        }

        // 17-19 min: recovery
        for (int i = 0; i < 120; i++) {
            hr.add(150.0 - i * 0.05);
        }

        // 19-24 min: spike to interval pace - SPIKE 3
        hr.add(138.0);
        hr.add(148.0);
        hr.add(158.0);
        hr.add(165.0);
        hr.add(170.0);
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
            hr.add(142.0);
            hr.add(150.0);
            hr.add(158.0);
            hr.add(165.0);
            hr.add(172.0);
            hr.add(175.0);
            hr.add(177.0);
            hr.add(178.0);
            hr.add(178.0);
            hr.add(178.0);

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

        List<Double> time = IntStream.rangeClosed(1, 67)
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

        // Sharp acceleration to 4.5 m/s for 12 seconds
        for (int i = 0; i < 12; i++) {
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
            double speed = 3.0 + (i / 60.0);
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

        List<Double> time = IntStream.rangeClosed(1, 110)
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
        for (int i = 0; i < 15; i++) {
            dist += 4.5;
            distance.add(dist);
        }

        // Recovery
        for (int i = 0; i < 25; i++) {
            dist += 3.2;
            distance.add(dist);
        }

        // Spike 2: acceleration to 5.0 m/s
        for (int i = 0; i < 15; i++) {
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

        List<Double> time = IntStream.rangeClosed(1, 147)
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
        for (int i = 0; i < 15; i++) {
            dist += 4.5;
            distance.add(dist);
        }

        // Brief recovery
        for (int i = 0; i < 14; i++) {
            dist += 3.2;
            distance.add(dist);
        }

        // Spike 2
        for (int i = 0; i < 15; i++) {
            dist += 4.8;
            distance.add(dist);
        }

        // Brief recovery
        for (int i = 0; i < 14; i++) {
            dist += 3.1;
            distance.add(dist);
        }

        // Spike 3
        for (int i = 0; i < 15; i++) {
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
            } else if (i >= 30 && i < 45) {
                // Real spike
                dist += 5;
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
    void testDetectPaceSpikes_WithRealWorldData_UnstableRunWithoutSustainedSpikes_ReturnsCorrectly() {
        activityRepository.deleteAll();
        activityRepository.flush();

        Activity a = getStravaActivity();

        double[] dist =
                new double[]{0.0, 0.0, 5.800000190734863, 8.600000381469727, 12.100000381469727, 15.600000381469727, 23.299999237060547, 26.700000762939453, 26.700000762939453, 43.70000076293945, 48.0, 48.0, 52.0, 70.30000305175781,
                        71.69999694824219, 89.0,
                        106.69999694824219, 125.5999984741211, 147.10000610351562, 149.3000030517578, 154.3000030517578, 162.10000610351562, 164.39999389648438, 182.89999389648438, 198.1999969482422, 215.10000610351562, 236.60000610351562,
                        238.60000610351562, 250.60000610351562, 266.0, 269.8999938964844, 284.20001220703125, 290.5, 294.0, 295.29998779296875, 298.29998779296875, 300.8999938964844, 305.8999938964844, 319.29998779296875, 336.29998779296875, 345.5,
                        354.79998779296875, 366.70001220703125, 383.70001220703125, 388.0, 404.5, 422.5, 435.1000061035156, 448.6000061035156, 450.70001220703125, 459.1000061035156, 467.3999938964844, 472.5, 475.5, 476.8999938964844,
                        481.20001220703125, 486.20001220703125, 491.79998779296875, 498.70001220703125, 503.1000061035156, 505.0, 508.3999938964844, 510.29998779296875, 525.5, 532.0, 533.7999877929688, 537.7000122070312, 554.0, 574.5999755859375,
                        582.5, 588.0999755859375, 591.0, 605.2000122070312, 619.2000122070312, 630.9000244140625, 643.7000122070312, 654.4000244140625, 658.5999755859375, 660.5, 662.5, 666.4000244140625, 668.2999877929688, 670.7999877929688,
                        681.0999755859375, 690.0, 692.0999755859375, 694.2000122070312, 698.0999755859375, 699.7000122070312, 702.5999755859375, 704.5, 704.9000244140625, 707.5999755859375, 711.0, 714.7000122070312, 720.2999877929688,
                        723.2000122070312, 728.4000244140625, 733.0999755859375, 741.7000122070312, 744.0999755859375, 752.9000244140625, 754.7999877929688, 763.5999755859375, 765.2999877929688, 770.5999755859375, 772.2000122070312,
                        773.2000122070312,
                        774.4000244140625, 776.0, 778.5999755859375, 781.5, 785.7000122070312, 800.7000122070312, 808.2000122070312, 820.7999877929688, 828.2000122070312, 835.0999755859375, 837.2000122070312, 838.7000122070312, 844.2999877929688,
                        847.2999877929688, 855.7999877929688, 863.0999755859375, 868.7000122070312, 870.4000244140625, 874.7999877929688, 878.5999755859375, 883.0, 885.7000122070312, 887.4000244140625, 888.5, 889.5, 891.2999877929688, 896.0, 898.5,
                        900.0, 905.4000244140625, 907.5999755859375, 914.5, 929.4000244140625, 930.5, 938.7000122070312, 958.0, 967.4000244140625, 971.4000244140625, 973.7000122070312, 979.2999877929688, 991.9000244140625, 995.9000244140625,
                        1000.2000122070312, 1006.2000122070312, 1011.0, 1014.0, 1014.4000244140625, 1015.0999755859375, 1016.5, 1021.4000244140625, 1023.0999755859375, 1025.300048828125, 1031.4000244140625, 1033.699951171875, 1047.5, 1054.5,
                        1067.0,
                        1078.9000244140625, 1084.699951171875, 1092.199951171875, 1099.5999755859375, 1103.699951171875, 1105.5999755859375, 1111.4000244140625, 1121.9000244140625, 1123.699951171875, 1129.300048828125, 1132.5999755859375,
                        1139.4000244140625, 1141.5, 1148.9000244140625, 1152.0, 1158.4000244140625, 1159.9000244140625, 1172.199951171875, 1181.4000244140625, 1191.800048828125, 1200.800048828125, 1204.199951171875, 1213.699951171875, 1215.0,
                        1224.5,
                        1237.4000244140625, 1244.9000244140625, 1248.5999755859375, 1258.199951171875, 1261.9000244140625, 1278.4000244140625, 1299.300048828125, 1314.4000244140625, 1322.199951171875, 1325.9000244140625, 1341.199951171875,
                        1353.5999755859375, 1367.199951171875, 1383.9000244140625, 1403.699951171875, 1417.300048828125, 1429.4000244140625, 1431.0999755859375, 1444.300048828125, 1462.4000244140625, 1476.699951171875, 1489.199951171875,
                        1496.4000244140625, 1506.300048828125, 1522.5, 1536.800048828125, 1540.9000244140625, 1545.199951171875, 1546.300048828125, 1555.0, 1574.5999755859375, 1584.0999755859375, 1594.9000244140625, 1615.699951171875, 1630.0,
                        1631.5999755859375, 1639.300048828125, 1644.300048828125, 1648.5999755859375, 1668.800048828125, 1673.5999755859375, 1685.300048828125, 1695.5, 1698.800048828125, 1710.5999755859375, 1719.5, 1728.199951171875,
                        1737.4000244140625, 1752.5999755859375, 1771.800048828125, 1775.199951171875, 1791.199951171875, 1793.4000244140625, 1794.699951171875, 1797.300048828125, 1800.4000244140625, 1802.199951171875, 1815.5999755859375, 1817.5,
                        1824.0, 1827.0, 1831.4000244140625, 1843.5999755859375, 1844.5999755859375, 1847.699951171875, 1855.0, 1856.300048828125, 1862.0999755859375, 1865.699951171875, 1870.5999755859375, 1874.0, 1879.5999755859375,
                        1884.300048828125,
                        1887.5, 1888.9000244140625, 1903.0999755859375, 1921.300048828125, 1932.9000244140625, 1938.5999755859375, 1941.4000244140625, 1945.0999755859375, 1950.300048828125, 1960.800048828125, 1962.4000244140625, 1964.0999755859375,
                        1967.5, 1976.5, 1990.800048828125, 1998.300048828125, 2001.300048828125, 2005.199951171875, 2008.0, 2008.4000244140625, 2012.699951171875, 2021.4000244140625, 2026.300048828125, 2032.699951171875, 2039.5999755859375,
                        2050.89990234375, 2054.699951171875, 2063.10009765625, 2075.0, 2085.0, 2088.89990234375, 2102.39990234375, 2118.199951171875, 2135.699951171875, 2154.39990234375, 2174.39990234375, 2180.0, 2183.39990234375,
                        2185.300048828125,
                        2201.60009765625, 2206.300048828125, 2209.5, 2223.5, 2238.300048828125, 2240.10009765625, 2243.0, 2246.60009765625, 2251.10009765625, 2254.39990234375, 2269.10009765625, 2273.300048828125, 2274.60009765625, 2275.5,
                        2277.699951171875, 2278.199951171875, 2279.199951171875, 2284.199951171875, 2285.300048828125, 2286.10009765625, 2293.60009765625, 2294.10009765625, 2295.300048828125, 2298.39990234375, 2308.60009765625, 2314.5, 2321.5,
                        2329.0,
                        2335.5, 2337.699951171875, 2353.10009765625, 2365.60009765625, 2380.699951171875, 2396.800048828125, 2406.5, 2421.60009765625, 2431.60009765625, 2444.199951171875, 2456.199951171875, 2467.300048828125, 2470.800048828125,
                        2474.10009765625, 2477.5, 2481.89990234375, 2483.5, 2487.199951171875, 2489.39990234375, 2496.5, 2506.800048828125, 2528.0, 2529.89990234375, 2537.60009765625, 2552.39990234375, 2572.39990234375, 2582.60009765625,
                        2598.10009765625, 2612.60009765625, 2623.10009765625, 2624.800048828125, 2632.699951171875, 2636.300048828125, 2643.60009765625, 2656.800048828125, 2671.300048828125, 2677.800048828125, 2679.699951171875, 2695.0,
                        2709.89990234375, 2713.39990234375, 2718.5, 2731.199951171875, 2733.0, 2750.89990234375, 2763.89990234375, 2775.10009765625, 2786.60009765625, 2788.39990234375, 2800.39990234375, 2816.800048828125, 2837.10009765625,
                        2851.199951171875, 2863.0, 2877.0, 2889.199951171875, 2891.5, 2901.0, 2905.60009765625, 2920.800048828125, 2933.300048828125, 2948.5, 2963.39990234375, 2974.10009765625, 2989.5, 2992.89990234375, 3001.199951171875,
                        3003.699951171875, 3005.5, 3010.800048828125, 3021.60009765625, 3030.60009765625, 3032.800048828125, 3034.800048828125, 3046.5, 3059.89990234375, 3069.10009765625, 3074.199951171875, 3078.800048828125, 3082.39990234375,
                        3083.699951171875, 3085.699951171875, 3095.699951171875, 3115.800048828125, 3117.60009765625, 3125.10009765625, 3129.89990234375, 3143.699951171875, 3158.10009765625, 3162.199951171875, 3166.60009765625, 3168.89990234375,
                        3176.10009765625, 3184.60009765625, 3189.800048828125, 3193.89990234375, 3206.800048828125, 3218.5, 3223.39990234375, 3238.699951171875, 3252.300048828125, 3254.10009765625, 3262.60009765625, 3277.699951171875,
                        3283.89990234375,
                        3285.699951171875, 3287.89990234375, 3293.39990234375, 3294.800048828125, 3306.800048828125, 3313.699951171875, 3329.199951171875, 3336.800048828125, 3340.10009765625, 3351.60009765625, 3363.10009765625, 3365.5,
                        3381.89990234375, 3388.39990234375, 3393.800048828125, 3406.89990234375, 3420.39990234375, 3431.699951171875, 3438.10009765625, 3451.199951171875, 3464.39990234375, 3470.89990234375, 3488.699951171875, 3509.0,
                        3522.89990234375,
                        3543.10009765625, 3558.60009765625, 3559.89990234375, 3568.699951171875, 3574.199951171875, 3577.800048828125, 3591.0, 3605.300048828125, 3620.300048828125, 3631.5, 3638.0, 3652.300048828125, 3664.60009765625,
                        3668.199951171875,
                        3672.5, 3682.699951171875, 3687.60009765625, 3700.300048828125, 3702.199951171875, 3714.199951171875, 3721.199951171875, 3733.699951171875, 3750.39990234375, 3762.89990234375, 3765.199951171875, 3775.699951171875,
                        3782.60009765625, 3784.5, 3802.300048828125, 3819.699951171875, 3823.89990234375, 3828.10009765625, 3840.300048828125, 3850.39990234375, 3852.199951171875, 3874.0, 3876.0, 3884.699951171875, 3889.10009765625,
                        3899.89990234375,
                        3915.699951171875, 3923.699951171875, 3926.0, 3935.699951171875, 3939.39990234375, 3943.39990234375, 3945.39990234375, 3955.60009765625, 3960.60009765625, 3963.800048828125, 3967.60009765625, 3977.10009765625,
                        3982.199951171875,
                        3987.300048828125, 3995.60009765625, 3996.89990234375, 4001.0, 4004.699951171875, 4012.60009765625, 4013.800048828125, 4015.800048828125, 4020.800048828125, 4037.300048828125, 4040.10009765625, 4055.800048828125,
                        4068.60009765625, 4085.0, 4093.0, 4111.39990234375, 4125.2998046875, 4139.0, 4149.7001953125, 4170.39990234375, 4184.60009765625, 4186.10009765625, 4190.89990234375, 4193.89990234375, 4196.0, 4203.39990234375,
                        4205.89990234375,
                        4219.60009765625, 4221.7001953125, 4229.39990234375, 4231.39990234375, 4242.89990234375, 4245.10009765625, 4252.2001953125, 4254.5, 4261.7998046875, 4268.60009765625, 4277.7001953125, 4281.2001953125, 4283.2998046875,
                        4297.7001953125, 4302.39990234375, 4308.89990234375, 4315.10009765625, 4319.10009765625, 4334.39990234375, 4346.2998046875, 4346.7001953125, 4350.60009765625, 4352.89990234375, 4359.2001953125, 4366.7001953125,
                        4368.2998046875,
                        4375.2001953125, 4377.2001953125, 4379.39990234375, 4388.2998046875, 4390.2001953125, 4398.7998046875, 4401.10009765625, 4405.89990234375, 4410.60009765625, 4416.0, 4426.5, 4430.7001953125, 4435.2998046875, 4437.0,
                        4444.7998046875, 4447.0, 4450.5, 4451.7001953125, 4459.2001953125, 4462.7998046875, 4467.2998046875, 4470.0, 4480.10009765625, 4486.2001953125, 4492.60009765625, 4494.7001953125, 4498.39990234375, 4502.2001953125,
                        4509.2998046875, 4518.2998046875, 4522.60009765625, 4530.2998046875, 4549.5, 4551.0, 4556.5, 4558.5, 4563.2001953125, 4568.10009765625, 4576.39990234375, 4582.60009765625, 4587.2998046875, 4597.2998046875, 4598.7998046875,
                        4603.89990234375, 4612.7998046875, 4619.7998046875, 4624.39990234375, 4626.89990234375, 4634.7001953125, 4642.2998046875, 4648.10009765625, 4651.2998046875, 4661.5, 4663.5, 4666.7998046875, 4670.0, 4672.10009765625,
                        4675.89990234375, 4678.39990234375, 4681.39990234375, 4688.60009765625, 4698.7001953125, 4704.10009765625, 4712.10009765625, 4714.2998046875, 4727.60009765625, 4731.39990234375, 4737.2998046875, 4742.5, 4744.60009765625,
                        4747.7998046875, 4753.39990234375, 4757.5, 4759.89990234375, 4763.89990234375, 4765.7001953125, 4777.60009765625, 4784.7001953125, 4787.7998046875, 4799.89990234375, 4801.39990234375, 4811.60009765625, 4813.10009765625,
                        4830.39990234375, 4842.60009765625, 4855.7998046875, 4873.60009765625, 4886.39990234375, 4893.2998046875, 4895.7998046875, 4906.5, 4916.2001953125, 4930.89990234375, 4948.89990234375, 4963.89990234375, 4971.10009765625,
                        4976.2998046875, 4993.5, 5000.2001953125, 5004.7998046875, 5006.89990234375, 5007.10009765625, 5028.2998046875, 5032.60009765625, 5034.7998046875, 5042.60009765625, 5044.89990234375, 5051.0, 5053.60009765625,
                        5064.7001953125,
                        5075.7998046875, 5084.39990234375, 5088.10009765625, 5097.89990234375, 5101.10009765625, 5106.0, 5110.2998046875, 5113.89990234375, 5118.7001953125, 5131.2001953125, 5143.39990234375, 5147.2001953125, 5149.5, 5159.5,
                        5161.60009765625, 5164.2998046875, 5179.0, 5190.2998046875, 5205.0, 5217.0, 5219.2998046875, 5228.0, 5243.0, 5251.2001953125, 5258.39990234375, 5266.7998046875, 5271.7998046875, 5286.2001953125, 5293.60009765625, 5302.0,
                        5306.7998046875, 5311.0, 5312.7001953125, 5319.89990234375, 5322.10009765625, 5323.89990234375, 5327.39990234375, 5330.7998046875, 5334.2001953125, 5334.7998046875, 5335.5, 5337.2001953125, 5340.60009765625, 5341.7998046875,
                        5346.10009765625, 5351.89990234375, 5356.0, 5371.5, 5390.0, 5410.2998046875, 5416.2998046875, 5420.39990234375, 5428.7998046875, 5430.89990234375, 5433.10009765625, 5449.7001953125, 5466.5, 5481.0, 5489.39990234375,
                        5493.7001953125, 5513.0, 5530.2001953125, 5545.0, 5560.2998046875, 5574.60009765625, 5577.0, 5584.2998046875, 5588.5, 5592.7001953125, 5596.89990234375, 5604.39990234375, 5606.5, 5611.39990234375, 5613.60009765625,
                        5616.89990234375, 5620.39990234375, 5622.39990234375, 5628.5, 5630.10009765625, 5647.60009765625, 5651.7998046875, 5658.39990234375, 5661.7998046875, 5665.60009765625, 5671.2001953125, 5678.2998046875, 5680.0,
                        5683.7001953125,
                        5685.5, 5691.39990234375, 5692.89990234375, 5694.2001953125, 5698.60009765625, 5704.7001953125, 5717.60009765625, 5720.2998046875, 5728.0, 5741.2998046875, 5744.0, 5747.89990234375, 5749.5, 5757.10009765625, 5762.7001953125,
                        5776.2998046875, 5783.5, 5789.2998046875, 5802.2001953125, 5805.39990234375, 5822.5, 5831.5, 5833.2998046875, 5838.0, 5841.2998046875, 5844.7001953125, 5853.7998046875, 5864.7998046875, 5869.10009765625, 5871.7998046875,
                        5874.2001953125, 5883.0, 5896.39990234375, 5907.0, 5927.7001953125, 5929.2998046875, 5937.60009765625, 5939.7998046875, 5940.5, 5950.39990234375, 5953.10009765625, 5969.2998046875, 5980.5, 5984.0, 5986.10009765625,
                        5990.7001953125, 5992.10009765625, 5993.7001953125, 5999.2001953125, 6000.2998046875, 6012.7998046875, 6024.10009765625, 6027.0, 6028.60009765625, 6028.7998046875, 6029.0};

        double[] time =
                new double[]{0.0, 1.0, 5.0, 6.0, 9.0, 10.0, 13.0, 14.0, 15.0, 19.0, 20.0, 21.0, 23.0, 27.0, 28.0, 36.0, 44.0, 52.0, 60.0, 61.0, 63.0, 66.0, 67.0, 75.0, 81.0, 88.0, 96.0, 97.0, 105.0, 113.0, 115.0, 120.0, 123.0, 126.0, 127.0,
                        129.0, 130.0, 132.0, 137.0, 144.0, 148.0, 152.0, 158.0, 165.0, 167.0, 174.0, 181.0, 187.0, 194.0, 195.0, 199.0, 202.0, 204.0, 206.0, 207.0, 209.0, 211.0, 214.0, 216.0, 219.0, 220.0, 222.0, 223.0, 230.0, 233.0, 234.0, 236.0,
                        243.0, 249.0, 251.0, 253.0, 254.0, 260.0, 267.0, 273.0, 279.0, 284.0, 286.0, 287.0, 288.0, 290.0, 291.0, 292.0, 296.0, 300.0, 301.0, 302.0, 304.0, 305.0, 308.0, 311.0, 312.0, 314.0, 316.0, 318.0, 321.0, 323.0, 326.0, 328.0,
                        332.0, 333.0, 337.0, 338.0, 343.0, 344.0, 347.0, 348.0, 349.0, 350.0, 351.0, 353.0, 355.0, 358.0, 364.0, 367.0, 371.0, 374.0, 376.0, 377.0, 378.0, 381.0, 382.0, 386.0, 391.0, 394.0, 395.0, 397.0, 399.0, 402.0, 404.0, 406.0,
                        407.0, 408.0, 410.0, 413.0, 414.0, 415.0, 418.0, 419.0, 421.0, 428.0, 429.0, 433.0, 439.0, 444.0, 446.0, 447.0, 449.0, 455.0, 457.0, 459.0, 461.0, 463.0, 465.0, 467.0, 469.0, 471.0, 474.0, 475.0, 476.0, 479.0, 480.0, 486.0,
                        488.0, 493.0, 499.0, 502.0, 505.0, 508.0, 510.0, 511.0, 514.0, 519.0, 520.0, 523.0, 525.0, 529.0, 531.0, 536.0, 538.0, 542.0, 543.0, 549.0, 555.0, 561.0, 566.0, 568.0, 573.0, 574.0, 580.0, 588.0, 594.0, 596.0, 601.0, 603.0,
                        611.0, 620.0, 627.0, 631.0, 633.0, 641.0, 648.0, 656.0, 665.0, 675.0, 682.0, 689.0, 690.0, 698.0, 708.0, 716.0, 724.0, 728.0, 733.0, 742.0, 748.0, 750.0, 755.0, 756.0, 764.0, 773.0, 779.0, 786.0, 794.0, 803.0, 804.0, 808.0,
                        811.0, 814.0, 823.0, 825.0, 834.0, 840.0, 842.0, 847.0, 850.0, 854.0, 861.0, 869.0, 876.0, 877.0, 885.0, 887.0, 889.0, 891.0, 893.0, 894.0, 901.0, 902.0, 906.0, 908.0, 911.0, 919.0, 920.0, 923.0, 927.0, 928.0, 931.0, 933.0,
                        936.0, 938.0, 941.0, 944.0, 946.0, 947.0, 954.0, 962.0, 969.0, 972.0, 974.0, 976.0, 979.0, 985.0, 986.0, 987.0, 989.0, 993.0, 1000.0, 1004.0, 1006.0, 1009.0, 1012.0, 1013.0, 1016.0, 1020.0, 1022.0, 1025.0, 1028.0, 1033.0,
                        1035.0, 1040.0, 1047.0, 1051.0, 1053.0, 1059.0, 1066.0, 1074.0, 1081.0, 1089.0, 1092.0, 1094.0, 1095.0, 1104.0, 1107.0, 1109.0, 1117.0, 1125.0, 1126.0, 1128.0, 1130.0, 1132.0, 1134.0, 1142.0, 1146.0, 1148.0, 1149.0, 1151.0,
                        1152.0, 1153.0, 1158.0, 1159.0, 1160.0, 1165.0, 1166.0, 1168.0, 1171.0, 1178.0, 1181.0, 1185.0, 1189.0, 1192.0, 1193.0, 1200.0, 1206.0, 1214.0, 1222.0, 1227.0, 1235.0, 1240.0, 1247.0, 1254.0, 1261.0, 1263.0, 1265.0, 1267.0,
                        1269.0, 1270.0, 1272.0, 1273.0, 1277.0, 1283.0, 1293.0, 1294.0, 1298.0, 1305.0, 1315.0, 1320.0, 1328.0, 1336.0, 1342.0, 1343.0, 1347.0, 1349.0, 1353.0, 1360.0, 1368.0, 1372.0, 1373.0, 1381.0, 1389.0, 1391.0, 1394.0, 1402.0,
                        1403.0, 1412.0, 1418.0, 1424.0, 1430.0, 1431.0, 1438.0, 1447.0, 1456.0, 1463.0, 1469.0, 1476.0, 1482.0, 1483.0, 1487.0, 1489.0, 1496.0, 1502.0, 1509.0, 1516.0, 1522.0, 1530.0, 1532.0, 1536.0, 1537.0, 1538.0, 1541.0, 1546.0,
                        1551.0, 1552.0, 1553.0, 1559.0, 1566.0, 1570.0, 1573.0, 1576.0, 1578.0, 1579.0, 1580.0, 1585.0, 1595.0, 1596.0, 1600.0, 1602.0, 1608.0, 1615.0, 1617.0, 1619.0, 1620.0, 1623.0, 1627.0, 1629.0, 1630.0, 1634.0, 1639.0, 1641.0,
                        1647.0, 1653.0, 1654.0, 1658.0, 1665.0, 1668.0, 1669.0, 1670.0, 1675.0, 1676.0, 1682.0, 1685.0, 1693.0, 1698.0, 1700.0, 1706.0, 1712.0, 1713.0, 1721.0, 1725.0, 1728.0, 1736.0, 1743.0, 1748.0, 1751.0, 1758.0, 1764.0, 1767.0,
                        1776.0, 1784.0, 1791.0, 1801.0, 1809.0, 1810.0, 1814.0, 1816.0, 1818.0, 1824.0, 1830.0, 1836.0, 1841.0, 1843.0, 1849.0, 1855.0, 1857.0, 1859.0, 1863.0, 1865.0, 1871.0, 1872.0, 1877.0, 1880.0, 1886.0, 1895.0, 1901.0, 1902.0,
                        1908.0, 1912.0, 1913.0, 1921.0, 1929.0, 1931.0, 1933.0, 1939.0, 1944.0, 1945.0, 1956.0, 1957.0, 1961.0, 1963.0, 1968.0, 1975.0, 1980.0, 1981.0, 1987.0, 1989.0, 1991.0, 1992.0, 1997.0, 1999.0, 2001.0, 2003.0, 2009.0, 2012.0,
                        2014.0, 2017.0, 2018.0, 2021.0, 2023.0, 2027.0, 2028.0, 2029.0, 2031.0, 2037.0, 2038.0, 2047.0, 2052.0, 2057.0, 2060.0, 2068.0, 2075.0, 2081.0, 2087.0, 2097.0, 2104.0, 2105.0, 2108.0, 2110.0, 2111.0, 2114.0, 2115.0, 2122.0,
                        2123.0, 2126.0, 2127.0, 2133.0, 2134.0, 2137.0, 2138.0, 2141.0, 2144.0, 2149.0, 2151.0, 2152.0, 2158.0, 2160.0, 2163.0, 2166.0, 2168.0, 2176.0, 2183.0, 2185.0, 2189.0, 2190.0, 2193.0, 2197.0, 2198.0, 2202.0, 2203.0, 2204.0,
                        2207.0, 2208.0, 2212.0, 2213.0, 2216.0, 2219.0, 2221.0, 2228.0, 2231.0, 2234.0, 2235.0, 2240.0, 2242.0, 2245.0, 2246.0, 2251.0, 2253.0, 2255.0, 2256.0, 2259.0, 2261.0, 2264.0, 2265.0, 2267.0, 2269.0, 2272.0, 2276.0, 2279.0,
                        2283.0, 2291.0, 2292.0, 2295.0, 2296.0, 2298.0, 2300.0, 2304.0, 2307.0, 2309.0, 2314.0, 2315.0, 2317.0, 2321.0, 2324.0, 2326.0, 2327.0, 2330.0, 2335.0, 2339.0, 2341.0, 2347.0, 2348.0, 2350.0, 2352.0, 2353.0, 2355.0, 2357.0,
                        2359.0, 2362.0, 2366.0, 2368.0, 2371.0, 2372.0, 2378.0, 2380.0, 2383.0, 2386.0, 2388.0, 2391.0, 2394.0, 2398.0, 2400.0, 2402.0, 2403.0, 2410.0, 2414.0, 2416.0, 2423.0, 2424.0, 2429.0, 2430.0, 2438.0, 2444.0, 2451.0, 2459.0,
                        2465.0, 2468.0, 2469.0, 2473.0, 2478.0, 2484.0, 2492.0, 2500.0, 2504.0, 2507.0, 2514.0, 2517.0, 2519.0, 2520.0, 2521.0, 2533.0, 2535.0, 2536.0, 2541.0, 2543.0, 2547.0, 2548.0, 2553.0, 2561.0, 2566.0, 2568.0, 2574.0, 2576.0,
                        2578.0, 2580.0, 2582.0, 2584.0, 2589.0, 2594.0, 2596.0, 2597.0, 2601.0, 2602.0, 2603.0, 2607.0, 2612.0, 2618.0, 2623.0, 2624.0, 2628.0, 2634.0, 2638.0, 2641.0, 2645.0, 2647.0, 2653.0, 2656.0, 2659.0, 2661.0, 2663.0, 2664.0,
                        2668.0, 2669.0, 2670.0, 2672.0, 2674.0, 2677.0, 2678.0, 2679.0, 2681.0, 2684.0, 2685.0, 2687.0, 2689.0, 2690.0, 2696.0, 2704.0, 2710.0, 2712.0, 2714.0, 2718.0, 2719.0, 2720.0, 2728.0, 2735.0, 2741.0, 2745.0, 2747.0, 2754.0,
                        2761.0, 2767.0, 2773.0, 2779.0, 2780.0, 2784.0, 2786.0, 2788.0, 2790.0, 2793.0, 2794.0, 2796.0, 2797.0, 2799.0, 2801.0, 2802.0, 2805.0, 2806.0, 2813.0, 2815.0, 2818.0, 2820.0, 2822.0, 2824.0, 2827.0, 2828.0, 2830.0, 2831.0,
                        2835.0, 2836.0, 2837.0, 2840.0, 2843.0, 2849.0, 2850.0, 2853.0, 2857.0, 2858.0, 2860.0, 2861.0, 2866.0, 2868.0, 2874.0, 2877.0, 2879.0, 2884.0, 2885.0, 2890.0, 2893.0, 2894.0, 2897.0, 2899.0, 2902.0, 2908.0, 2913.0, 2915.0,
                        2918.0, 2919.0, 2922.0, 2927.0, 2930.0, 2937.0, 2938.0, 2943.0, 2945.0, 2946.0, 2951.0, 2952.0, 2956.0, 2960.0, 2962.0, 2965.0, 2970.0, 2971.0, 2972.0, 2976.0, 2977.0, 2981.0, 2985.0, 2987.0, 2989.0, 2991.0, 2993.0};

        List<Double> timeList = Arrays.stream(time).boxed().toList();

        List<Double> distance = Arrays.stream(dist).boxed().toList();

        ActivityStream as = activityProcessingService.createActivityStream(
                timeList,
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
