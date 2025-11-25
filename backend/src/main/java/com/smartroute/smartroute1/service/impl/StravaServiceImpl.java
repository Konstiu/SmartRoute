package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.AthleteDetailDto;
import com.smartroute.smartroute1.endpoint.dto.StravaActivityDto;
import com.smartroute.smartroute1.endpoint.dto.StravaZoneDataDto;
import com.smartroute.smartroute1.endpoint.mapper.StravaActivityMapper;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.StravaAccount;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.AthleteZone;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.exception.StravaAuthorizationException;
import com.smartroute.smartroute1.repository.StravaAccountRepository;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.AthleteZoneRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.ActivityProcessingService;
import com.smartroute.smartroute1.service.StravaOauthService;
import com.smartroute.smartroute1.service.StravaService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StravaServiceImpl implements StravaService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final StravaAccountRepository stravaAccountRepository;
    private final UserRepository userRepository;
    private final StravaOauthService authService;
    private final WebClient webClient;
    private final AthleteZoneRepository athleteZoneRepository;
    private final ActivityRepository activityRepository;
    private final StravaActivityMapper activityMapper;
    private final ActivityProcessingService activityProcessingService;


    @Override
    @Transactional
    public List<StravaActivityDto> importStravaActivities(String email) {
        LOGGER.trace("Import Strava activities for user with mail: {}", email);

        ApplicationUser user = userRepository.findUserByEmail(email);
        Optional<StravaAccount> accountOpt = stravaAccountRepository.findByUser(user);
        if (accountOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No linked Strava account found");
        }

        StravaAccount account = accountOpt.get();

        String token;
        try {
            token = authService.ensureValidAccessToken(account);
        } catch (StravaAuthorizationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Failed to get Strava access token", e);
        }

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString("https://www.strava.com/api/v3/athlete/activities")
                .queryParam("per_page", 45);

        List<StravaActivityDto> activities;
        try {
            activities = webClient.get()
                    .uri(builder.build().toUri())
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new ResponseStatusException(
                                            HttpStatus.BAD_REQUEST, "Strava API 4xx: " + body
                                    )))
                    )
                    .onStatus(HttpStatusCode::is5xxServerError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new ResponseStatusException(
                                            HttpStatus.BAD_GATEWAY, "Strava API 5xx: " + body
                                    )))
                    )
                    .bodyToFlux(StravaActivityDto.class)
                    .collectList()
                    .block();
        } catch (WebClientRequestException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Strava API unavailable " + e.getMessage());
        } catch (WebClientResponseException e) {
            throw new ResponseStatusException(e.getStatusCode(), "Strava API error: " + e.getResponseBodyAsString(), e);
        }

        List<Activity> savedActivities = saveImportedActivities(activities, user);

        activityProcessingService.fetchHeartRateDataForActivities(45, savedActivities, token);

        return activities;
    }

    private List<Activity> saveImportedActivities(List<StravaActivityDto> stravaActivities, ApplicationUser user) {
        List<Activity> activities = new ArrayList<>();
        if (stravaActivities == null) {
            return activities;
        }

        LOGGER.info("Dtos: {}", stravaActivities);
        for (StravaActivityDto dto : stravaActivities) {
            LOGGER.info("Dto: {}", dto);
            Activity existing = activityRepository.findByStravaId(dto.getStravaId())
                    .orElse(null);

            Activity entity = activityMapper.dtoToEntity(dto, existing, user);
            if (entity == null) {
                LOGGER.error("Error mapping entity to activity {}", dto);
                continue;
            }
            activityRepository.save(entity);
            activities.add(entity);
            LOGGER.debug("Saved Strava activity: {}", entity);
        }
        return activities;
    }

    @Override
    @Transactional
    public StravaZoneDataDto importStravaZoneData(String email) {
        LOGGER.trace("Import Strava zone data for user with mail: {}", email);

        ApplicationUser user = userRepository.findUserByEmail(email);
        Optional<StravaAccount> accountOpt = stravaAccountRepository.findByUser(user);
        if (accountOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No linked Strava account found");
        }

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString("https://www.strava.com/api/v3/athlete/zones");

        StravaAccount account = accountOpt.get();

        String token;
        try {
            token = authService.ensureValidAccessToken(account);
        } catch (StravaAuthorizationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Failed to get Strava access token", e);
        }

        StravaZoneDataDto zones;
        try {
            zones = webClient.get()
                    .uri(builder.build().toUri())
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new ResponseStatusException(
                                            HttpStatus.BAD_REQUEST, "Strava API 4xx: " + body
                                    )))
                    )
                    .onStatus(HttpStatusCode::is5xxServerError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new ResponseStatusException(
                                            HttpStatus.BAD_GATEWAY, "Strava API 5xx: " + body
                                    )))
                    )
                    .bodyToMono(StravaZoneDataDto.class)
                    .block();
        } catch (WebClientRequestException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Strava API unavailable " + e.getMessage());
        } catch (WebClientResponseException e) {
            throw new ResponseStatusException(e.getStatusCode(), "Strava API error: " + e.getResponseBodyAsString(), e);
        }

        if (zones != null) {
            LOGGER.debug("Imported Strava zones: {}", zones);
        }

        saveImportedZones(zones, user);

        return zones;
    }

    private void saveImportedZones(StravaZoneDataDto zones, ApplicationUser user) {
        if (zones == null) {
            return;
        }

        athleteZoneRepository.deleteAllByUser(user);

        if (zones.getHeartRate() != null && zones.getHeartRate().getZones() != null) {
            int index = 1;
            for (StravaZoneDataDto.Zone zone : zones.getHeartRate().getZones()) {
                AthleteZone entity = new AthleteZone();
                entity.setZoneIndex(index++);
                entity.setMin(zone.getMin());
                entity.setMax(zone.getMax());
                entity.setCustom(zones.getHeartRate().getCustomZones());
                entity.setUser(user);
                athleteZoneRepository.save(entity);
                LOGGER.debug("Saved Strava zone: {}", entity);
            }
        }
    }

    @Override
    @Transactional
    public AthleteDetailDto importStravaAthlete(String email) {
        LOGGER.trace("Import Strava athlete data for user with mail: {}", email);

        ApplicationUser user = userRepository.findUserByEmail(email);
        Optional<StravaAccount> accountOpt = stravaAccountRepository.findByUser(user);
        if (accountOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No linked Strava account found");
        }

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString("https://www.strava.com/api/v3/athlete");

        StravaAccount account = accountOpt.get();

        String token;
        try {
            token = authService.ensureValidAccessToken(account);
        } catch (StravaAuthorizationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Failed to get Strava access token", e);
        }

        AthleteDetailDto athleteDetail;
        try {
            athleteDetail = webClient.get()
                    .uri(builder.build().toUri())
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new ResponseStatusException(
                                            HttpStatus.BAD_REQUEST, "Strava API 4xx: " + body
                                    )))
                    )
                    .onStatus(HttpStatusCode::is5xxServerError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new ResponseStatusException(
                                            HttpStatus.BAD_GATEWAY, "Strava API 5xx: " + body
                                    )))
                    )
                    .bodyToMono(AthleteDetailDto.class)
                    .block();
        } catch (WebClientRequestException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Strava API unavailable " + e.getMessage());
        } catch (WebClientResponseException e) {
            throw new ResponseStatusException(e.getStatusCode(), "Strava API error: " + e.getResponseBodyAsString(), e);
        }

        if (athleteDetail != null) {
            LOGGER.debug("Imported Strava athlete details: {}", athleteDetail);
        }

        saveAthleteDetail(athleteDetail, user);

        return athleteDetail;
    }

    private void saveAthleteDetail(AthleteDetailDto athleteDetailDto, ApplicationUser user) {
        if (athleteDetailDto == null) {
            return;
        }

        // Sets user data. Manual input has priority and won't be overwritten.
        if (user.getSex() == null) {
            Sex sex = switch (athleteDetailDto.getSex()) {
                case "M" -> Sex.MALE;
                case "F" -> Sex.FEMALE;
                default -> Sex.OTHER;
            };
            user.setSex(sex);
        }

        if (user.getWeight() == null) {
            user.setWeight(BigDecimal.valueOf(athleteDetailDto.getWeight()));
        }

        user.setFtp(athleteDetailDto.getFtp());

        userRepository.save(user);
    }
}
