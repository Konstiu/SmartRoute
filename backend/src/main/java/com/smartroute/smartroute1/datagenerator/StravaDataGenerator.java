package com.smartroute.smartroute1.datagenerator;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.StravaAccount;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.repository.StravaAccountRepository;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.FitnessScoreService;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Profile("generateData")
@DependsOn("userDataGenerator")
@Component
@AllArgsConstructor
public class StravaDataGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final int NUMBER_OF_ACTIVITIES_PER_USER = 10;

    private final StravaAccountRepository stravaAccountRepository;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final FitnessScoreService fitnessScoreService;

    @PostConstruct
    public void generateAccounts() {
        List<ApplicationUser> userList = userRepository.findAll();
        if (!stravaAccountRepository.findAll().isEmpty()) {
            LOGGER.info("Accounts already generated");
        } else {
            LOGGER.info("generating {} Strava account entries", userList.size());

            for (ApplicationUser user : userList) {
                StravaAccount acc = new StravaAccount();
                acc.setUser(user);
                acc.setScopes("read,activity:read_all,profile:read_all");
                acc.setConnectedAt(Instant.now());
                acc.setAthleteId(user.getId());
                //acc.setAccessToken("DummyAccessToken" + user.getId());
                //acc.setRefreshToken("DummyRefreshToken" + user.getId());
                //acc.setExpiresAt(Instant.now().plusSeconds(300));
                stravaAccountRepository.save(acc);
                LOGGER.debug("saving account for user {} ", user.getId());
            }
        }

        generateActivities();
    }


    private void generateActivities() {
        List<ApplicationUser> userList = userRepository.findAll();
        if (!activityRepository.findAll().isEmpty()) {
            LOGGER.info("Activities already generated");
        } else {
            long id = 0;
            for (int i = 0; i < userList.size(); i++) {
                switch (i) {
                    case 1 -> {
                        // User 1: Beginner - irregular 0-3x per week, 2-6 km, moderate-slow pace
                        generateBeginnerActivities(userList.get(i));
                    }
                    case 2 -> {
                        // User 2: Advanced - 1-3x per week, 5-15 km, moderate pace
                        generateAdvancedActivities(userList.get(i));
                    }
                    case 3 -> {
                        // User 3: Pro - 3-5x per week, 8-28 km, moderate-high pace
                        generateProActivities(userList.get(i));
                    }
                    default -> {
                        // Random activities for other users
                        for (int j = 0; j < NUMBER_OF_ACTIVITIES_PER_USER; j++) {
                            Activity sa = new Activity();
                            sa.setName("Activity " + j);
                            float distance = (float) (1 + Math.random() * 25) * 1000;
                            float avgSpeed = (float) (1000 / ((2.5 + Math.random() * 7.5) * 60));
                            int movingTime = (int) (distance / avgSpeed);
                            float maxSpeed = (float) Math.min(avgSpeed * 1.25, 1000 / (6 + Math.random() * 24 * 60));
                            float totalElevationGain = (float) (Math.random() * .1 * distance);

                            sa.setDistance(distance);
                            sa.setAverageSpeed(avgSpeed);
                            sa.setMovingTime(movingTime);
                            sa.setMaxSpeed(maxSpeed);
                            sa.setElapsedTime(sa.getMovingTime() + (int) (Math.random() * 600));
                            sa.setTotalElevationGain(totalElevationGain);
                            sa.setType("Run");
                            sa.setSportType("Run");
                            sa.setStartDate(Instant.now().minusSeconds((long) (Math.random() * 30 * 24 * 3600)));
                            sa.setStartDateLocal(Instant.now().minusSeconds((long) (Math.random() * 30 * 24 * 3600)));

                            float averageHeartrate = (float) (120 + Math.random() * 60);
                            sa.setAverageHeartrate(averageHeartrate);
                            float maxHeartrate = (float) Math.min(averageHeartrate * 1.1, (140 + Math.random() * 60));
                            sa.setMaxHeartrate(maxHeartrate);

                            float averageWatts = (float) (120 + Math.random() * 230);
                            sa.setAverageWatts(averageWatts);
                            sa.setKilojoules(averageWatts * movingTime / 1000);
                            sa.setSummaryPolyline("}_ilHqmi@o@e@}@eAe@k@a@m@iBoBqBuC}CeBm@o@gA{@wA_AwA_@}Ai@yAi@qBkA{DqBiDkBuCgAiBoA}BiAkCg@cBe@eBcAoDkCiEyCaGeEuDiBuCq@kB{@}Bi@kAw@iAe@wA]yB]gDQcEQkGKkEE}B@eBHoBT{BJ}HHaFBqC");
                            sa.setUser(userList.get(i));

                            Integer sessionLoad = fitnessScoreService.calculateSessionLoad(
                                    distance / 1000,
                                    movingTime / 60,
                                    totalElevationGain
                            );
                            sa.setSessionLoad(sessionLoad);
                            activityRepository.save(sa);
                        }
                    }
                }
            }

            for (ApplicationUser user : userList) {
                LOGGER.debug("generating activities for user {}", user.getEmail());
            }
        }
    }

    private void generateBeginnerActivities(ApplicationUser user) {
        // Beginner: 18 activities over 2 months, irregular (0-3x/week)
        // Days when running occurred: [3, 7, 14, 16, 21, 28, 30, 35, 42, 44, 49, 51, 56, 58]
        int[] daysAgo = {3, 7, 14, 16, 21, 28, 30, 35, 42, 44, 49, 51, 56, 58};
        float[] distances = {2.5f, 3.2f, 2.8f, 4.1f, 3.5f, 2.3f, 5.2f, 3.8f, 4.5f, 3.1f, 5.8f, 4.2f, 3.6f, 6.0f};
        int[] sessionLoads = {25, 32, 28, 42, 35, 23, 55, 38, 47, 31, 62, 44, 36, 68}; // TRIMP-like values
        String[] names = {"Morning Jog", "Easy Run", "Park Run", "Evening Run", "Recovery Run",
                "Short Run", "Weekend Run", "Leisure Run", "Easy Pace", "Slow Run",
                "Comfortable Run", "Sunday Run", "Light Jog", "Relaxed Run"};

        for (int i = 0; i < daysAgo.length; i++) {
            Activity activity = new Activity();
            activity.setName(names[i]);

            float distance = distances[i] * 1000; // km to meters
            float avgSpeed = (float) (1000 / ((5.5 + Math.random() * 1.5) * 60)); // 5:30-7:00 min/km
            int movingTime = (int) (distance / avgSpeed);
            float maxSpeed = avgSpeed * 1.15f;
            float totalElevationGain = (float) (Math.random() * 30 + 10); // 10-40m

            activity.setDistance(distance);
            activity.setAverageSpeed(avgSpeed);
            activity.setMovingTime(movingTime);
            activity.setMaxSpeed(maxSpeed);
            activity.setElapsedTime(movingTime + (int) (Math.random() * 300 + 60));
            activity.setTotalElevationGain(totalElevationGain);
            activity.setType("Run");
            activity.setSportType("Run");

            long secondsAgo = (long) daysAgo[i] * 24 * 3600;
            activity.setStartDate(Instant.now().minusSeconds(secondsAgo));
            activity.setStartDateLocal(Instant.now().minusSeconds(secondsAgo));

            float averageHeartrate = (float) (145 + Math.random() * 15); // 145-160 bpm
            activity.setAverageHeartrate(averageHeartrate);
            activity.setMaxHeartrate(averageHeartrate * 1.12f);

            float averageWatts = (float) (150 + Math.random() * 40);
            activity.setAverageWatts(averageWatts);
            activity.setKilojoules(averageWatts * movingTime / 1000);
            activity.setSummaryPolyline("}_ilHqmi@o@e@}@eAe@k@a@m@iBoBqBuC}CeBm@o@gA{@wA_AwA");
            activity.setUser(user);
            activity.setSessionLoad(sessionLoads[i]);
            activityRepository.save(activity);
        }
    }

    private void generateAdvancedActivities(ApplicationUser user) {
        // Advanced: 20 activities over 2 months (1-3x/week)
        int[] daysAgo = {1, 4, 7, 10, 14, 17, 21, 24, 28, 31, 35, 38, 42, 45, 49, 52, 56, 59};
        float[] distances = {7.2f, 10.5f, 6.8f, 12.3f, 8.5f, 10.0f, 14.2f, 9.1f, 11.8f,
                7.5f, 13.5f, 8.8f, 10.2f, 12.0f, 9.5f, 11.0f, 14.8f, 10.5f};
        int[] sessionLoads = {85, 125, 78, 148, 102, 118, 172, 108, 142,
                88, 162, 105, 122, 145, 112, 132, 180, 125}; // TRIMP-like values
        String[] names = {"Tempo Run", "Long Run", "Recovery Run", "Steady Pace", "Morning Run",
                "Hill Workout", "Weekend Long", "Easy Run", "Interval Training",
                "Base Run", "Endurance Run", "Recovery Jog", "Steady Run", "Long Distance",
                "Moderate Run", "Training Run", "Extended Run", "Cardio Session"};

        for (int i = 0; i < daysAgo.length; i++) {
            Activity activity = new Activity();
            activity.setName(names[i]);

            float distance = distances[i] * 1000;
            float avgSpeed = (float) (1000 / ((4.5 + Math.random() * 1.0) * 60)); // 4:30-5:30 min/km
            int movingTime = (int) (distance / avgSpeed);
            float maxSpeed = avgSpeed * 1.20f;
            float totalElevationGain = (float) (Math.random() * 50 + 30); // 30-80m

            activity.setDistance(distance);
            activity.setAverageSpeed(avgSpeed);
            activity.setMovingTime(movingTime);
            activity.setMaxSpeed(maxSpeed);
            activity.setElapsedTime(movingTime + (int) (Math.random() * 400 + 120));
            activity.setTotalElevationGain(totalElevationGain);
            activity.setType("Run");
            activity.setSportType("Run");

            long secondsAgo = (long) daysAgo[i] * 24 * 3600;
            activity.setStartDate(Instant.now().minusSeconds(secondsAgo));
            activity.setStartDateLocal(Instant.now().minusSeconds(secondsAgo));

            float averageHeartrate = (float) (150 + Math.random() * 15); // 150-165 bpm
            activity.setAverageHeartrate(averageHeartrate);
            activity.setMaxHeartrate(averageHeartrate * 1.10f);

            float averageWatts = (float) (180 + Math.random() * 50);
            activity.setAverageWatts(averageWatts);
            activity.setKilojoules(averageWatts * movingTime / 1000);
            activity.setSummaryPolyline("}_ilHqmi@o@e@}@eAe@k@a@m@iBoBqBuC}CeBm@o@gA{@wA_AwA_@}Ai@yAi@qBkA{DqB");
            activity.setUser(user);
            activity.setSessionLoad(sessionLoads[i]);
            activityRepository.save(activity);
        }
    }

    private void generateProActivities(ApplicationUser user) {
        // Pro: 25 activities over 2 months (3-5x/week)
        int[] daysAgo = {1, 3, 5, 8, 10, 12, 15, 17, 19, 22, 24, 26, 29, 31, 33, 36, 38, 40, 43, 45, 47, 50, 52, 54, 57};
        float[] distances = {12.5f, 18.2f, 10.0f, 22.5f, 15.3f, 20.0f, 25.8f, 12.0f, 16.5f,
                14.2f, 21.0f, 11.5f, 27.5f, 13.8f, 19.5f, 16.0f, 23.2f, 14.5f,
                20.5f, 17.8f, 24.0f, 15.5f, 21.8f, 18.5f, 26.5f};
        int[] sessionLoads = {152, 225, 118, 278, 188, 245, 320, 145, 202,
                172, 258, 138, 342, 168, 238, 195, 288, 178,
                252, 218, 298, 190, 268, 228, 328}; // TRIMP-like values for pro athletes
        String[] names = {"Speed Workout", "Long Run", "Recovery Run", "Race Pace", "Tempo Run",
                "Endurance", "Marathon Prep", "Easy Miles", "Interval Session", "Base Building",
                "Half Marathon Pace", "Active Recovery", "Long Distance", "Hill Repeats", "Steady State",
                "Aerobic Run", "Threshold Run", "Easy Run", "Progressive Run", "Fartlek",
                "Distance Run", "Tempo Session", "Endurance Training", "Quality Run", "Volume Run"};

        for (int i = 0; i < daysAgo.length; i++) {
            Activity activity = new Activity();
            activity.setName(names[i]);

            float distance = distances[i] * 1000;
            float avgSpeed = (float) (1000 / ((3.5 + Math.random() * 1.0) * 60)); // 3:30-4:30 min/km
            int movingTime = (int) (distance / avgSpeed);
            float maxSpeed = avgSpeed * 1.25f;
            float totalElevationGain = (float) (Math.random() * 80 + 40); // 40-120m

            activity.setDistance(distance);
            activity.setAverageSpeed(avgSpeed);
            activity.setMovingTime(movingTime);
            activity.setMaxSpeed(maxSpeed);
            activity.setElapsedTime(movingTime + (int) (Math.random() * 500 + 180));
            activity.setTotalElevationGain(totalElevationGain);
            activity.setType("Run");
            activity.setSportType("Run");

            long secondsAgo = (long) daysAgo[i] * 24 * 3600;
            activity.setStartDate(Instant.now().minusSeconds(secondsAgo));
            activity.setStartDateLocal(Instant.now().minusSeconds(secondsAgo));

            float averageHeartrate = (float) (155 + Math.random() * 15); // 155-170 bpm
            activity.setAverageHeartrate(averageHeartrate);
            activity.setMaxHeartrate(averageHeartrate * 1.08f);

            float averageWatts = (float) (220 + Math.random() * 60);
            activity.setAverageWatts(averageWatts);
            activity.setKilojoules(averageWatts * movingTime / 1000);
            activity.setSummaryPolyline("}_ilHqmi@o@e@}@eAe@k@a@m@iBoBqBuC}CeBm@o@gA{@wA_AwA_@}Ai@yAi@qBkA{DqBiDkBuCgAiBoA");
            activity.setUser(user);
            activity.setSessionLoad(sessionLoads[i]);
            activityRepository.save(activity);
        }
    }
}
