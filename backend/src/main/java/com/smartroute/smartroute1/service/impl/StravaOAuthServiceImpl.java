package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.StravaTokenResponseDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.StravaAccount;
import com.smartroute.smartroute1.repository.StravaAccountRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.StravaOAuthService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StravaOAuthServiceImpl implements StravaOAuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final WebClient webClient;
    private final UserRepository userRepository;
    private final StravaAccountRepository stravaAccountRepository;
    @Value("${strava.client.id}")
    private String clientId;
    @Value("${strava.client.secret}")
    private String clientSecret;
    @Value("${app.baseUrl}")
    private String baseUrl;

    public StravaTokenResponseDto exchangeCodeForToken(String code, String scope, String email) {
        LOGGER.trace("Excchanging code: {} for token with scopes: {} for user with email: {}", code, scope, email);
        try {
            StravaTokenResponseDto dto = webClient.post()
                    .uri("https://www.strava.com/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("client_id", clientId)
                            .with("client_secret", clientSecret)
                            .with("code", code)
                            .with("grant_type", "authorization_code")
                            .with("redirect_uri", baseUrl + "/api/v1/strava/callback"))
                    .retrieve()
                    .bodyToMono(StravaTokenResponseDto.class)
                    .block();

            if (dto != null) {
                dto.setScope(scope);
            }
            createOrUpdateStravaAccount(dto, email);
            return dto;
        } catch (WebClientResponseException e) {
            throw new ResponseStatusException(
                    e.getStatusCode(),
                    "Strava OAuth Error: " + e.getResponseBodyAsString(),
                    e);
        } catch (WebClientRequestException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Strava not available", e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unknown Strava OAuth Error", e);
        }
    }

    private void createOrUpdateStravaAccount(StravaTokenResponseDto dto, String email) {
        ApplicationUser user = userRepository.findUserByEmail(email);

        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Strava Token Response null");
        }

        Optional<StravaAccount> existing = stravaAccountRepository.findByUser(user);
        if (existing.isPresent()) {
            existing.get().setAccessToken(dto.getAccessToken());
            existing.get().setRefreshToken(dto.getRefreshToken());

            stravaAccountRepository.save(existing.get());
            LOGGER.debug("Updated Strava account connection: {}", existing.get());
        } else {
            StravaAccount account = new StravaAccount();
            account.setAccessToken(dto.getAccessToken());
            account.setRefreshToken(dto.getRefreshToken());
            account.setExpiresAt(Instant.ofEpochSecond(dto.getExpiresAt()));
            account.setAthleteId(dto.getAthleteId());
            account.setUser(user);
            account.setScopes(dto.getScope());
            account.setConnectedAt(Instant.now());

            stravaAccountRepository.save(account);
            LOGGER.debug("Created new Strava account connection: {}", account);
        }
    }

    public synchronized String ensureValidAccessToken(StravaAccount account) {
        LOGGER.trace("Ensure access token for user: {}", account);
        if (account.getExpiresAt().isBefore(Instant.now().minusSeconds(30))) {
            var resp = refreshAccessToken(account.getRefreshToken());
            account.setAccessToken(resp.getAccessToken());
            account.setRefreshToken(resp.getRefreshToken());
            account.setExpiresAt(Instant.ofEpochSecond(resp.getExpiresAt()));
            stravaAccountRepository.save(account);
            LOGGER.debug("Updated Strava account connection: {}", account);
        }
        return account.getAccessToken();
    }

    private StravaTokenResponseDto refreshAccessToken(String refreshToken) {
        LOGGER.debug("Refresh access token for user: {}", refreshToken);
        return webClient.post()
                .uri("https://www.strava.com/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("client_id", clientId)
                        .with("client_secret", clientSecret)
                        .with("grant_type", "refresh_token")
                        .with("refresh_token", refreshToken))
                .retrieve()
                .bodyToMono(StravaTokenResponseDto.class)
                .block();
    }
}