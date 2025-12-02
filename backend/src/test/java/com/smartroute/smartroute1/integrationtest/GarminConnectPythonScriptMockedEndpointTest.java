package com.smartroute.smartroute1.integrationtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.endpoint.dto.GarminConnectAccountDto;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.repository.ActivityRepository;
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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
                () -> assertThat(activity.getExternalId()).isEqualTo(activityId),
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
                () -> assertThat(activity.getExternalId()).isEqualTo(activityId),
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
                () -> assertThat(finalActivityList1.get(10).getExternalId()).isEqualTo(activityId),
                () -> assertEquals(11, finalActivityList1.size())
        );
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