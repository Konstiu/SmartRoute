package com.smartroute.smartroute1.datagenerator;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.StravaAccount;
import com.smartroute.smartroute1.entity.StravaActivity;
import com.smartroute.smartroute1.repository.StravaAccountRepository;
import com.smartroute.smartroute1.repository.StravaActivityRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.List;

@Profile("generateData")
@Component
@AllArgsConstructor
public class StravaDataGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final int NUMBER_OF_ACTIVITIES_PER_USER = 3;

    private final StravaAccountRepository stravaAccountRepository;
    private final UserRepository userRepository;
    private final StravaActivityRepository activityRepository;

    @PostConstruct
    public void generateAccounts() {
        List<ApplicationUser> userList = userRepository.findAll();
        if (!stravaAccountRepository.findAll().isEmpty()) {
            LOGGER.info("Accounts already generated");
        } else {
            LOGGER.info("generating {} Strava account entries", userList.size());
            long id = 0;
            for (ApplicationUser user : userList) {
                StravaAccount acc = new StravaAccount();
                acc.setUser(user);
                acc.setScopes("read,activity:read_all,profile:read_all");
                acc.setId(id);
                acc.setConnectedAt(Instant.now());
                acc.setAthleteId(id);
                acc.setAccessToken("DummyAccessToken" + id);
                acc.setRefreshToken("DummyRefreshToken" + id);
                acc.setExpiresAt(Instant.now().plusSeconds(300));
                stravaAccountRepository.save(acc);
                LOGGER.info("saving account for user {} ", user.getId());


            }
        }

        generateActivities();
    }

    private void generateActivities() {
        List<StravaAccount> stravaAccountList = stravaAccountRepository.findAll();

        if (!activityRepository.findAll().isEmpty()) {
            LOGGER.info("Activities already generated");
        } else {
            long id = 0;
            for (StravaAccount acc : stravaAccountList) {
                LOGGER.info("generating {} Strava activities for user {}", NUMBER_OF_ACTIVITIES_PER_USER, acc.getUser().getEmail());

                for (int i = 0; i < NUMBER_OF_ACTIVITIES_PER_USER; i++) {
                    StravaActivity sa = new StravaActivity();
                    sa.setId(id++);
                    sa.setName("Activity " + i);
                    sa.setDistance((float) (1 + Math.random() * 25)); // 1-26 km
                    sa.setMovingTime((int) (600 + Math.random() * 7200)); // 10min-2h
                    sa.setElapsedTime(sa.getMovingTime() + (int) (Math.random() * 6000)); // moving + 0-10 min
                    sa.setTotalElevationGain((float) (0 + Math.random() * 250)); // 0-250 m
                    sa.setType("Run");
                    sa.setSportType("Run");
                    sa.setStartDate(Instant.now().minusSeconds((long) (Math.random() * 30 * 24 * 3600)).toString()); // last 30 days
                    sa.setStartDateLocal(Instant.now().minusSeconds((long) (Math.random() * 30 * 24 * 3600)).toString());
                    sa.setAverageSpeed((float) (5 + Math.random() * 10)); // 5-15 km/h
                    sa.setMaxSpeed((float) (sa.getAverageSpeed() + Math.random() * 5));
                    sa.setAverageHeartrate((float) (120 + Math.random() * 60)); // 120-180 bpm
                    sa.setMaxHeartrate((float) (160 + Math.random() * 40)); // 160-200 bpm
                    sa.setAverageWatts((float) (100 + Math.random() * 200));
                    sa.setKilojoules(sa.getAverageWatts() * sa.getMovingTime() / 1000);
                    sa.setKudosCount((int) (Math.random() * 50)); // 0-50
                    sa.setStravaAccount(acc);

                    activityRepository.save(sa);
                }
            }

        }


    }

}
