package com.smartroute.smartroute1.datagenerator;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.StravaAccount;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.repository.StravaAccountRepository;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.List;

@Profile("generateData")
@DependsOn("userDataGenerator")
@Component
@AllArgsConstructor
public class StravaDataGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final int NUMBER_OF_ACTIVITIES_PER_USER = 3;

    private final StravaAccountRepository stravaAccountRepository;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;

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
                acc.setAccessToken("DummyAccessToken" + user.getId());
                acc.setRefreshToken("DummyRefreshToken" + user.getId());
                acc.setExpiresAt(Instant.now().plusSeconds(300));
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
            for (ApplicationUser user : userList) {
                LOGGER.info("generating {} Strava activities for user {}", NUMBER_OF_ACTIVITIES_PER_USER, user.getEmail());

                for (int i = 0; i < NUMBER_OF_ACTIVITIES_PER_USER; i++) {
                    Activity sa = new Activity();
                    sa.setStravaId(id++);
                    sa.setName("Activity " + i);

                    float distance = (float) (1 + Math.random() * 25) * 1000; // 1-26 km in m
                    float avgSpeed = (float) (1000 / ((2.5 + Math.random() * 7.5) * 60)); // 2:30-10:00 min/km converted to m/s
                    int movingTime = (int) (distance / avgSpeed); // 2min30-4h20min in seconds
                    float maxSpeed = (float) Math.min(avgSpeed * 1.25, 1000 / (6 + Math.random() * 24 * 60)); // 7.5-30 km/h
                    sa.setDistance(distance);
                    sa.setAverageSpeed(avgSpeed);
                    sa.setMovingTime(movingTime);
                    sa.setMaxSpeed(maxSpeed);

                    sa.setElapsedTime(sa.getMovingTime() + (int) (Math.random() * 600)); // moving + 0-10 min
                    sa.setTotalElevationGain((float) (Math.random() * .1 * distance)); // 0-100 m/km
                    sa.setType("Run");
                    sa.setSportType("Run");
                    sa.setStartDate(Instant.now().minusSeconds((long) (Math.random() * 30 * 24 * 3600))); // last 30 days
                    sa.setStartDateLocal(Instant.now().minusSeconds((long) (Math.random() * 30 * 24 * 3600)));


                    float averageHeartrate = (float) (120 + Math.random() * 60);
                    sa.setAverageHeartrate(averageHeartrate); // 120-180 bpm
                    float maxHeartrate = (float) Math.min(averageHeartrate * 1.1, (140 + Math.random() * 60));
                    sa.setMaxHeartrate(maxHeartrate);
                    float averageWatts = (float) (120 + Math.random() * 230);
                    sa.setAverageWatts(averageWatts);
                    sa.setKilojoules(averageWatts * movingTime / 1000);
                    sa.setSufferScore(2 * (int) (averageHeartrate * ((float) movingTime / 60)));
                    sa.setSummaryPolyline("}_ilHq`mi@o@e@}@eAe@k@a@m@iBoBqBuC}CeBm@o@gA{@wA_AwA_@}Ai@yAi@qBkA{DqBiDkBuCgAiBoA}BiAkCg@cBe@eBcAoDkCiEyCaGeEuDiBuCq@kB{@}Bi@kAw@iAe@wA]yB]gDQcEQkGKkEE}B@eBHoBT{BJ}HHaFBqC");
                    sa.setUser(user);

                    activityRepository.save(sa);
                }
            }

        }


    }

}
