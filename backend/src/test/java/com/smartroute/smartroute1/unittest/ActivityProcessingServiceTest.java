package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.service.FitnessScoreService;
import com.smartroute.smartroute1.service.ActivityProcessingService;
import jakarta.transaction.Transactional;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ActiveProfiles;

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
public class ActivityProcessingServiceTest extends BaseTest {
    @MockBean
    private FitnessScoreService fitnessScoreService;
    @MockBean
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
}
