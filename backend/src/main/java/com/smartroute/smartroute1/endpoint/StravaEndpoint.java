package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.AthleteDetailDto;
import com.smartroute.smartroute1.endpoint.dto.StravaActivityDto;
import com.smartroute.smartroute1.endpoint.dto.ZoneDataDto;
import com.smartroute.smartroute1.service.StravaService;
import com.smartroute.smartroute1.service.impl.StravaOauthServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
                
                2. Open the following URL in your browser:
                   `GET /api/v1/strava/connect`
                    You will be redirected to Strava's authorization screen.
                
                3. Log into your Strava account and approve permissions.
                
                4. After approval, Strava redirects back to:
                
                   ```
                   {backendBaseUrl}/api/v1/strava/callback?code=...&scope=...
                   ```
                
                   However, this request **fails authentication** because the browser does not
                   include your Bearer token.
                
                5. Make a manual GET request to the /callback URL.
                   Copy the `code` and `scope` parameters from the redirect URL and include the previously generated Bearer token
                   in your request:
                
                    ```
                    Authorization: Bearer <jwt>
                    ```
                
                    ```
                    GET {backendBaseUrl}/api/v1/strava/callback?code=XXX&scope=YYY
                    ```
                
                6. The backend processes the code, exchanges it for tokens, links your 
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
    @ResponseStatus(value = HttpStatus.FOUND)
    public void connect(HttpServletResponse res) throws IOException {
        LOGGER.info("GET /api/v1/strava/connect");
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

    @Operation(
            summary = "Handle Strava OAuth callback",
            description = "Handles the callback from Strava after user authorization. "
                    + "Exchanges the received authorization code for an access token, "
                    + "stores the Strava account connection, and triggers an initial data import "
                    + "(zones and activities)."
    )
    @Secured("ROLE_USER")
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam("code") String code, @RequestParam("scope") String scope) {
        LOGGER.info("GET /api/v1/strava/callback code: {}, scope: {}", code, scope);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        authService.exchangeCodeForToken(code, scope, email);

        stravaService.importStravaZoneData(email);
        stravaService.importStravaActivities(email);
        stravaService.importStravaAthlete(email);

        URI redirectUri = URI.create(frontendUrl + "/connected");

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(redirectUri)
                .build();
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
