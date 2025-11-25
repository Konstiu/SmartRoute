package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.StravaStreamDto;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.AthleteZone;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.AthleteZoneRepository;
import com.smartroute.smartroute1.service.FitnessScoreService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FitnessScoreServiceImpl implements FitnessScoreService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final AthleteZoneRepository athleteZoneRepository;
    private final ActivityRepository activityRepository;

    // Weights for sessionLoad calculation according to:
    // https://djconnel.blogspot.com/2011/08/strava-suffer-score-decoded.html
    private static final int K1 = 30;
    private static final int K2 = 60;
    private static final int K3 = 120;
    private static final int K4 = 240;
    private static final int K5 = 480;

    private static final float ELEVATION_COEFFICIENT = 0.05f;
    private static final float TIME_MODIFIER = 75;

    @Override
    public int calculateFitnessScore(Instant day, ApplicationUser user) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate localDate = day.atZone(zone).toLocalDate();

        Instant startOfDay = localDate.atStartOfDay(zone).toInstant();
        Instant endOfDay = localDate.plusDays(1).atStartOfDay(zone).toInstant();

        Integer sum = activityRepository.sumSessionLoadForDay(user, "Run", startOfDay, endOfDay);

        return sum == null ? 0 : sum;
    }

    @Override
    public Integer calculateSessionLoad(int sufferScore, float totalElevationGain) {
        LOGGER.trace("calculateSessionLoad({}, {})", sufferScore, totalElevationGain);
        float elevationFactor = 1 + ELEVATION_COEFFICIENT * totalElevationGain / 100;
        return Math.round(sufferScore * elevationFactor);
    }

    @Override
    public Integer calculateSessionLoad(List<StravaStreamDto> heartRateStream, Activity activity) {
        LOGGER.trace("calculateSessionLoad({}, {})", heartRateStream, activity);

        float elevationFactor = 1 + ELEVATION_COEFFICIENT * activity.getTotalElevationGain() / 100;
        int trimp;

        try {
            Map<Integer, Float> timeInHrZones;

            timeInHrZones = calculateTimeInZones(heartRateStream, activity.getUser());

            trimp = calculateTrimp(timeInHrZones);

            return Math.round(trimp * elevationFactor);
        } catch (NoSuchElementException e) {
            // Fall back to distance/time method
            return calculateSessionLoad(activity.getDistance(), activity.getMovingTime(), activity.getTotalElevationGain());
        }
    }

    @Override
    public Integer calculateSessionLoad(int ftp, int movingTime, float averageWatts, float totalElevationGain) {
        LOGGER.trace("calculateSessionLoad({}, {}, {}, {})", ftp, movingTime, averageWatts, totalElevationGain);

        float elevationFactor = 1 + ELEVATION_COEFFICIENT * totalElevationGain / 100;
        float i = averageWatts / ftp;
        float load = (float) ((float) movingTime / 3600 * Math.pow(i, 2) * 100);
        return Math.round(load * elevationFactor);
    }

    @Override
    public Integer calculateSessionLoad(float kilojoules, float weight, float totalElevationGain) {
        LOGGER.trace("calculateSessionLoad({}, {}, {})", kilojoules, weight, totalElevationGain);

        float elevationFactor = 1 + ELEVATION_COEFFICIENT * totalElevationGain / 100;
        float load = kilojoules / weight;
        return Math.round(load * elevationFactor);
    }

    @Override
    public Integer calculateSessionLoad(float distance, int movingTime, float totalElevationGain) {
        LOGGER.trace("calculateSessionLoad({},{},{})", distance, movingTime, totalElevationGain);

        float elevationFactor = 1 + ELEVATION_COEFFICIENT * totalElevationGain / 100;
        float load = distance * movingTime / 12;
        return Math.round(load * elevationFactor);
    }

    private int calculateTrimp(Map<Integer, Float> timeInZones) {
        float trimp = 0;
        for (Map.Entry<Integer, Float> entry : timeInZones.entrySet()) {
            float timeInZone = entry.getValue();
            int coefficient = switch (entry.getKey()) {
                case 1 -> K1;
                case 2 -> K2;
                case 3 -> K3;
                case 4 -> K4;
                case 5 -> K5;
                default -> 0;
            };

            // Reduce weight for Zone 1 and 2 for shorter activities
            float timeCoefficient = 1;
            if (coefficient == K1 || coefficient == K2) {
                timeCoefficient = Math.min(1, timeInZone / 60 / TIME_MODIFIER);
            }

            //TRIMP = Sum (K_i * TK_i * t_i), with t_i in hours
            trimp += coefficient * timeCoefficient * (timeInZone / 3600);
        }
        return Math.round(trimp);
    }

    private Map<Integer, Float> calculateTimeInZones(List<StravaStreamDto> stravaStreams, ApplicationUser user) {
        LOGGER.trace("calculateTimeInZones({}, {})", stravaStreams, user);

        // Find all zones for the athlete and fall back to zone calculation if missing
        List<AthleteZone> zones = athleteZoneRepository.findAllByUser(user);
        if (zones.size() != 5) {
            int age = Period.between(
                    user.getBirthdate(),
                    LocalDate.now()
            ).getYears();
            int maxHr = 220 - age;
            zones = approximateZones(maxHr);
        }

        StravaStreamDto heartRateStream = stravaStreams.stream().filter(s -> Objects.equals(s.getType(), "heartrate")).findFirst().orElseThrow();
        List<Float> heartRateData = heartRateStream.getData();
        StravaStreamDto timeStream = stravaStreams.stream().filter(s -> Objects.equals(s.getType(), "time")).findFirst().orElseThrow();
        List<Float> timeData = timeStream.getData();

        Map<Integer, Float> timeInZonesMap = new HashMap<>();

        for (int i = 0; i < heartRateData.size() - 1; i++) {
            float hr = heartRateData.get(i);
            float timeCurrent = timeData.get(i);
            float timeNext = timeData.get(i + 1);

            float timeDifference = timeNext - timeCurrent;

            AthleteZone zone = findZone(hr, zones);
            if (zone != null) {
                timeInZonesMap.merge(zone.getZoneIndex(), timeDifference, Float::sum);
            }
        }

        return timeInZonesMap;
    }

    private List<AthleteZone> approximateZones(int maxHr) {
        AthleteZone z1 = new AthleteZone();
        z1.setZoneIndex(1);
        z1.setMin(0);
        z1.setMax((int) (.59 * maxHr));

        AthleteZone z2 = new AthleteZone();
        z2.setZoneIndex(2);
        z2.setMin((int) (.6 * maxHr));
        z2.setMax((int) (.69 * maxHr));

        AthleteZone z3 = new AthleteZone();
        z3.setZoneIndex(3);
        z3.setMin((int) (.7 * maxHr));
        z3.setMax((int) (.79 * maxHr));

        AthleteZone z4 = new AthleteZone();
        z4.setZoneIndex(4);
        z4.setMin((int) (.8 * maxHr));
        z4.setMax((int) (.89 * maxHr));

        AthleteZone z5 = new AthleteZone();
        z5.setZoneIndex(5);
        z5.setMin((int) (.9 * maxHr));
        z5.setMax(-1);

        return new ArrayList<>(List.of(z1, z2, z3, z4, z5));
    }

    private AthleteZone findZone(float hr, List<AthleteZone> zones) {
        return zones.stream()
                .filter(z -> (hr >= z.getMin() && hr <= z.getMax()) || (z.getZoneIndex() == 5 && hr >= z.getMin()))
                .findFirst()
                .orElse(null);
    }

}
