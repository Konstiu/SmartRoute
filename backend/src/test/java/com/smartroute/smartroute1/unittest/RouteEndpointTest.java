package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.endpoint.dto.ViewRouteDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.service.RouteService;
import com.smartroute.smartroute1.service.impl.CustomUserDetailService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@ActiveProfiles({"test", "generateData"})
@Transactional
@AutoConfigureMockMvc
public class RouteEndpointTest {

    private static final String BASEURI = "/api/v1";
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private RouteService routeService;
    @MockitoBean
    private CustomUserDetailService customUserDetailService;

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    public void test_WhenSaveRoute_ThenReturnHttpCreated() throws Exception {
        ApplicationUser user = new ApplicationUser();
        user.setEmail("test@test.com");

        when(customUserDetailService.findApplicationUserByEmail(any()))
                .thenReturn(user);

        doNothing().when(routeService).saveRoute(any(), any());

        String json = "{"
                + "\"name\":\"Morning Ride\","
                + "\"distance\":12.5,"
                + "\"pace\":5.2,"
                + "\"elevation\":200.0,"
                + "\"route\":\"encodedPolylineOrGeoJsonHere\""
                + "}";

        mockMvc.perform(post("/api/v1/route/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void testGetRouteById() throws Exception {
        ViewRouteDto viewRoute = new ViewRouteDto();
        viewRoute.setId(1L);
        viewRoute.setName("Test Route");

        when(routeService.getRoute(1L, "test@test.com")).thenReturn(viewRoute);

        mockMvc.perform(get("/api/v1/route/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Route"));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void testGetRoutesForUser() throws Exception {
        // Mock authenticated user
        ApplicationUser user = new ApplicationUser();
        user.setEmail("test@test.com");
        when(customUserDetailService.findApplicationUserByEmail(any())).thenReturn(user);

        // Mock service
        ViewRouteDto route1 = new ViewRouteDto();
        route1.setId(1L);
        route1.setName("Route 1");

        ViewRouteDto route2 = new ViewRouteDto();
        route2.setId(2L);
        route2.setName("Route 2");

        when(routeService.getRoutes(user)).thenReturn(List.of(route1, route2));

        mockMvc.perform(get("/api/v1/route/get"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Route 1"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("Route 2"));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void testSaveRoute_InvalidInput_ShouldReturn400() throws Exception {
        String invalidJson = "{test:yes}";

        mockMvc.perform(post("/api/v1/route/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void testDeleteRoute_ShouldReturn200() throws Exception {
        mockMvc.perform(delete("/api/v1/route/-4"))
                .andExpect(status().isOk());
    }


}
