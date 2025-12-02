package com.smartroute.smartroute1.integrationtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.endpoint.dto.GarminConnectAccountDto;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.GarminAccount;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.GarminAccountRepository;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "generateData"})
@AutoConfigureMockMvc
class GarminConnectPythonScriptMockedEndpointTest extends BaseTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private GarminAccountRepository garminAccountRepository;

    @Test
    @WithMockUser(username = "email0@smartroute.com", roles = "USER")
    void syncActivities_withValidRequestBody_shouldReturn200WithGarminData() throws Exception {
        ApplicationUser user = userRepository.findAll().getFirst();
        GarminConnectAccountDto garminConnectAccountDto = new GarminConnectAccountDto();
        garminConnectAccountDto.setGarminEmail("test@garmin.com");
        garminConnectAccountDto.setGarminPassword("myPassword");
        garminConnectAccountDto.setCount(1);

        performSync(garminConnectAccountDto)
                .andExpect(status().isOk());

        long activityId = 21013233687L;
        long beginTimestamp = 1763377372000L; // from MOCK_ACTIVITY.beginTimestamp
        Instant startInstant = Instant.ofEpochMilli(beginTimestamp);

        Optional<Activity> activityOpt =
                activityRepository.getActivitiesByUserAndStartDateAndExternalId(user, startInstant, String.valueOf(activityId));

        assertThat(activityOpt)
                .as("Garmin activity should have been imported")
                .isPresent();

        Activity activity = activityOpt.get();

        assertAll(
                () -> assertThat(activity.getExternalId()).isEqualTo(String.valueOf(activityId)),
                () -> assertThat(activity.getName()).isEqualTo("Vienna Running"),
                () -> assertThat(activity.getDistance()).isEqualTo(22.350368f)
        );
    }

    @Test
    @WithMockUser(username = "email0@smartroute.com", roles = "USER")
    void syncActivities_withEmptyResult_shouldReturn401() throws Exception {
        GarminConnectAccountDto garminConnectAccountDto = new GarminConnectAccountDto();
        garminConnectAccountDto.setGarminEmail("");
        garminConnectAccountDto.setGarminPassword("myEmail");
        garminConnectAccountDto.setCount(0);
        performSync(garminConnectAccountDto)
                .andExpect(status().is(401));
    }


    @Test
    @WithMockUser(username = "email0@smartroute.com", roles = "USER")
    void syncActivities_withNullPassword_shouldReturn401() throws Exception {
        GarminConnectAccountDto garminConnectAccountDto = new GarminConnectAccountDto();
        garminConnectAccountDto.setGarminEmail("no@password.com");
        garminConnectAccountDto.setCount(0);
        performSync(garminConnectAccountDto)
                .andExpect(status().is(401));

    }


    @Test
    void syncActivities_withoutAuthentication_shouldReturn403() throws Exception {
        GarminConnectAccountDto garminConnectAccountDto = new GarminConnectAccountDto();
        garminConnectAccountDto.setGarminEmail("no@password.com");
        garminConnectAccountDto.setCount(0);
        performSync(garminConnectAccountDto)
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WRONG_ROLE")
    void syncActivities_withWrongRole_shouldReturn403() throws Exception {
        GarminConnectAccountDto dto = new GarminConnectAccountDto();
        dto.setGarminEmail("test@garmin.com");
        dto.setGarminPassword("password");
        dto.setCount(5);

        performSync(dto)
                .andExpect(status().isForbidden());
    }


    @Test
    @WithMockUser(username = "email0@smartroute.com", roles = "USER")
    void syncActivities_withValidRequestBody_WithTokenFromDB_shouldReturn200WithGarminData() throws Exception {
        ApplicationUser user = userRepository.findAll().getFirst();
        GarminConnectAccountDto garminConnectAccountDto = new GarminConnectAccountDto();
        garminConnectAccountDto.setGarminEmail("test@garmin.com");
        garminConnectAccountDto.setGarminPassword("myPassword");
        garminConnectAccountDto.setCount(1);

        performSync(garminConnectAccountDto)
                .andExpect(status().isOk());

        GarminConnectAccountDto garminConnectAccountDtoNew = new GarminConnectAccountDto();
        garminConnectAccountDtoNew.setCount(1);

        long activityId = 21013233687L;
        long beginTimestamp = 1763377372000L; // from MOCK_ACTIVITY.beginTimestamp
        Instant startInstant = Instant.ofEpochMilli(beginTimestamp);

        Optional<Activity> activityOpt =
                activityRepository.getActivitiesByUserAndStartDateAndExternalId(user, startInstant, String.valueOf(activityId));

        assertThat(activityOpt)
                .as("Garmin activity should have been imported")
                .isPresent();

        Activity activity = activityOpt.get();

        // assert some important fields
        assertAll(
                () -> assertThat(activity.getExternalId()).isEqualTo(String.valueOf(activityId)),
                () -> assertThat(activity.getName()).isEqualTo("Vienna Running"),
                () -> assertThat(activity.getDistance()).isEqualTo(22.350368f)
        );


    }

    @Test
    @WithMockUser(username = "email0@smartroute.com", roles = "USER")
    void syncActivities_withCountTen_shouldPersistOneMoreActivity() throws Exception {
        ApplicationUser user = userRepository.findAll().getFirst();
        GarminConnectAccountDto dto = new GarminConnectAccountDto();
        dto.setGarminEmail("test@garmin.com");
        dto.setGarminPassword("myPassword");
        dto.setCount(3);

        List<Activity> activityList =
                activityRepository.getActivitiesByUser(user);
        List<Activity> finalActivityList = activityList;
        assertAll(
                () -> assertEquals(10, finalActivityList.size())
        );

        performSync(dto)
                .andExpect(status().isOk());

        long activityId = 21013233687L;

        activityList =
                activityRepository.getActivitiesByUser(user);

        List<Activity> finalActivityList1 = activityList;
        assertAll(
                () -> assertThat(finalActivityList1.get(10).getExternalId()).isEqualTo(String.valueOf(activityId)),
                () -> assertEquals(11, finalActivityList1.size())
        );
    }

    @Test
    @WithMockUser(username = "email0@smartroute.com", roles = "USER")
    void isGarminConnected_authenticated_shouldReturnTrue() throws Exception {
        GarminConnectAccountDto garminConnectAccountDto = new GarminConnectAccountDto();
        garminConnectAccountDto.setGarminEmail("test@garmin.com");
        garminConnectAccountDto.setGarminPassword("myPassword");
        garminConnectAccountDto.setCount(1);

        performSync(garminConnectAccountDto)
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/garmin/connection-state"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void isGarminConnected_withoutAuthentication_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/garmin/connection-state"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "email0@smartroute.com", roles = "USER")
    void isGarminConnected_withExpiredToken_returnsFalse() throws Exception {
        ApplicationUser user = userRepository.findAll().getFirst();
        GarminConnectAccountDto garminConnectAccountDto = new GarminConnectAccountDto();
        garminConnectAccountDto.setGarminEmail("test@garmin.com");
        garminConnectAccountDto.setGarminPassword("myPassword");
        garminConnectAccountDto.setCount(1);

        performSync(garminConnectAccountDto).andExpect(status().isOk());

        // set token to expired
        GarminAccount account = garminAccountRepository.findByUser(user);
        assertNotNull(account);
        long now = Instant.now().getEpochSecond();
        String expired = getExpiredTokenJson(now);
        account.setTokenJson(expired);
        garminAccountRepository.save(account);

        mockMvc.perform(get("/api/v1/garmin/connection-state"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    @WithMockUser(username = "email0@smartroute.com", roles = "USER")
    void isGarminConnected_userWithoutGarminAccount_returnsFalse() throws Exception {
        ApplicationUser user = userRepository.findAll().getFirst();
        GarminAccount account = garminAccountRepository.findByUser(user);
        if (account != null) {
            garminAccountRepository.delete(account);
        }

        mockMvc.perform(get("/api/v1/garmin/connection-state"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    @WithMockUser(username = "email0@smartroute.com", roles = "USER")
    void disconnectEndpoint_withConnectedAccount_removesAccountAndReturnsNoContent() throws Exception {
        GarminConnectAccountDto dto = new GarminConnectAccountDto();
        dto.setGarminEmail("test@garmin.com");
        dto.setGarminPassword("myPassword");
        dto.setCount(1);

        performSync(dto).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/garmin/disconnect"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/garmin/connection-state"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    @WithMockUser(username = "email0@smartroute.com", roles = "USER")
    void disconnectEndpoint_withoutAccount_returnsNoContentAndStillNotConnected() throws Exception {
        ApplicationUser user = userRepository.findAll().getFirst();
        com.smartroute.smartroute1.entity.GarminAccount account = garminAccountRepository.findByUser(user);
        if (account != null) {
            garminAccountRepository.delete(account);
        }

        mockMvc.perform(post("/api/v1/garmin/disconnect"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/garmin/connection-state"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    void disconnectEndpoint_withoutAuthentication_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/garmin/disconnect"))
                .andExpect(status().isForbidden());
    }

    @NotNull
    private static String getExpiredTokenJson(long now) {
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

    @NotNull
    @Contract("_ -> new")
    private ResultActions performSync(GarminConnectAccountDto dto) throws Exception {
        return mockMvc.perform(post("/api/v1/garmin/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(dto)));
    }

    private String toJson(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

}
