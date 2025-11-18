package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.AthleteDetailDto;
import com.smartroute.smartroute1.endpoint.dto.StravaActivityDto;
import com.smartroute.smartroute1.endpoint.dto.ZoneDataDto;
import com.smartroute.smartroute1.endpoint.mapper.StravaActivityMapper;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.AthleteDetail;
import com.smartroute.smartroute1.entity.StravaAccount;
import com.smartroute.smartroute1.entity.StravaActivity;
import com.smartroute.smartroute1.entity.StravaZone;
import com.smartroute.smartroute1.exception.StravaAuthorizationException;
import com.smartroute.smartroute1.repository.AthleteDetailRepository;
import com.smartroute.smartroute1.repository.StravaAccountRepository;
import com.smartroute.smartroute1.repository.StravaActivityRepository;
import com.smartroute.smartroute1.repository.StravaZoneRepository;
import com.smartroute.smartroute1.repository.UserRepository;
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
    private final StravaZoneRepository stravaZoneRepository;
    private final StravaActivityRepository stravaActivityRepository;
    private final AthleteDetailRepository athleteDetailRepository;
    private final StravaActivityMapper activityMapper;

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

        LOGGER.debug("Number of imported Strava activities: {}", activities.size());
        saveImportedActivities(activities, account);

        return activities;
    }

    @Transactional
    protected void saveImportedActivities(List<StravaActivityDto> stravaActivities, StravaAccount account) {
        if (stravaActivities == null) {
            return;
        }

        for (StravaActivityDto dto : stravaActivities) {
            StravaActivity existing = stravaActivityRepository
                    .findById(dto.getId())
                    .orElse(null);

            StravaActivity entity = activityMapper.dtoToEntity(dto, existing, account);

            stravaActivityRepository.save(entity);
            LOGGER.debug("Saved Strava activity: {}", entity);
        }
    }

    @Override
    @Transactional
    public ZoneDataDto importStravaZoneData(String email) {
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

        ZoneDataDto zones;
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
                    .bodyToMono(ZoneDataDto.class)
                    .block();
        } catch (WebClientRequestException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Strava API unavailable " + e.getMessage());
        } catch (WebClientResponseException e) {
            throw new ResponseStatusException(e.getStatusCode(), "Strava API error: " + e.getResponseBodyAsString(), e);
        }

        LOGGER.debug("Imported Strava zones: {}", zones);
        saveImportedZones(zones, account);

        return zones;
    }

    @Transactional
    protected void saveImportedZones(ZoneDataDto zones, StravaAccount account) {
        stravaZoneRepository.deleteAllByStravaAccount(account);

        if (zones.getHeartRate() != null && zones.getHeartRate().getZones() != null) {
            int index = 1;
            for (ZoneDataDto.Zone zone : zones.getHeartRate().getZones()) {
                StravaZone entity = new StravaZone();
                entity.setZoneIndex(index++);
                entity.setMin(zone.getMin());
                entity.setMax(zone.getMax());
                entity.setCustom(zones.getHeartRate().getCustomZones());
                entity.setStravaAccount(account);
                stravaZoneRepository.save(entity);
                LOGGER.debug("Saved Strava zone: {}", entity);
            }
        }
    }

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

        LOGGER.debug("Imported Strava athlete details: {}", athleteDetail);
        saveAthleteDetail(athleteDetail, account);

        return athleteDetail;
    }

    @Override
    public List<StravaActivity> getStravaActivities(String email) {

        LOGGER.trace("Get Strava activities for user with mail: {}", email);
        ApplicationUser user = userRepository.findUserByEmail(email);
        Optional<StravaAccount> accountOpt = stravaAccountRepository.findByUser(user);
        if (accountOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No linked Strava account found");
        }
        StravaAccount account = accountOpt.get();

        return stravaActivityRepository.findByStravaAccount(account);

    }

    @Override
    public StravaActivity getStravaActivity(String email, long id) {
        LOGGER.trace("Get Strava activities for user with mail: {}", email);
        ApplicationUser user = userRepository.findUserByEmail(email);
        Optional<StravaAccount> accountOpt = stravaAccountRepository.findByUser(user);
        if (accountOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No linked Strava account found");
        }
        StravaAccount account = accountOpt.get();

        StravaActivity act = stravaActivityRepository.findByIdAndStravaAccount(id, account);
        if (act == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Strava activity not found");
        }
        return act;
    }

    @Transactional
    protected void saveAthleteDetail(AthleteDetailDto athleteDetailDto, StravaAccount account) {
        AthleteDetail athleteDetail = athleteDetailRepository.findByStravaAccount(account)
                .orElseGet(() -> {
                    AthleteDetail newDetail = new AthleteDetail();
                    newDetail.setStravaAccount(account);
                    return newDetail;
                });

        athleteDetail.setSex(athleteDetailDto.getSex());
        athleteDetail.setFtp(athleteDetailDto.getFtp());
        athleteDetail.setWeight(athleteDetailDto.getWeight());
        athleteDetailRepository.save(athleteDetail);
    }
}
