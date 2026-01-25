package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.StravaAccount;
import com.smartroute.smartroute1.exception.garmin.GarminException;
import com.smartroute.smartroute1.repository.StravaAccountRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.ActivityService;
import com.smartroute.smartroute1.service.GarminImportService;
import com.smartroute.smartroute1.service.StravaService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@AllArgsConstructor
@Service
@Slf4j
public class ActivityServiceImpl implements ActivityService {
    private StravaService stravaService;
    private GarminImportService garminService;
    private UserRepository userRepository;
    private StravaAccountRepository stravaAccountRepository;

    @Override
    public void synchronize(String email, int count) throws Exception {
        ApplicationUser user = userRepository.findUserByEmail(email);

        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        Exception stravaException = null;
        Exception garminException = null;

        StravaAccount account = stravaAccountRepository.findByUser(user).orElse(null);
        if (account != null) {
            try {
                stravaService.importStravaActivities(email, count);
                log.info("Successfully synced Strava activities for user {}", email);
            } catch (Exception e) {
                stravaException = e;
                log.error("Failed to sync Strava activities for user {}: {}", email, e.getMessage(), e);
            }
        }

        if (garminService.isGarminConnected(email)) {
            try {
                garminService.syncActivities(user, count, null, null);
                log.info("Successfully synced Garmin activities for user {}", email);
            } catch (GarminException e) {
                garminException = e;
                log.error("Failed to sync Garmin activities for user {}: {}", email, e.getMessage(), e);
            }
        }

        if (stravaException != null && garminException != null) {
            throw new RuntimeException("Strava sync failed: " + stravaException.getMessage() + "; Garmin sync failed: " + garminException.getMessage());
        } else if (stravaException != null) {
            throw stravaException;
        } else if (garminException != null) {
            throw garminException;
        }

        log.info("Successfully synced {} activities for user {}", count, email);
    }
}
