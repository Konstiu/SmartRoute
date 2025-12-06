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
import com.smartroute.smartroute1.exception.garmin.GarminException;
import com.smartroute.smartroute1.exception.garmin.GarminScriptException;
import com.smartroute.smartroute1.exception.garmin.GarminNoDataException;
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
import org.springframework.beans.FatalBeanException;
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
public class GarminConnectPythonScriptMockedServiceTest extends BaseTest {

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

    @Test
    void isGarminConnected_withValidToken_returnsTrue() throws Exception {
        ApplicationUser user = userRepository.findAll().getFirst();
        // sync once to create account and token
        garminImportService.syncActivities(user, 1, "test@email.com", "myGarminPassword");

        boolean connected = garminImportService.isGarminConnected(user.getEmail());
        assertTrue(connected, "User should be reported as connected when a valid token exists");
    }

    @Test
    void isGarminConnected_withoutToken_returnsFalse() throws Exception {
        ApplicationUser user = userRepository.findAll().getFirst();

        // remove any existing token
        GarminAccount account = garminAccountRepository.findByUser(user);
        if (account != null) {
            account.setTokenJson(null);
            garminAccountRepository.save(account);
        }

        boolean connected = garminImportService.isGarminConnected(user.getEmail());
        assertFalse(connected, "User without token should not be reported as connected");
    }

    @Test
    void isGarminConnected_withExpiredToken_returnsFalse() throws Exception {
        ApplicationUser user = userRepository.findAll().getFirst();
        // sync first to create account and token
        garminImportService.syncActivities(user, 1, "test@email.com", "myGarminPassword");
        GarminAccount account = garminAccountRepository.findByUser(user);
        assertNotNull(account);

        long now = Instant.now().getEpochSecond();
        String expiredTokenJson = getString(now);

        account.setTokenJson(expiredTokenJson);
        garminAccountRepository.save(account);

        boolean connected = garminImportService.isGarminConnected(user.getEmail());
        assertFalse(connected, "User with expired token should not be reported as connected");
    }

    @Test
    void disconnectGarminAccount_existingAccount_removesAccount() throws Exception {
        ApplicationUser user = userRepository.findAll().getFirst();
        // ensure an account exists by performing a sync which stores tokens
        garminImportService.syncActivities(user, 1, "test@email.com", "myGarminPassword");

        GarminAccount account = garminAccountRepository.findByUser(user);
        assertNotNull(account, "GarminAccount should exist after successful sync");

        // Disconnect
        garminImportService.disconnectGarminAccount(user.getEmail());

        // Account should be removed
        GarminAccount after = garminAccountRepository.findByUser(user);
        assertNull(after, "GarminAccount should have been removed after disconnect");

        // isGarminConnected must return false
        assertFalse(garminImportService.isGarminConnected(user.getEmail()));
    }

    @Test
    void disconnectGarminAccount_noAccount_completesSilently() {
        ApplicationUser user = userRepository.findAll().getFirst();

        // Ensure no account exists
        GarminAccount account = garminAccountRepository.findByUser(user);
        if (account != null) {
            garminAccountRepository.delete(account);
        }

        // Should stay silent
        garminImportService.disconnectGarminAccount(user.getEmail());

        // Still not connected
        assertFalse(garminImportService.isGarminConnected(user.getEmail()));
    }


    @Test
    void isGarminConnected_withUnknownEmail_throwsFatalBeanException() {
        String unknownEmail = "does-not-exist@example.com";

        assertThrows(
                FatalBeanException.class,
                () -> garminImportService.isGarminConnected(unknownEmail)
        );
    }

    @Test
    void disconnectGarminAccount_withUnknownEmail_throwsFatalBeanException() {
        String unknownEmail = "does-not-exist@example.com";

        assertThrows(
                FatalBeanException.class,
                () -> garminImportService.disconnectGarminAccount(unknownEmail)
        );
    }

