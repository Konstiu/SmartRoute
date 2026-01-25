package com.smartroute.smartroute1.unittest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.endpoint.dto.StravaAccountConnectionStateDto;
import com.smartroute.smartroute1.endpoint.dto.StravaTokenResponseDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.StravaAccount;
import com.smartroute.smartroute1.exception.StravaAuthorizationException;
import com.smartroute.smartroute1.repository.StravaAccountRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.StravaOauthService;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest()
@ExtendWith(SpringExtension.class)
@ActiveProfiles({"test", "generateData"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StravaOauthServiceTest extends BaseTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Autowired
    private StravaOauthService service;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StravaAccountRepository stravaAccountRepository;

    @Test
    void testCreateState_createsNewState() {
        String state = service.createState("mail@mail", "origin");

        assertNotNull(state);
    }

    @Test
    void testCreateState_withTwoCalls_createsUniqueStates() {
        String state1 = service.createState("mail@mail", "origin");
        String state2 = service.createState("mail@mail", "origin");

        assertNotEquals(state1, state2);
    }

    @Test
    void testGetState_withEmptyStateMap_returnsNull() {
        assertNull(service.getState("mail@mail"));
    }

    @Test
    void testGetState_withEntries_returnsCorrectStates() {
        String state1 = service.createState("mail1@mail", "originA");
        String state2 = service.createState("mail2@mail", "originB");

        StravaOauthService.StravaOauthState s1 = service.getState(state1);
        StravaOauthService.StravaOauthState s2 = service.getState(state2);

        assertAll(
            () -> assertEquals("mail1@mail", s1.email),
            () -> assertEquals("mail2@mail", s2.email),
            () -> assertEquals("originA", s1.origin),
            () -> assertEquals("originB", s2.origin)
        );
    }

    @Test
    void testGetState_whenGettingState_stateRemovedFromMap() {
        String state = service.createState("mail@mail", "originA");

        StravaOauthService.StravaOauthState s1 = service.getState(state);
        StravaOauthService.StravaOauthState s2 = service.getState(state);

        assertAll(
            () -> assertEquals("mail@mail", s1.email),
            () -> assertEquals("originA", s1.origin),
            () -> assertNull(s2)
        );
    }

    @Test
    void testDisconnectStravaAccount_withDisconnectedAccount_returnsCorrectState() {
        ApplicationUser user = new ApplicationUser();
        user.setEmail("test@smartroute.com");
        user.setFirstname("Max");
        user.setLastname("Mustermann");
        user.setPassword("password");
        user.setVerified(true);
        userRepository.save(user);

        StravaAccount account = new StravaAccount();
        account.setScopes("read");
        account.setUser(user);
        // no access token set
        stravaAccountRepository.save(account);

        StravaAccountConnectionStateDto dto = service.disconnectStravaAccount("test@smartroute.com");

        StravaAccount updatedAccount = stravaAccountRepository.findByUser(user).orElse(null);

        assertAll(
            () -> assertFalse(dto.isConnected()),
            () -> assertEquals("", dto.getScopes()),
            () -> assertNull(account.getAccessToken()),
            () -> assertNull(account.getRefreshToken())
        );
    }

    @Test
    void testDisconnectStravaAccount_withoutStravaAccountForUser_returnsCorrectState() {
        ApplicationUser user = new ApplicationUser();
        user.setEmail("test@smartroute.com");
        user.setFirstname("Max");
        user.setLastname("Mustermann");
        user.setPassword("password");
        user.setVerified(true);
        userRepository.save(user);

        StravaAccountConnectionStateDto dto = service.disconnectStravaAccount("test@smartroute.com");

        assertAll(
            () -> assertFalse(dto.isConnected()),
            () -> assertEquals("", dto.getScopes())
        );
    }

    @Test
    void testDisconnectStravaAccount_withConnectedUser_returnsCorrectState() {
        ApplicationUser user = new ApplicationUser();
        user.setEmail("test@smartroute.com");
        user.setFirstname("Max");
        user.setLastname("Mustermann");
        user.setPassword("password");
        user.setVerified(true);
        userRepository.save(user);

        StravaAccount account = new StravaAccount();
        account.setUser(user);
        account.setAccessToken("still-valid");
        account.setRefreshToken("refresh");
        account.setExpiresAt(Instant.now().plusSeconds(3600)); // VALID
        stravaAccountRepository.save(account);

        mockApiServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("deleted"));

        StravaAccountConnectionStateDto dto = service.disconnectStravaAccount("test@smartroute.com");

        StravaAccount updatedAccount = stravaAccountRepository.findByUser(user).orElse(null);

        assertAll(
            () -> assertFalse(dto.isConnected()),
            () -> assertEquals("", dto.getScopes()),
            () -> assertNull(updatedAccount)
        );
    }

    @Test
    void testDisconnectStravaAccount_withConnectedUser_stravaAuthorizationException_returnsCorrectState() {
        ApplicationUser user = new ApplicationUser();
        user.setEmail("test@smartroute.com");
        user.setFirstname("Max");
        user.setLastname("Mustermann");
        user.setPassword("password");
        user.setVerified(true);
        userRepository.save(user);

        StravaAccount account = new StravaAccount();
        account.setUser(user);
        account.setAccessToken("still-valid");
        account.setRefreshToken("refresh");
        account.setExpiresAt(Instant.now().minusSeconds(3600)); // INVALID
        stravaAccountRepository.save(account);

        mockApiServer.enqueue(new MockResponse()
            .setResponseCode(400)
            .setBody("{\"message\":\"invalid code\"}")
        );

        StravaAccountConnectionStateDto dto = service.disconnectStravaAccount("test@smartroute.com");

        StravaAccount updatedAccount = stravaAccountRepository.findByUser(user).orElse(null);

        assertAll(
            () -> assertFalse(dto.isConnected()),
            () -> assertEquals("", dto.getScopes()),
            () -> assertNull(updatedAccount)
        );
    }

    @Test
    void testGetConnectionState_whenNotConnected_returnCorrectState() {
        ApplicationUser user = new ApplicationUser();
        user.setEmail("test@smartroute.com");
        user.setFirstname("Max");
        user.setLastname("Mustermann");
        user.setPassword("password");
        user.setVerified(true);
        userRepository.save(user);

        StravaAccountConnectionStateDto dto = service.getConnectionState(user.getEmail());

        assertAll(
            () -> assertNotNull(dto),
            () -> assertEquals(false, dto.isConnected()),
            () -> assertEquals("", dto.getScopes())
        );
    }

    @Test
    void testGetConnectionState_whenConnected_returnCorrectState() {
        ApplicationUser user = new ApplicationUser();
        user.setEmail("test@smartroute.com");
        user.setFirstname("Max");
        user.setLastname("Mustermann");
        user.setPassword("password");
        user.setVerified(true);
        userRepository.save(user);

        StravaAccount account = new StravaAccount();
        account.setScopes("read");
        account.setUser(user);
        stravaAccountRepository.save(account);

        StravaAccountConnectionStateDto dto = service.getConnectionState(user.getEmail());

        assertAll(
            () -> assertNotNull(dto),
            () -> assertEquals(true, dto.isConnected()),
            () -> assertEquals(account.getScopes(), dto.getScopes())
        );
    }

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
    void testExchangeCodeForToken_4xx_throws() {
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

    @Test
    void testExchangeCodeForToken_updatesToken() throws Exception {
        ApplicationUser user = new ApplicationUser();
        user.setEmail("update@test.com");
        user.setFirstname("Test");
        user.setLastname("User");
        user.setPassword("pw");
        user.setVerified(true);
        userRepository.save(user);

        // existing account
        StravaAccount existing = new StravaAccount();
        existing.setUser(user);
        existing.setAccessToken("old-access");
        existing.setRefreshToken("old-refresh");
        existing.setExpiresAt(Instant.now().minusSeconds(1000));
        stravaAccountRepository.save(existing);

        StravaTokenResponseDto dto = new StravaTokenResponseDto();
        dto.setAccessToken("updated-access");
        dto.setRefreshToken("updated-refresh");
        dto.setExpiresAt(Instant.now().plusSeconds(3600).getEpochSecond());
        dto.setScope("read,activity:read");

        mockApiServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(mapper.writeValueAsString(dto))
        );

        StravaTokenResponseDto result = service.exchangeCodeForToken("abc123", "read", user.getEmail());

        StravaAccount updated = stravaAccountRepository.findByUser(user).orElseThrow();

        assertAll(
            () -> assertEquals("updated-access", updated.getAccessToken()),
            () -> assertEquals("updated-refresh", updated.getRefreshToken()),
            () -> assertTrue(updated.getExpiresAt().isAfter(Instant.now()))
        );
    }

    @Test
    void testEnsureValidAccessToken_doesNotRefreshIfNotExpired() {
        ApplicationUser user = new ApplicationUser();
        user.setEmail("norefresh@test.com");
        user.setFirstname("Test");
        user.setLastname("User");
        user.setPassword("pw");
        user.setVerified(true);
        userRepository.save(user);

        StravaAccount account = new StravaAccount();
        account.setUser(user);
        account.setAccessToken("still-valid");
        account.setRefreshToken("refresh");
        account.setExpiresAt(Instant.now().plusSeconds(3600)); // VALID
        stravaAccountRepository.save(account);

        String token = service.ensureValidAccessToken(account);

        assertAll(
            () -> assertEquals("still-valid", token),
            () -> assertEquals(0, mockApiServer.getRequestCount()) // no HTTP request
        );

    }

}
