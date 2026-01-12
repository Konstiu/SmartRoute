package com.smartroute.smartroute1.integrationtest;

import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.endpoint.dto.AthleteDetailDto;
import com.smartroute.smartroute1.endpoint.dto.StravaAccountConnectionStateDto;
import com.smartroute.smartroute1.endpoint.dto.StravaZoneDataDto;
import com.smartroute.smartroute1.service.StravaService;
import com.smartroute.smartroute1.service.StravaOauthService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.smartroute.smartroute1.basetest.TestData.DEFAULT_USER_EMAIL;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
@ActiveProfiles({"test", "generateData"})
@AutoConfigureMockMvc
class StravaEndpointTest extends BaseTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StravaOauthService stravaOauthService;

    @MockitoBean
    private StravaService stravaService;

    @BeforeEach
    void resetMocks() {
        reset(stravaOauthService, stravaService);
    }

    @Test
    void testCallback_withValidCodeAndScope_shouldTriggerExchangeAndRedirect() throws Exception {
        String state = "xyz123";
        String encodedOrigin = URLEncoder.encode("register", StandardCharsets.UTF_8);

        when(stravaOauthService.getState(state))
                .thenReturn(new StravaOauthService.StravaOauthState(DEFAULT_USER_EMAIL, "register"));

        when(stravaOauthService.exchangeCodeForToken(anyString(), anyString(), anyString()))
                .thenReturn(null);

        when(stravaService.importStravaActivities(anyString(), anyInt()))
                .thenReturn(List.of());

        when(stravaService.importStravaZoneData(anyString()))
                .thenReturn(new StravaZoneDataDto());

        when(stravaService.importStravaAthlete(anyString()))
                .thenReturn(new AthleteDetailDto());

        var response = mockMvc.perform(get("/api/v1/strava/callback")
                        .param("code", "valid-code")
                        .param("scope", "activity:read_all,profile:read_all")
                        .param("state", state))
                .andExpect(status().isFound())
                .andReturn()
                .getResponse();

        verify(stravaOauthService).exchangeCodeForToken(eq("valid-code"), anyString(), eq(DEFAULT_USER_EMAIL));
        verify(stravaService).importStravaActivities(eq(DEFAULT_USER_EMAIL), eq(50));
        verify(stravaService).importStravaZoneData(eq(DEFAULT_USER_EMAIL));
        verify(stravaService).importStravaAthlete(eq(DEFAULT_USER_EMAIL));

        Assertions.assertTrue(response.getHeader("Location").contains(encodedOrigin));
    }

    @Test
    void testCallback_missingCodeOrScope_shouldRedirectWithoutExchange() throws Exception {
        String state = "missing";

        when(stravaOauthService.getState(state))
                .thenReturn(new StravaOauthService.StravaOauthState(DEFAULT_USER_EMAIL, "register"));

        // missing code parameter
        mockMvc.perform(get("/api/v1/strava/callback")
                        .param("state", state)
                        .param("scope", ""))
                .andExpect(status().isFound());

        verify(stravaOauthService, never()).exchangeCodeForToken(any(), any(), any());
        verify(stravaService, never()).importStravaActivities(any(), anyInt());
    }

    @Test
    void testCallback_withErrorParam_shouldRedirectAndNotExchange() throws Exception {
        String state = "errorstate";

        when(stravaOauthService.getState(state))
                .thenReturn(new StravaOauthService.StravaOauthState(DEFAULT_USER_EMAIL, "register"));

        mockMvc.perform(get("/api/v1/strava/callback")
                        .param("error", "access_denied")
                        .param("state", state))
                .andExpect(status().isFound());

        verify(stravaOauthService, never()).exchangeCodeForToken(any(), any(), any());
    }

    @Test
    void testCallback_invalidState_returnsRedirectToRoot() throws Exception {
        when(stravaOauthService.getState(anyString())).thenReturn(null);

        var response = mockMvc.perform(get("/api/v1/strava/callback")
                        .param("state", "invalid"))
                .andExpect(status().isFound())
                .andReturn()
                .getResponse();

        Assertions.assertTrue(response.getHeader("Location").endsWith("/"));
    }

    @Test
    void testCallback_anonymousUser_returns401() throws Exception {
        String state = "anon";

        when(stravaOauthService.getState(state))
                .thenReturn(new StravaOauthService.StravaOauthState("anonymousUser", "register"));

        mockMvc.perform(get("/api/v1/strava/callback")
                        .param("state", state))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = {"USER"})
    void connect_authenticatedUser_returnsStravaAuthUrl() throws Exception {
        when(stravaOauthService.createState(anyString(), eq("register")))
            .thenReturn("state123");

        var response = mockMvc.perform(get("/api/v1/strava/connect")
                .param("origin", "register"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();

        String body = response.getContentAsString();

        assertAll(
            () -> Assertions.assertTrue(body.contains("https://www.strava.com/oauth/authorize")),
            () -> Assertions.assertTrue(body.contains("state=state123"))
        );

        verify(stravaOauthService).createState(anyString(), eq("register"));
    }

    @Test
    void connect_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/strava/connect")
                .param("origin", "register"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = {"USER"})
    void getConnectionState_authenticated_returnsState() throws Exception {
        when(stravaOauthService.getConnectionState(anyString()))
            .thenReturn(new StravaAccountConnectionStateDto(true, "read"));

        mockMvc.perform(get("/api/v1/strava/connection-state"))
            .andExpect(status().isOk());

        verify(stravaOauthService).getConnectionState(anyString());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = {"USER"})
    void disconnect_authenticated_returnsState() throws Exception {
        when(stravaOauthService.disconnectStravaAccount(anyString()))
            .thenReturn(new StravaAccountConnectionStateDto(true, "read"));

        mockMvc.perform(delete("/api/v1/strava/disconnect"))
            .andExpect(status().isOk());

        verify(stravaOauthService).disconnectStravaAccount(anyString());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = {"USER"})
    void getZones_authenticated_callsService() throws Exception {
        when(stravaService.importStravaZoneData(anyString()))
            .thenReturn(new StravaZoneDataDto());

        mockMvc.perform(get("/api/v1/strava/zones"))
            .andExpect(status().isOk());

        verify(stravaService).importStravaZoneData(anyString());
    }

    @Test
    void getZones_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/strava/zones"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = {"USER"})
    void getActivities_authenticated_callsService() throws Exception {
        when(stravaService.importStravaActivities(anyString(), eq(50)))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/strava/activities"))
            .andExpect(status().isOk());

        verify(stravaService).importStravaActivities(anyString(), eq(50));
    }

    @Test
    void getActivities_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/strava/activities"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = {"USER"})
    void getAthlete_authenticated_callsService() throws Exception {
        when(stravaService.importStravaAthlete(anyString()))
            .thenReturn(new AthleteDetailDto());

        mockMvc.perform(get("/api/v1/strava/athlete"))
            .andExpect(status().isOk());

        verify(stravaService).importStravaAthlete(anyString());
    }

    @Test
    void getAthlete_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/strava/athlete"))
            .andExpect(status().isForbidden());
    }
}
