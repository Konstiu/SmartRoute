package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.StravaActivityDto;
import com.smartroute.smartroute1.endpoint.dto.ZoneDataDto;
import com.smartroute.smartroute1.service.StravaService;
import com.smartroute.smartroute1.service.impl.StravaOAuthServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/strava")
public class StravaEndpoint {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final StravaOAuthServiceImpl stravaOAuthServiceImpl;
    private final StravaService stravaService;
    @Value("${strava.client.id}")
    private String clientId;
    @Value("${app.baseUrl}")
    private String baseUrl;
    @Value("${app.frontendUrl}")
    private String frontendUrl;

    @GetMapping("/connect")
    @ResponseStatus(HttpStatus.OK)
    public void connect(HttpServletResponse res) throws IOException {
        String redirectUri = baseUrl + "/api/v1/strava/callback";
        String scopes = "activity:read_all,profile:read_all";
        String url = UriComponentsBuilder.fromUriString("https://www.strava.com/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", scopes)
                .queryParam("approval_prompt", "auto")
                .build().toUriString();
        res.sendRedirect(url);
    }

    @Secured("ROLE_USER")
    @GetMapping("/callback")
    @ResponseStatus(HttpStatus.OK)
    public String callback(@RequestParam("code") String code, @RequestParam("scope") String scope) {
        LOGGER.info("Received code {}, scope {}", code, scope);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        stravaOAuthServiceImpl.exchangeCodeForToken(code, scope, authentication.getName());

        stravaService.importStravaZoneData(authentication.getName());
        stravaService.importStravaActivities(authentication.getName());

        return "redirect:" + frontendUrl + "/connected";
    }

    @Operation(
            summary = "Import Strava zone data",
            description = "Fetches the athlete’s current heart rate zones from the Strava API and returns them. "
    )
    @Secured("ROLE_USER")
    @GetMapping("/zones")
    @ResponseStatus(HttpStatus.OK)
    public ZoneDataDto getZones() {
        LOGGER.info("GET /api/v1/strava/zones");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return stravaService.importStravaZoneData(authentication.getName());
    }

    @Operation(
            summary = "Import Strava activities",
            description = "Fetches the authenticated user’s latest activities from the Strava API. "
    )
    @Secured("ROLE_USER")
    @GetMapping("/activities")
    @ResponseStatus(HttpStatus.OK)
    public List<StravaActivityDto> getActivities() {
        LOGGER.info("GET /api/v1/strava/activities");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return stravaService.importStravaActivities(authentication.getName());
    }
}
