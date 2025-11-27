package com.smartroute.smartroute1.integrationtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.endpoint.dto.GarminConnectAccountDto;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "generateData"})
@AutoConfigureMockMvc
class GarminConnectPythonScriptMockedEndpointTest extends BaseTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @WithMockUser(username = "email0@smartroute.com", roles = "USER")
    void syncActivities_withValidRequestBody_shouldReturn200WithGarminData() throws Exception {
        GarminConnectAccountDto garminConnectAccountDto = new GarminConnectAccountDto();
        garminConnectAccountDto.setGarminEmail("test@garmin.com");
        garminConnectAccountDto.setGarminPassword("myPassword");
        garminConnectAccountDto.setCount(1);

        performSync(garminConnectAccountDto)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].activityId").value(21013233687L))
                .andExpect(jsonPath("$[0].activityName").value("Vienna Running"))
                .andExpect(jsonPath("$[0].summary.activityType.typeKey").value("running"))
                .andExpect(jsonPath("$[0].summary.distance").value(3610.219970703125))
                .andExpect(jsonPath("$[0].summary.averageHR").value(164.0))
                .andExpect(jsonPath("$[0].details.measurementCount").value(24));
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
        GarminConnectAccountDto garminConnectAccountDto = new GarminConnectAccountDto();
        garminConnectAccountDto.setGarminEmail("test@garmin.com");
        garminConnectAccountDto.setGarminPassword("myPassword");
        garminConnectAccountDto.setCount(1);

        performSync(garminConnectAccountDto)
                .andExpect(status().isOk());

        GarminConnectAccountDto garminConnectAccountDtoNew = new GarminConnectAccountDto();
        garminConnectAccountDtoNew.setCount(1);

        performSync(garminConnectAccountDto)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].activityId").value(21013233687L))
                .andExpect(jsonPath("$[0].activityName").value("Vienna Running"))
                .andExpect(jsonPath("$[0].summary.activityType.typeKey").value("running"))
                .andExpect(jsonPath("$[0].summary.distance").value(3610.219970703125))
                .andExpect(jsonPath("$[0].summary.averageHR").value(164.0))
                .andExpect(jsonPath("$[0].details.measurementCount").value(24));
    }

    @Test
    @WithMockUser(username = "email0@smartroute.com", roles = "USER")
    void syncActivities_withCountThree_shouldReturnThreeActivities() throws Exception {
        GarminConnectAccountDto dto = new GarminConnectAccountDto();
        dto.setGarminEmail("test@garmin.com");
        dto.setGarminPassword("myPassword");
        dto.setCount(3);

        performSync(dto)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].activityId").value(21013233687L))
                .andExpect(jsonPath("$[1].activityId").value(21013233687L))
                .andExpect(jsonPath("$[2].activityId").value(21013233687L));
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