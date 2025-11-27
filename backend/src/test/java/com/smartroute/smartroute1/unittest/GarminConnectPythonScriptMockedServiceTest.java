package com.smartroute.smartroute1.unittest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.endpoint.dto.GarminConnectAccountDto;
import com.smartroute.smartroute1.endpoint.dto.StravaStreamDto;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.AthleteZone;
import com.smartroute.smartroute1.entity.GarminAccount;
import com.smartroute.smartroute1.exception.garmin.GarminAuthenticationException;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.AthleteZoneRepository;
import com.smartroute.smartroute1.repository.GarminAccountRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.FitnessScoreService;
import com.smartroute.smartroute1.service.GarminImportService;
import jakarta.transaction.Transactional;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles({"test", "generateData"})
@Transactional
public class GarminConnectPythonScriptMockedServiceTest {

    @Autowired
    private GarminAccountRepository garminAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GarminImportService  garminImportService;

    @Test
    void syncActivities_withExpiredRefreshTokenAndNoCredentials_throwsGarminAuthenticationException() throws Exception {
        ApplicationUser user = userRepository.findAll().getFirst(); // Java 21
        garminImportService.syncActivities(user, 1, "test@email.com", "myGarminPassword");
        GarminAccount account = garminAccountRepository.findByUser(user);
        assertNotNull(account);

        long now = Instant.now().getEpochSecond();
        String expiredTokenJson = getString(now);

        account.setTokenJson(expiredTokenJson);
        garminAccountRepository.save(account);

        assertThrows(
                GarminAuthenticationException.class,
                () -> garminImportService.syncActivities(user, 1, null, null)
        );
    }

    @NotNull
    private static String getString(long now) {
        long expiredTs = now - 10;

        return """
        {
          "oauth2_token.json": {
            "scope": "DUMMY_SCOPE",
            "jti": "dummy-jti",
            "token_type": "bearer",
            "access_token": "dummy-token",
            "expires_in": 99999,
            "expires_at": %d,
            "refresh_token_expires_in": 2591999,
            "refresh_token_expires_at": %d
          },
          "oauth1_token.json": {
            "oauth_token": "dummy-token",
            "oauth_token_secret": "dummy_auth",
            "mfa_token": null,
            "mfa_expiration_timestamp": null,
            "domain": "garmin.com"
          },
          "refresh_token_expires_at": %d,
          "expires_at": %d
        }
        """.formatted(expiredTs, expiredTs, expiredTs, expiredTs);
    }

}