    @Test
    void isGarminConnected_withBlankToken_returnsFalse() {
        ApplicationUser user = userRepository.findAll().getFirst();

        GarminAccount account = garminAccountRepository.findByUser(user);
        if (account == null) {
            account = new GarminAccount();
            account.setUser(user);
        }
        account.setTokenJson("    "); // blank but not null
        garminAccountRepository.save(account);

        boolean connected = garminImportService.isGarminConnected(user.getEmail());
        assertFalse(connected, "Blank token JSON should be treated as not connected");
    }

    @Test
    void isGarminConnected_withTokenWithoutRefreshExpiry_returnsFalse() {
        ApplicationUser user = userRepository.findAll().getFirst();

        GarminAccount account = garminAccountRepository.findByUser(user);
        if (account == null) {
            account = new GarminAccount();
            account.setUser(user);
        }

        // Minimal JSON: refresh_token_expires_at is 0 -> invalid token
        String tokenJson = """
        {
          "refresh_token_expires_at": 0
        }
        """;
        account.setTokenJson(tokenJson);
        garminAccountRepository.save(account);

        boolean connected = garminImportService.isGarminConnected(user.getEmail());
        assertFalse(connected, "Token without refresh expiry must be treated as invalid");
    }

    @Test
    void isGarminConnected_withMalformedTokenJson_returnsFalse() {
        ApplicationUser user = userRepository.findAll().getFirst();

        GarminAccount account = garminAccountRepository.findByUser(user);
        if (account == null) {
            account = new GarminAccount();
            account.setUser(user);
        }

        account.setTokenJson("this is not valid json {"); // will cause parse error
        garminAccountRepository.save(account);

        boolean connected = garminImportService.isGarminConnected(user.getEmail());
        assertFalse(connected, "Malformed token JSON should be treated as invalid");
    }

    @Test
    void syncActivities_withValidTokenAndNoCredentials_usesTokenBasedFlow() throws Exception {
        ApplicationUser user = userRepository.findAll().getFirst();

        // First login with credentials -> creates GarminAccount + tokens
        garminImportService.syncActivities(user, 1, "test@email.com", "myGarminPassword");

        GarminAccount afterFirstSync = garminAccountRepository.findByUser(user);
        assertNotNull(afterFirstSync, "GarminAccount should exist after first sync");
        assertNotNull(afterFirstSync.getTokenJson(), "Token JSON should be stored after first sync");

        // Second sync: no email/password -> should use token-based flow
        garminImportService.syncActivities(user, 1, null, null);

        GarminAccount afterSecondSync = garminAccountRepository.findByUser(user);
        assertNotNull(afterSecondSync, "GarminAccount must still exist after token-based sync");
        assertNotNull(afterSecondSync.getTokenJson(), "Token JSON must still be present after token-based sync");
        // We don't assert that the token changed – script may or may not refresh it.
    }

    @Test
    void syncActivities_withMockAuthError_throwsGarminAuthenticationException() {
        ApplicationUser user = userRepository.findAll().getFirst();

        assertThrows(
                GarminAuthenticationException.class,
                () -> garminImportService.syncActivities(
                        user,
                        1,
                        "auth-error@example.com",   // magic email the mock script checks
                        "whatever"
                )
        );
    }
    @Test
    void syncActivities_withMockNoRuns_throwsGarminNoDataException() {
        ApplicationUser user = userRepository.findAll().getFirst();

        assertThrows(
                GarminNoDataException.class,
                () -> garminImportService.syncActivities(
                        user,
                        1,
                        "no-runs@example.com",       // magic email
                        "whatever"
                )
        );
    }
    @Test
    void syncActivities_withMockScriptFailure_throwsGarminScriptException() {
        ApplicationUser user = userRepository.findAll().getFirst();

        assertThrows(
                GarminScriptException.class,
                () -> garminImportService.syncActivities(
                        user,
                        1,
                        "script-error@example.com",  // magic email
                        "whatever"
                )
        );
    }




}
