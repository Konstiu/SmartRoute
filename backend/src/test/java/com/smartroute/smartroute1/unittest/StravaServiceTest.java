package com.smartroute.smartroute1.unittest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.endpoint.dto.AthleteDetailDto;
import com.smartroute.smartroute1.endpoint.dto.StravaActivityDto;
import com.smartroute.smartroute1.endpoint.dto.StravaZoneDataDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.StravaAccount;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.repository.StravaAccountRepository;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.StravaService;
import com.smartroute.smartroute1.service.impl.StravaOauthServiceImpl;
import jakarta.transaction.Transactional;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles({"test", "generateData"})
@Transactional
class StravaServiceTest {

    @Autowired
    private StravaService stravaService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StravaAccountRepository stravaAccountRepository;
    @Autowired
    private ActivityRepository activityRepository;
    @MockBean
    private StravaOauthServiceImpl authService;

    private static StravaActivityDto getTestActivityDto() {
        StravaActivityDto activityDto = new StravaActivityDto();
        activityDto.setStravaId(1L);
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

        mockApiServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(json)
        );

        mockApiServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("[{\"type\": \"heartrate\", \"data\": [150,151,152], \"original_size\": 3}]")
        );

        List<StravaActivityDto> result = stravaService.importStravaActivities(email);

        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(1, result.size()),
                () -> assertEquals("Morning Run", result.getFirst().getName())
        );

        List<Activity> stored = activityRepository.findByUser(user);

        Activity saved = stored.stream().filter(s -> s.getStravaId() != null && s.getStravaId() == 1L).findFirst().get();

        assertAll(
                () -> assertEquals(1L, saved.getStravaId()),
                () -> assertEquals("Morning Run", saved.getName()),
                () -> assertEquals(25000.0f, saved.getDistance()),
                () -> assertEquals(3600, saved.getMovingTime()),
                () -> assertEquals(3700, saved.getElapsedTime()),
                () -> assertEquals(150.0f, saved.getTotalElevationGain()),
                () -> assertEquals("Run", saved.getType()),
                () -> assertEquals("Running", saved.getSportType()),
                () -> assertEquals(Instant.parse("2025-01-01T08:00:00Z"), saved.getStartDate()),
                () -> assertEquals(Instant.parse("2025-01-01T09:00:00+01:00"), saved.getStartDateLocal()),
                () -> assertEquals(3.0f, saved.getAverageSpeed()),
                () -> assertEquals(6.0f, saved.getMaxSpeed()),
                () -> assertEquals(user.getId(), saved.getUser().getId())
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
    void testImportStravaActivities_api4xx_throwsBadRequest() {
        ApplicationUser user = userRepository.findAll().getFirst();
        String email = user.getEmail();
        StravaAccount account = stravaAccountRepository.findByUser(user).orElseThrow();

        when(authService.ensureValidAccessToken(account)).thenReturn("dummy-token");

        mockApiServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Authorization Error\"}")
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> stravaService.importStravaActivities(email));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode()),
                () -> {
                    assertNotNull(ex.getReason());
                    assertTrue(ex.getReason().contains("Strava API 4xx"));
                }
        );
    }

    @Test
    void testImportStravaActivities_api5xx_throwsBadGateway() {
        ApplicationUser user = userRepository.findAll().getFirst();
        String email = user.getEmail();
        StravaAccount account = stravaAccountRepository.findByUser(user).orElseThrow();

        when(authService.ensureValidAccessToken(account)).thenReturn("dummy-token");

        mockApiServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Internal Server Error\"}")
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> stravaService.importStravaActivities(email));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode()),
                () -> {
                    assertNotNull(ex.getReason());
                    assertTrue(ex.getReason().contains("Strava API 5xx"));
                }
        );
    }

    @Test
    void testImportStravaActivities_multipleActivities_savedCorrectly() throws Exception {
        ApplicationUser user = userRepository.findAll().getFirst();
        String email = user.getEmail();
        StravaAccount account = stravaAccountRepository.findByUser(user).orElseThrow();

        StravaActivityDto a1 = getTestActivityDto();
        StravaActivityDto a2 = getTestActivityDto();
        a2.setStravaId(2L);
        a2.setName("Evening Ride");

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(List.of(a1, a2));

        when(authService.ensureValidAccessToken(account)).thenReturn("dummy-token");

        mockApiServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json)
        );

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
                        .setBody("[{\"type\": \"heartrate\", \"data\": [144,141,142], \"original_size\": 3}]")
        );

        List<StravaActivityDto> result = stravaService.importStravaActivities(email);

        List<Activity> stored = activityRepository.findByUser(user);

        Activity saved1 = stored.stream().filter(s -> s.getStravaId() != null && s.getStravaId() == 1L).findFirst().get();
        Activity saved2 = stored.stream().filter(s -> s.getStravaId() != null && s.getStravaId() == 2L).findFirst().get();

        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals("Morning Run", saved1.getName()),
                () -> assertEquals("Evening Ride", saved2.getName())
        );
    }

    // Test importStravaZoneData

    @Test
    void testImportStravaZoneData_success() throws Exception {
        ApplicationUser user = userRepository.findAll().getFirst();
        String email = user.getEmail();
        StravaAccount account = stravaAccountRepository.findByUser(user).orElseThrow();

        when(authService.ensureValidAccessToken(account)).thenReturn("dummy-token");

        StravaZoneDataDto.Zone z1 = new StravaZoneDataDto.Zone();
        z1.setMin(100);
        z1.setMax(120);

        StravaZoneDataDto.HeartRate hr = new StravaZoneDataDto.HeartRate();
        hr.setCustomZones(false);
        hr.setZones(List.of(z1));

        StravaZoneDataDto zoneData = new StravaZoneDataDto();
        zoneData.setHeartRate(hr);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(zoneData);

        mockApiServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json)
        );

        StravaZoneDataDto result = stravaService.importStravaZoneData(email);

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

        mockApiServer.enqueue(new MockResponse()
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

        mockApiServer.enqueue(new MockResponse()
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

        mockApiServer.enqueue(new MockResponse()
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

        mockApiServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Unauthorized\"}")
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> stravaService.importStravaAthlete(email));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode()),
                () -> {
                    assertNotNull(ex.getReason());
                    assertTrue(ex.getReason().contains("Strava API 4xx"));
                }
        );
    }

    @Test
    void testImportStravaAthlete_api5xx_throwsBadGateway() {
        ApplicationUser user = userRepository.findAll().getFirst();
        String email = user.getEmail();
        StravaAccount account = stravaAccountRepository.findByUser(user).orElseThrow();

        when(authService.ensureValidAccessToken(account)).thenReturn("dummy-token");

        mockApiServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Internal Server Error\"}")
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> stravaService.importStravaAthlete(email));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode()),
                () -> {
                    assertNotNull(ex.getReason());
                    assertTrue(ex.getReason().contains("Strava API 5xx"));
                }
        );
    }
}
