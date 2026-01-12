package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.service.FitnessScoreService;
import com.smartroute.smartroute1.service.ActivityProcessingService;
import jakarta.transaction.Transactional;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;

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
        reset(fitnessScoreService);
    }

    @Test
    void testFetchHeartRateDataForActivities_setsSessionLoad() {
        activityRepository.deleteAll();
        activityRepository.flush();
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

        activityProcessingService.fetchHeartRateDataForActivities(1, activities, "token");

        List<Activity> activitiesWithSessionLoad = activityRepository.findByUser(user);

        assertAll(
                () -> assertEquals(2, activitiesWithSessionLoad.size()),
                () -> assertEquals(123, activitiesWithSessionLoad.get(0).getSessionLoad()),
                () -> assertEquals(123, activitiesWithSessionLoad.get(1).getSessionLoad())
        );
    }

    @Test
    void testFetchHeartRateDataForActivities_withStravaSufferScore_setsSessionLoad() {
        activityRepository.deleteAll();
        activityRepository.flush();
        ApplicationUser user = userRepository.findAll().getFirst();
        Activity act1 = getStravaActivity();
        act1.setUser(user);
        act1.setStravaId(1L);
        act1.setName("TestActivity1");
        act1.setSufferScore(123);
        Activity act2 = getStravaActivity();
        act2.setUser(user);
        act2.setName("TestActivity2");
        act2.setStravaId(2L);
        act2.setSufferScore(456);

        List<Activity> activities = List.of(
            act1,
            act2
        );

        activityRepository.saveAll(activities);

        when(fitnessScoreService.calculateSessionLoad(anyInt(), anyFloat()))
            .thenReturn(129).thenReturn(459);

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        activityProcessingService.fetchHeartRateDataForActivities(1, activities, "token");

        List<Activity> activitiesWithSessionLoad = activityRepository.findByUser(user);

        assertAll(
            () -> assertEquals(2, activitiesWithSessionLoad.size()),
            () -> assertEquals(129, activitiesWithSessionLoad.get(0).getSessionLoad()),
            () -> assertEquals(459, activitiesWithSessionLoad.get(1).getSessionLoad())
        );

        verify(fitnessScoreService, times(2)).calculateSessionLoad(anyInt(), anyFloat());
    }

    @Test
    void testFetchHeartRateDataForActivities_withPowerBasedMethod_setsSessionLoad() {
        activityRepository.deleteAll();
        activityRepository.flush();
        ApplicationUser user = userRepository.findAll().getFirst();
        user.setFtp(150);
        userRepository.save(user);
        Activity act1 = getStravaActivity();
        act1.setUser(user);
        act1.setStravaId(null);
        act1.setName("TestActivity1");
        act1.setAverageWatts(150f);

        List<Activity> activities = List.of(
            act1
        );

        activityRepository.saveAll(activities);

        when(fitnessScoreService.calculateSessionLoad(anyInt(), anyInt(), anyFloat(), anyFloat()))
            .thenReturn(123);

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        activityProcessingService.fetchHeartRateDataForActivities(1, activities, "token");

        List<Activity> activitiesWithSessionLoad = activityRepository.findByUser(user);

        assertAll(
            () -> assertEquals(1, activitiesWithSessionLoad.size()),
            () -> assertEquals(123, activitiesWithSessionLoad.get(0).getSessionLoad())
        );

        verify(fitnessScoreService, times(1)).calculateSessionLoad(anyInt(), anyInt(), anyFloat(), anyFloat());
    }

    @Test
    void testFetchHeartRateDataForActivities_withEnergyBasedMethod_setsSessionLoad() {
        activityRepository.deleteAll();
        activityRepository.flush();
        ApplicationUser user = userRepository.findAll().getFirst();
        user.setFtp(150);
        userRepository.save(user);
        Activity act1 = getStravaActivity();
        act1.setUser(user);
        act1.setStravaId(null);
        act1.setName("TestActivity1");
        act1.setKilojoules(150f);

        List<Activity> activities = List.of(
            act1
        );

        activityRepository.saveAll(activities);

        when(fitnessScoreService.calculateSessionLoad(anyFloat(), anyFloat(), anyFloat()))
            .thenReturn(123);

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        activityProcessingService.fetchHeartRateDataForActivities(1, activities, "token");

        List<Activity> activitiesWithSessionLoad = activityRepository.findByUser(user);

        assertAll(
            () -> assertEquals(1, activitiesWithSessionLoad.size()),
            () -> assertEquals(123, activitiesWithSessionLoad.get(0).getSessionLoad())
        );

        verify(fitnessScoreService, times(1)).calculateSessionLoad(anyFloat(), anyFloat(), anyFloat());
    }

    @Test
    void testFetchHeartRateDataForActivities_withFallbackMethod_setsSessionLoad() {
        activityRepository.deleteAll();
        activityRepository.flush();
        ApplicationUser user = userRepository.findAll().getFirst();
        Activity act1 = getStravaActivity();
        act1.setUser(user);
        act1.setStravaId(null);
        act1.setName("TestActivity1");

        List<Activity> activities = List.of(
            act1
        );

        activityRepository.saveAll(activities);

        when(fitnessScoreService.calculateSessionLoad(anyFloat(), anyInt(), anyFloat()))
            .thenReturn(123);

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        activityProcessingService.fetchHeartRateDataForActivities(1, activities, "token");

        List<Activity> activitiesWithSessionLoad = activityRepository.findByUser(user);

        assertAll(
            () -> assertEquals(1, activitiesWithSessionLoad.size()),
            () -> assertEquals(123, activitiesWithSessionLoad.get(0).getSessionLoad())
        );

        verify(fitnessScoreService, times(1)).calculateSessionLoad(anyFloat(), anyInt(), anyFloat());
    }

    @Test
    void testFetchHeartRateDataForActivities_schedulesTasksCorrectly() {
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

        activityProcessingService.fetchHeartRateDataForActivities(1, activities, "token");


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
