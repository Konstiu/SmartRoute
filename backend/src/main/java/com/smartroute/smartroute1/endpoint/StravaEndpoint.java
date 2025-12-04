package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.AthleteDetailDto;
import com.smartroute.smartroute1.endpoint.dto.StravaAccountConnectionStateDto;
import com.smartroute.smartroute1.endpoint.dto.StravaActivityDto;
import com.smartroute.smartroute1.endpoint.dto.StravaZoneDataDto;
import com.smartroute.smartroute1.service.StravaOauthService;
import com.smartroute.smartroute1.service.StravaService;
import com.smartroute.smartroute1.service.impl.StravaOauthServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/strava")
@Tag(
        name = "Strava OAuth",
        description = """
                ### How to connect your Strava Account Without a Frontend
                
                Strava uses an OAuth2 Authorization Code Flow that requires browser redirects.
                Because Swagger or direct API calls cannot handle this flow, you need to perform the connection manually.
                
                ---
                
                ## Requirements
                
                Ensure the following values are correctly configured in `application-secrets.properties`:
                
                - `strava.client.id`
                - `strava.client.secret`
                
                ---
                
                ## Connect a Strava account to a user
                
                1. Authenticate your API user
                    Obtain a Bearer token (JWT) by sending:
                
                    ```
                    POST {backendBaseUrl}/api/v1/authentication
                    ```
                
                    with a valid request body.
                
                    Use any tool like **Postman**, **Insomnia**, or **cURL**.
                
                2. Send the following request:
                   `GET /api/v1/strava/connect?origin=register`
                    With the Bearer Token from step 1.
                
                    You will get a URL which links to the Strava's authorization screen.
                    The frontend would automatically redirect you to this page.
                    Open the link manually.
                
                3. Log into your Strava account and approve permissions.
                
                4. After approval, Strava redirects back to:
                
                   ```
                   {backendBaseUrl}/api/v1/strava/callback?code=...&scope=...
                   ```
                
                   The backend processes the code, exchanges it for tokens, links your 
                   Strava account, and imports:
                   - Athlete Profile  
                   - Activities  
                   - Heart Rate Zones  
                
                   If no frontend exists, the redirect will simply show a blank page.  
                   Connection still succeeds.
                
                """
)
public class StravaEndpoint {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final StravaOauthServiceImpl authService;
    private final StravaService stravaService;
    @Value("${strava.client.id}")
    private String clientId;
    @Value("${app.baseUrl}")
    private String baseUrl;
    @Value("${app.frontendUrl}")
    private String frontendUrl;

    @Operation(
            summary = "Start Strava OAuth connection",
            description = "Redirects the authenticated user to Strava’s authorization page to connect their Strava account. "
                    + "The user will be prompted to grant access to read activity and profile data."
    )
    @GetMapping("/connect")
    @Secured("ROLE_USER")
    @ResponseStatus(value = HttpStatus.FOUND)
    public ResponseEntity<String> connect(@RequestParam("origin") String origin) {
        LOGGER.info("GET /api/v1/strava/connect");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        String state = authService.createState(email, origin);

        String redirectUri = baseUrl + "/api/v1/strava/callback";
        String scopes = "activity:read_all,profile:read_all";
        String url = UriComponentsBuilder.fromUriString("https://www.strava.com/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", scopes)
                .queryParam("approval_prompt", "auto")
                .queryParam("state", state)
                .build().toUriString();

        return ResponseEntity.ok(url);
    }

    @Operation(
            summary = "Handle Strava OAuth callback",
            description = "Handles the callback from Strava after user authorization. "
                    + "Exchanges the received authorization code for an access token, "
                    + "stores the Strava account connection, and triggers an initial data import "
                    + "(zones and activities)."
    )
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam("code") String code, @RequestParam("scope") String scope, @RequestParam("state") String state) {
        LOGGER.info("GET /api/v1/strava/callback code: {}, scope: {}, state: {}", code, scope, state);

        StravaOauthService.StravaOauthState stravaOauthState = authService.getState(state);
        String email = stravaOauthState.email;
        String origin = stravaOauthState.origin;

        if (email == null || origin == null || email.equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        authService.exchangeCodeForToken(code, scope, email);

        if (scope.contains("activity:read_all")) {
            stravaService.importStravaActivities(email, 50);
        }
        if (scope.contains("profile:read_all")) {
            stravaService.importStravaZoneData(email);
        }

        stravaService.importStravaAthlete(email);

        URI redirectUri = URI.create(frontendUrl + "/" + origin);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(redirectUri)
                .build();
    }

    @Operation(
            summary = "Returns connection state information for an authenticated user",
            description = "Returns a StravaAccountConnectionStateDto containing information about the "
                    + "connection status and the granted Strava API scopes."
    )
    @GetMapping("/connection-state")
    @Secured("ROLE_USER")
    @ResponseStatus(value = HttpStatus.OK)
    public StravaAccountConnectionStateDto getConnectionState() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        return authService.getConnectionState(email);
    }

    @Operation(
            summary = "Disconnects a Strava Account",
            description = "Revokes the access to the Strava athlete data, deletes the StravaAccount entry in the database"
                    + " and returns a StravaAccountConnectionStateDto containing information about the connection status "
                    + "and the granted Strava API scopes."
    )
    @DeleteMapping("/disconnect")
    @Secured("ROLE_USER")
    @ResponseStatus(value = HttpStatus.OK)
    public StravaAccountConnectionStateDto disconnect() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        return authService.disconnectStravaAccount(email);
    }

    @Operation(
            summary = "Import Strava zone data",
            description = "Fetches the athlete’s current heart rate zones from the Strava API and returns them. "
    )
    @Secured("ROLE_USER")
    @GetMapping("/zones")
    @ResponseStatus(HttpStatus.OK)
    public StravaZoneDataDto getZones() {
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
        return stravaService.importStravaActivities(authentication.getName(), 50);
    }

    @Operation(
            summary = "Import Strava athlete",
            description = "Fetches the authenticated user’s athlete details from the Strava API. "
    )
    @Secured("ROLE_USER")
    @GetMapping("/athlete")
    @ResponseStatus(HttpStatus.OK)
    public AthleteDetailDto getAthlete() {
        LOGGER.info("GET /api/v1/strava/athlete");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return stravaService.importStravaAthlete(authentication.getName());
    }
}
