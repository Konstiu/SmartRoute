package com.smartroute.smartroute1.integrationtest;

import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.basetest.TestData;
import com.smartroute.smartroute1.entity.StravaActivity;
import com.smartroute.smartroute1.repository.StravaActivityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "generateData"})
class StravaActivityViewEndpointTest extends BaseTest implements TestData {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StravaActivityRepository stravaActivityRepository;

    private static final String STRAVA_BASE_URI = BASE_URI + "/strava";

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = {"USER"})
    void getStravaActivities_shouldReturnListOfActivities_whenAuthenticated() throws Exception {
        mockMvc.perform(get(STRAVA_BASE_URI + "/activities/view")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3)) // NUMBER_OF_ACTIVITIES_PER_USER
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].distance").exists());
    }

    @Test
    void getStravaActivities_shouldReturn403_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get(STRAVA_BASE_URI + "/activities/view")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = {"USER"})
    void getOneStravaActivity_shouldReturnDetailedActivity_whenExists() throws Exception {
        List<StravaActivity> activities = stravaActivityRepository.findAll();
        Long activityId = activities.get(0).getId();

        mockMvc.perform(get(STRAVA_BASE_URI + "/activity/" + activityId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(activityId))
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.distance").exists())
                .andExpect(jsonPath("$.averageSpeed").exists());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = {"USER"})
    void getOneStravaActivity_shouldReturn404_whenActivityNotFound() throws Exception {
        long nonExistentId = 99999L;

        mockMvc.perform(get(STRAVA_BASE_URI + "/activity/" + nonExistentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "email1@smartroute.com", roles = {"USER"})
    void getOneStravaActivity_shouldReturn404OrForbidden_whenAccessingOtherUsersActivity() throws Exception {
        // Get activity from first user - just use the first activity in the list
        // Since data generator creates activities in order, first 3 belong to email0
        List<StravaActivity> activities = stravaActivityRepository.findAll();
        Long firstUserActivityId = activities.get(0).getId();

        // Try to access it as second user (email1@smartroute.com)
        mockMvc.perform(get(STRAVA_BASE_URI + "/activity/" + firstUserActivityId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(anyOf(is(403), is(404))));
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = {"ADMIN"})
    void getStravaActivities_shouldReturn403_whenUserDoesNotHaveUserRole() throws Exception {
        mockMvc.perform(get(STRAVA_BASE_URI + "/activities/view")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = {"USER"})
    void getStravaActivities_shouldReturnCorrectActivityData() throws Exception {
        mockMvc.perform(get(STRAVA_BASE_URI + "/activities/view")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].type").value(everyItem(is("Run"))))
                .andExpect(jsonPath("$[*].sportType").value(everyItem(is("Run"))));
    }
}