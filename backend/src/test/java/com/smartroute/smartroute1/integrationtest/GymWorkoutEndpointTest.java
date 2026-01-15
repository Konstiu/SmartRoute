package com.smartroute.smartroute1.integrationtest;

import com.smartroute.smartroute1.endpoint.dto.GymWorkoutDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.BodyPart;
import com.smartroute.smartroute1.service.GymWorkoutSelectorService;
import com.smartroute.smartroute1.service.InjuryAwareTrainingService;
import com.smartroute.smartroute1.service.ReadinessScoreService;
import com.smartroute.smartroute1.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;


import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles({"test", "generateData"})
@AutoConfigureMockMvc
public class GymWorkoutEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GymWorkoutSelectorService gymWorkoutSelectorService;

    @MockBean
    private UserService userService;

    @MockBean
    private InjuryAwareTrainingService injuryAwareTrainingService;

    @MockBean
    private ReadinessScoreService readinessScoreService;

    @Test
    void test_GetGymWorkouts_ReturnsList() throws Exception {

        String email = "test@example.com";

        GymWorkoutDto workout1 = new GymWorkoutDto();
        workout1.setId(1L);
        GymWorkoutDto workout2 = new GymWorkoutDto();
        workout2.setId(2L);

        List<GymWorkoutDto> mockWorkouts = List.of(workout1, workout2);

        when(gymWorkoutSelectorService.getAllGymWorkouts(email))
                .thenReturn(mockWorkouts);

        mockMvc.perform(get("/api/v1/gym")
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = {"USER"})
    void test_GenerateGymWorkout_ReturnsWorkout() throws Exception {
        ApplicationUser user = new ApplicationUser();
        user.setEmail("user@test.com");

        Map<BodyPart, Double> injuryMap = new HashMap<>();
        GymWorkoutDto generatedWorkout = new GymWorkoutDto();
        generatedWorkout.setId(10L);

        when(userService.findApplicationUserByEmail("user@test.com")).thenReturn(user);
        when(injuryAwareTrainingService.findInjuriesByEmail("user@test.com")).thenReturn(List.of());
        when(injuryAwareTrainingService.calculateInjuriesMap(any())).thenReturn(injuryMap);
        when(readinessScoreService.calculateReadinessScore(user, LocalDate.now())).thenReturn(50);
        when(gymWorkoutSelectorService.getGymWorkout(user, injuryMap, 50)).thenReturn(generatedWorkout);

        mockMvc.perform(get("/api/v1/gym/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = {"USER"})
    void test_GetGymWorkoutById_ReturnsWorkout() throws Exception {
        GymWorkoutDto workout = new GymWorkoutDto();
        workout.setId(99L);

        when(gymWorkoutSelectorService.getGymWorkoutById(99L, "user@test.com"))
                .thenReturn(workout);

        mockMvc.perform(get("/api/v1/gym/get/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99));
    }

    @Test
    void test_UnauthorizedAccess_Returns403() throws Exception {
        // no @WithMockUser, so user is unauthenticated
        mockMvc.perform(get("/api/v1/gym"))
                .andExpect(status().isForbidden());
    }


}
