package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.StravaActivityDto;
import com.smartroute.smartroute1.endpoint.dto.ZoneDataDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.StravaAccount;
import com.smartroute.smartroute1.entity.StravaActivity;
import com.smartroute.smartroute1.entity.StravaZone;
import com.smartroute.smartroute1.repository.StravaAccountRepository;
import com.smartroute.smartroute1.repository.StravaActivityRepository;
import com.smartroute.smartroute1.repository.StravaZoneRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.StravaOAuthService;
import com.smartroute.smartroute1.service.StravaService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StravaServiceImpl implements StravaService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final StravaAccountRepository stravaAccountRepository;
    private final UserRepository userRepository;
    private final StravaOAuthService stravaOAuthService;
    private final WebClient webClient;
    private final StravaZoneRepository stravaZoneRepository;
    private final StravaActivityRepository stravaActivityRepository;

    @Override
    public List<StravaActivityDto> importStravaActivities(String email) {
        LOGGER.trace("Import Strava activities for user with mail: {}", email);
        ApplicationUser user = userRepository.findUserByEmail(email);
        Optional<StravaAccount> accountOpt = stravaAccountRepository.findByUser(user);
        if (accountOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No linked Strava account found");
        }

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString("https://www.strava.com/api/v3/athlete/activities")
                .queryParam("per_page", 45);

        StravaAccount account = accountOpt.get();
        String token = stravaOAuthService.ensureValidAccessToken(account);

        List<StravaActivityDto> activities = webClient.get()
                .uri(builder.build().toUri())
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .bodyToFlux(StravaActivityDto.class)
                .collectList()
                .block();

        LOGGER.debug("Imported Strava activities: {}", activities);

        saveImportedActivities(activities, account);

        return activities;
    }

    private void saveImportedActivities(List<StravaActivityDto> stravaActivities, StravaAccount account) {
        if (stravaActivities == null) {
            return;
        }

        for (StravaActivityDto dto : stravaActivities) {
            if (stravaActivityRepository.existsById(dto.getId())){
                continue;
            }
            StravaActivity entity = new StravaActivity();
            entity.setId(dto.getId());
            entity.setName(dto.getName());
            entity.setDistance(dto.getDistance());
            entity.setMovingTime(dto.getMovingTime());
            entity.setElapsedTime(dto.getElapsedTime());
            entity.setTotalElevationGain(dto.getTotalElevationGain());
            entity.setType(dto.getType());
            entity.setSportType(dto.getSportType());
            entity.setStartDate(dto.getStartDate());
            entity.setStartDateLocal(dto.getStartDateLocal());
            entity.setAverageSpeed(dto.getAverageSpeed());
            entity.setMaxSpeed(dto.getMaxSpeed());
            entity.setAverageHeartrate(dto.getAverageHeartrate());
            entity.setMaxHeartrate(dto.getMaxHeartrate());
            entity.setAverageWatts(dto.getAverageWatts());
            entity.setKilojoules(dto.getKilojoules());
            entity.setKudosCount(dto.getKudosCount());
            entity.setStravaAccount(account);
            stravaActivityRepository.save(entity);
            LOGGER.debug("Saved Strava activity: {}", entity);
        }
    }

    @Override
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
        String token = stravaOAuthService.ensureValidAccessToken(account);

        ZoneDataDto zones = webClient.get()
                .uri(builder.build().toUri())
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .bodyToMono(ZoneDataDto.class)
                .block();

        LOGGER.debug("Imported Strava zones: {}", zones);

        saveImportedZones(zones, account);

        return zones;
    }

    private void saveImportedZones(ZoneDataDto zones, StravaAccount account) {
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
}
