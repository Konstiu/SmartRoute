package com.smartroute.smartroute1.unittest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.endpoint.dto.StravaTokenResponseDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.StravaAccount;
import com.smartroute.smartroute1.exception.StravaAuthorizationException;
import com.smartroute.smartroute1.repository.StravaAccountRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.impl.StravaOauthServiceImpl;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest()
@ExtendWith(SpringExtension.class)
@ActiveProfiles({"test", "generateData"})
public class StravaOauthServiceTest extends BaseTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Autowired
    private StravaOauthServiceImpl service;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StravaAccountRepository stravaAccountRepository;

    @Test
    void testExchangeCodeForToken_createsNewAccount() throws Exception {
        ApplicationUser user = new ApplicationUser();
        user.setEmail("test@smartroute.com");
        user.setFirstname("Max");
        user.setLastname("Mustermann");
        user.setPassword("password");
        user.setVerified(true);
        userRepository.save(user);

        StravaTokenResponseDto dto = new StravaTokenResponseDto();
        dto.setAccessToken("dummy-access");
        dto.setRefreshToken("dummy-refresh");
        dto.setExpiresAt(Instant.now().plusSeconds(3600).getEpochSecond());
        dto.setAthleteId(123L);
        dto.setScope("read,activity:read");

        mockApiServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(mapper.writeValueAsString(dto))
        );

        StravaTokenResponseDto result = service.exchangeCodeForToken("code123", "read", "test@smartroute.com");

        StravaAccount savedAccount = stravaAccountRepository.findByUser(user).orElseThrow(AssertionError::new);

        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals("dummy-access", result.getAccessToken()),
                () -> assertEquals("dummy-access", savedAccount.getAccessToken()),
                () -> assertEquals("dummy-refresh", savedAccount.getRefreshToken()),
                () -> assertNotNull(result),
                () -> assertEquals("dummy-access", result.getAccessToken())
        );
    }

    @Test
    void testEnsureValidAccessToken_refreshesToken() throws Exception {
        ApplicationUser user = new ApplicationUser();
        user.setEmail("test@smartroute.com");
        user.setFirstname("Max");
        user.setLastname("Mustermann");
        user.setPassword("password");
        user.setVerified(true);
        userRepository.save(user);

        StravaAccount account = new StravaAccount();
        account.setUser(user);
        account.setAccessToken("old-token");
        account.setRefreshToken("old-refresh");
        account.setExpiresAt(Instant.now().minusSeconds(10)); // expired
        stravaAccountRepository.save(account);

        StravaTokenResponseDto refreshed = new StravaTokenResponseDto();
        refreshed.setAccessToken("new-token");
        refreshed.setRefreshToken("new-refresh");
        refreshed.setExpiresAt(Instant.now().plusSeconds(3600).getEpochSecond());

        mockApiServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(mapper.writeValueAsString(refreshed))
        );

        String token = service.ensureValidAccessToken(account);

        StravaAccount updated = stravaAccountRepository.findById(account.getId()).orElseThrow();


        assertAll(
                () -> assertEquals("new-token", token),
                () -> assertEquals("new-token", updated.getAccessToken())
        );

    }

    @Test
    void testExchangeCodeForToken_4xx_throws() throws Exception {
        ApplicationUser user = new ApplicationUser();
        user.setEmail("test@smartroute.com");
        user.setFirstname("Max");
        user.setLastname("Mustermann");
        user.setPassword("password");
        user.setVerified(true);
        userRepository.save(user);

        mockApiServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("{\"message\":\"invalid code\"}")
        );

        assertThrows(StravaAuthorizationException.class,
                () -> service.exchangeCodeForToken("invalid", "read", user.getEmail()));
    }
}
