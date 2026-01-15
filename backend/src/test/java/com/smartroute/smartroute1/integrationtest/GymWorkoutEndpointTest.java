package com.smartroute.smartroute1.endpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.endpoint.dto.GymWorkoutDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.BodyPart;
import com.smartroute.smartroute1.service.GymWorkoutSelectorService;
import com.smartroute.smartroute1.service.InjuryAwareTrainingService;
import com.smartroute.smartroute1.service.ReadinessScoreService;
import com.smartroute.smartroute1.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "generateData"})
class GymWorkoutEndpointTest {

    private static final String EMAIL = "testuser@example.com";
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private GymWorkoutSelectorService gymWorkoutSelectorService;
    @MockBean
    private UserService userService;
    @MockBean
    private InjuryAwareTrainingService injuryAwareTrainingService;
    @MockBean
    private ReadinessScoreService readinessScoreService;

    @Test
    @WithMockUser(username = EMAIL, roles = "USER")
    void getGymWorkouts_shouldReturnList() throws Exception {
        GymWorkoutDto workout = new GymWorkoutDto();
        workout.setId(1L);

        Mockito.when(gymWorkoutSelectorService.getAllGymWorkouts(EMAIL))
                .thenReturn(List.of(workout));

        mockMvc.perform(get("/api/v1/gym"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1L));
    }


    @Test
    @WithMockUser(username = EMAIL, roles = "USER")
    void generateGymWorkout_shouldReturnWorkout() throws Exception {
        ApplicationUser user = new ApplicationUser();
        user.setEmail(EMAIL);

        GymWorkoutDto workout = new GymWorkoutDto();
        workout.setId(42L);

        Mockito.when(userService.findApplicationUserByEmail(EMAIL))
                .thenReturn(user);

        Mockito.when(injuryAwareTrainingService.findInjuriesByEmail(EMAIL))
                .thenReturn(List.of());

        Mockito.when(injuryAwareTrainingService.calculateInjuriesMap(List.of()))
                .thenReturn(Map.of(BodyPart.HIP, 0.0));

        Mockito.when(readinessScoreService.calculateReadinessScore(user, LocalDate.now()))
                .thenReturn(85);

        Mockito.when(gymWorkoutSelectorService.getGymWorkout(
                Mockito.eq(user),
                Mockito.anyMap(),
                Mockito.eq(85)
        )).thenReturn(workout);

        mockMvc.perform(get("/api/v1/gym/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42L));
    }


    @Test
    @WithMockUser(username = EMAIL, roles = "USER")
    void getGymWorkoutById_shouldReturnWorkout() throws Exception {
        GymWorkoutDto workout = new GymWorkoutDto();
        workout.setId(99L);

        Mockito.when(gymWorkoutSelectorService.getGymWorkoutById(99L, EMAIL))
                .thenReturn(workout);

        mockMvc.perform(get("/api/v1/gym/get/{id}", 99))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99L));
    }

    @Test
    void getGymWorkouts_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/gym"))
                .andExpect(status().isForbidden());
    }
}
