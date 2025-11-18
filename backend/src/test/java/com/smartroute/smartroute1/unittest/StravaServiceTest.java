package com.smartroute.smartroute1.unittest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.endpoint.dto.AthleteDetailDto;
import com.smartroute.smartroute1.endpoint.dto.StravaActivityDto;
import com.smartroute.smartroute1.endpoint.dto.ZoneDataDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.StravaAccount;
import com.smartroute.smartroute1.entity.StravaActivity;
import com.smartroute.smartroute1.repository.StravaAccountRepository;
import com.smartroute.smartroute1.repository.StravaActivityRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.StravaService;
import com.smartroute.smartroute1.service.impl.StravaOauthServiceImpl;
import jakarta.transaction.Transactional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles({"test", "generateData"})
@Transactional
class StravaServiceTest extends BaseTest {

    public static MockWebServer mockStravaApi;
    @Autowired
    private StravaService stravaService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StravaAccountRepository stravaAccountRepository;
    @Autowired
    private StravaActivityRepository stravaActivityRepository;
    @MockBean
    private StravaOauthServiceImpl authService;

    @BeforeAll
    static void setup() throws IOException {
        mockStravaApi = new MockWebServer();
        mockStravaApi.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockStravaApi.shutdown();
    }

    private static StravaActivityDto getTestActivityDto() {
        StravaActivityDto activityDto = new StravaActivityDto();
        activityDto.setId(1L);
        activityDto.setName("Morning Run");

        activityDto.setDistance(25000.0f);
        activityDto.setMovingTime(3600);
        activityDto.setElapsedTime(3700);
        activityDto.setTotalElevationGain(150.0f);

        activityDto.setType("Run");
        activityDto.setSportType("Running");

        activityDto.setStartDate("2025-01-01T08:00:00Z");
        activityDto.setStartDateLocal("2025-01-01T09:00:00+01:00");

        activityDto.setAverageSpeed(3.0f);
        activityDto.setMaxSpeed(6.0f);
        return activityDto;
    }

    @BeforeEach
    void setupEach() {
        stravaActivityRepository.deleteAll();
    }

    // Test importStravaActivities

    @Test
    void testImportStravaActivities_savesActivities() throws Exception {
        ApplicationUser user = userRepository.findAll().getFirst();
        String email = user.getEmail();

        StravaAccount account = stravaAccountRepository.findByUser(user).orElseThrow();

        when(authService.ensureValidAccessToken(account)).thenReturn("dummy-token");


        StravaActivityDto activityDto = getTestActivityDto();

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(List.of(activityDto));

        mockStravaApi.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(json)
        );

        List<StravaActivityDto> result = stravaService.importStravaActivities(email);

        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(1, result.size()),
                () -> assertEquals("Morning Run", result.getFirst().getName())
        );

        List<StravaActivity> stored = stravaActivityRepository.findByStravaAccount(account);

        StravaActivity saved = stored.getFirst();

        assertAll(
                () -> assertEquals(1L, saved.getStravaId()),
                () -> assertEquals("Morning Run", saved.getName()),
                () -> assertEquals(25000.0f, saved.getDistance()),
                () -> assertEquals(3600, saved.getMovingTime()),
                () -> assertEquals(3700, saved.getElapsedTime()),
                () -> assertEquals(150.0f, saved.getTotalElevationGain()),
                () -> assertEquals("Run", saved.getType()),
                () -> assertEquals("Running", saved.getSportType()),
                () -> assertEquals("2025-01-01T08:00:00Z", saved.getStartDate()),
                () -> assertEquals("2025-01-01T09:00:00+01:00", saved.getStartDateLocal()),
                () -> assertEquals(3.0f, saved.getAverageSpeed()),
                () -> assertEquals(6.0f, saved.getMaxSpeed()),
                () -> assertEquals(account.getId(), saved.getStravaAccount().getId())
        );
    }

    @Test
    void testImportStravaActivities_noLinkedAccount_throwsNotFound() {
        ApplicationUser user = userRepository.findAll().getFirst();
        String email = user.getEmail();

        stravaAccountRepository.deleteAll();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> stravaService.importStravaActivities(email));

        assertAll(
                () -> assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode()),
                () -> assertTrue(ex.getReason().contains("No linked Strava account"))
        );
    }

    @Test
    void testImportStravaActivities_api4xx_throwsBadRequest() throws Exception {
        ApplicationUser user = userRepository.findAll().getFirst();
        String email = user.getEmail();
        StravaAccount account = stravaAccountRepository.findByUser(user).orElseThrow();

        when(authService.ensureValidAccessToken(account)).thenReturn("dummy-token");

        mockStravaApi.enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Authorization Error\"}")
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> stravaService.importStravaActivities(email));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode()),
                () -> assertTrue(ex.getReason().contains("Strava API 4xx"))
        );
    }

    @Test
    void testImportStravaActivities_api5xx_throwsBadGateway() throws Exception {
        ApplicationUser user = userRepository.findAll().getFirst();
        String email = user.getEmail();
        StravaAccount account = stravaAccountRepository.findByUser(user).orElseThrow();

        when(authService.ensureValidAccessToken(account)).thenReturn("dummy-token");

        mockStravaApi.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Internal Server Error\"}")
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> stravaService.importStravaActivities(email));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode()),
                () -> assertTrue(ex.getReason().contains("Strava API 5xx"))
        );
    }

    @Test
    void testImportStravaActivities_multipleActivities_savedCorrectly() throws Exception {
        ApplicationUser user = userRepository.findAll().getFirst();
        String email = user.getEmail();
        StravaAccount account = stravaAccountRepository.findByUser(user).orElseThrow();

        StravaActivityDto a1 = getTestActivityDto();
        StravaActivityDto a2 = getTestActivityDto();
        a2.setId(2L);
        a2.setName("Evening Ride");

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(List.of(a1, a2));

        when(authService.ensureValidAccessToken(account)).thenReturn("dummy-token");

        mockStravaApi.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json)
        );

        List<StravaActivityDto> result = stravaService.importStravaActivities(email);

        List<StravaActivity> stored = stravaActivityRepository.findByStravaAccount(account);

        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(2, result.size()),
                () -> assertEquals("Morning Run", result.get(0).getName()),
                () -> assertEquals("Evening Ride", result.get(1).getName()),
                () -> assertEquals(2, stored.size())
        );


    }

    // Test importStravaZoneData

    @Test
    void testImportStravaZoneData_success() throws Exception {
        ApplicationUser user = userRepository.findAll().getFirst();
        String email = user.getEmail();
        StravaAccount account = stravaAccountRepository.findByUser(user).orElseThrow();

        when(authService.ensureValidAccessToken(account)).thenReturn("dummy-token");

        ZoneDataDto.Zone z1 = new ZoneDataDto.Zone();
        z1.setMin(100);
        z1.setMax(120);

        ZoneDataDto.HeartRate hr = new ZoneDataDto.HeartRate();
        hr.setCustomZones(false);
        hr.setZones(List.of(z1));

        ZoneDataDto zoneData = new ZoneDataDto();
        zoneData.setHeartRate(hr);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(zoneData);

        mockStravaApi.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json)
        );

        ZoneDataDto result = stravaService.importStravaZoneData(email);

        assertAll(
                () -> assertNotNull(result),
                () -> assertNotNull(result.getHeartRate()),
                () -> assertFalse(result.getHeartRate().getCustomZones()),
                () -> assertEquals(1, result.getHeartRate().getZones().size()),
                () -> assertEquals(100, result.getHeartRate().getZones().getFirst().getMin()),
                () -> assertEquals(120, result.getHeartRate().getZones().getFirst().getMax())
        );

    }

    @Test
    void testImportStravaZoneData_noLinkedAccount_throwsNotFound() {
        ApplicationUser user = userRepository.findAll().getFirst();
        String email = user.getEmail();
        stravaAccountRepository.deleteAll();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> stravaService.importStravaZoneData(email));

        assertAll(
                () -> assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode()),
                () -> assertTrue(ex.getReason().contains("No linked Strava account"))
        );
    }

    @Test
    void testImportStravaZoneData_api4xx_throwsBadRequest() {
        ApplicationUser user = userRepository.findAll().getFirst();
        String email = user.getEmail();
        StravaAccount account = stravaAccountRepository.findByUser(user).orElseThrow();

        when(authService.ensureValidAccessToken(account)).thenReturn("dummy-token");

        mockStravaApi.enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Authorization Error\"}")
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> stravaService.importStravaZoneData(email));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode()),
                () -> assertTrue(ex.getReason().contains("Strava API 4xx"))
        );
    }

    @Test
    void testImportStravaZoneData_api5xx_throwsBadGateway() {
        ApplicationUser user = userRepository.findAll().getFirst();
        String email = user.getEmail();
        StravaAccount account = stravaAccountRepository.findByUser(user).orElseThrow();

        when(authService.ensureValidAccessToken(account)).thenReturn("dummy-token");

        mockStravaApi.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Internal Server Error\"}")
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> stravaService.importStravaZoneData(email));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode()),
                () -> assertTrue(ex.getReason().contains("Strava API 5xx"))
        );

    }

    // Test importStravaAthlete

    @Test
    void testImportStravaAthlete_success() throws Exception {
        ApplicationUser user = userRepository.findAll().getFirst();
        String email = user.getEmail();
        StravaAccount account = stravaAccountRepository.findByUser(user).orElseThrow();

        when(authService.ensureValidAccessToken(account)).thenReturn("dummy-token");

        AthleteDetailDto athleteDto = new AthleteDetailDto();
        athleteDto.setSex("M");
        athleteDto.setFtp(250);
        athleteDto.setWeight(70.5f);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(athleteDto);

        mockStravaApi.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json)
        );

        AthleteDetailDto result = stravaService.importStravaAthlete(email);

        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals("M", result.getSex()),
                () -> assertEquals(250, result.getFtp()),
                () -> assertEquals(70.5f, result.getWeight())
        );
    }

    @Test
    void testImportStravaAthlete_noLinkedAccount_throwsNotFound() {
        ApplicationUser user = userRepository.findAll().getFirst();
        String email = user.getEmail();
        stravaAccountRepository.deleteAll();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> stravaService.importStravaAthlete(email));

        assertAll(
                () -> assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode()),
                () -> assertTrue(ex.getReason().contains("No linked Strava account"))
        );
    }

    @Test
    void testImportStravaAthlete_api4xx_throwsBadRequest() {
        ApplicationUser user = userRepository.findAll().getFirst();
        String email = user.getEmail();
        StravaAccount account = stravaAccountRepository.findByUser(user).orElseThrow();

        when(authService.ensureValidAccessToken(account)).thenReturn("dummy-token");

        mockStravaApi.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Unauthorized\"}")
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> stravaService.importStravaAthlete(email));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode()),
                () -> assertTrue(ex.getReason().contains("Strava API 4xx"))
        );
    }

    @Test
    void testImportStravaAthlete_api5xx_throwsBadGateway() {
        ApplicationUser user = userRepository.findAll().getFirst();
        String email = user.getEmail();
        StravaAccount account = stravaAccountRepository.findByUser(user).orElseThrow();

        when(authService.ensureValidAccessToken(account)).thenReturn("dummy-token");

        mockStravaApi.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Internal Server Error\"}")
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> stravaService.importStravaAthlete(email));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode()),
                () -> assertTrue(ex.getReason().contains("Strava API 5xx"))
        );
    }


    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public WebClient mockWebClient() {
            return WebClient.builder()
                    .defaultHeaders(h -> h.add("Host", "www.strava.com"))
                    .baseUrl(mockStravaApi.url("/").toString())
                    .filter((request, next) -> {

                        // Rewrite absolute Strava URLs on the MockWebServer
                        URI rewritten = mockStravaApi.url("/").resolve(
                                request.url().getPath()
                        ).uri();

                        ClientRequest newRequest = ClientRequest.create(request.method(), rewritten)
                                .headers(h -> h.addAll(request.headers()))
                                .body(request.body())
                                .build();

                        return next.exchange(newRequest);
                    })
                    .build();
        }
    }
}
