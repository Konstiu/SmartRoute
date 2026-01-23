package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.StravaStreamDto;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.AthleteZone;
import com.smartroute.smartroute1.entity.enums.Sex;
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

    private static final int K1_RIDE = 12;
    private static final int K2_RIDE = 24;
    private static final int K3_RIDE = 45;
    private static final int K4_RIDE = 100;
    private static final int K5_RIDE = 120;

    private static final int K1_WALK = 20;
    private static final int K2_WALK = 40;
    private static final int K3_WALK = 80;
    private static final int K4_WALK = 160;
    private static final int K5_WALK = 320;

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

            trimp = calculateTrimp(timeInHrZones, activity.getSportType());

            return Math.round(trimp * elevationFactor);
        } catch (NoSuchElementException e) {
            // Fall back to distance/time method
            return calculateSessionLoad(activity.getDistance(), activity.getMovingTime(), activity.getTotalElevationGain(), activity.getSportType());
        }
    }

    @Override
    public Integer calculateSessionLoad(List<Float> heartRates, List<Float> timestamps, Activity activity) {
        LOGGER.trace("calculateSessionLoad({}, {}, {})", heartRates, timestamps, activity);

        float elevationFactor = 1 + ELEVATION_COEFFICIENT * activity.getTotalElevationGain() / 100;
        int trimp;
        try {
            Map<Integer, Float> timeInHrZones;
            timeInHrZones = calculateTimeInZones(heartRates, timestamps, activity.getUser());
            trimp = calculateTrimp(timeInHrZones, activity.getSportType());
            return Math.round(trimp * elevationFactor);
        } catch (NoSuchElementException e) {
            // Fall back to distance/time method
            return calculateSessionLoad(activity.getDistance(), activity.getMovingTime(), activity.getTotalElevationGain(), activity.getSportType());
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
    public Integer calculateSessionLoad(float distance, int movingTime, float totalElevationGain, String activityType) {
        LOGGER.trace("calculateSessionLoad({},{},{})", distance, movingTime, totalElevationGain);

        float elevationFactor = 1 + ELEVATION_COEFFICIENT * totalElevationGain / 100;
        float load = distance * movingTime / 12;
        if (activityType.equals("Ride")) {
            return Math.round(load * .25f * elevationFactor);
        }
        return Math.round(load * elevationFactor);
    }

    private int calculateTrimp(Map<Integer, Float> timeInZones, String activityType) {
        float trimp = 0;
        for (Map.Entry<Integer, Float> entry : timeInZones.entrySet()) {
            float timeInZone = entry.getValue();
            int coefficient;

            if (activityType.equals("Run")) {
                coefficient = switch (entry.getKey()) {
                    case 1 -> K1;
                    case 2 -> K2;
                    case 3 -> K3;
                    case 4 -> K4;
                    case 5 -> K5;
                    default -> 0;
                };
            } else if (activityType.equals("Ride")) {
                coefficient = switch (entry.getKey()) {
                    case 1 -> K1_RIDE;
                    case 2 -> K2_RIDE;
                    case 3 -> K3_RIDE;
                    case 4 -> K4_RIDE;
                    case 5 -> K5_RIDE;
                    default -> 0;
                };
            } else {
                coefficient = switch (entry.getKey()) {
                    case 1 -> K1_WALK;
                    case 2 -> K2_WALK;
                    case 3 -> K3_WALK;
                    case 4 -> K4_WALK;
                    case 5 -> K5_WALK;
                    default -> 0;
                };
            }


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

    @Override
    public Map<Integer, Float> calculateTimeInZones(List<StravaStreamDto> stravaStreams, ApplicationUser user) {
        LOGGER.trace("calculateTimeInZones({}, {})", stravaStreams, user);

        // Find all zones for the athlete and fall back to zone calculation if missing
        List<AthleteZone> zones = getUserTimeZonesOrFallbackToApproximation(user, approximateMaxHr(user));

        // Get data
        StravaStreamDto heartRateStream = stravaStreams.stream().filter(s -> Objects.equals(s.getType(), "heartrate")).findFirst().orElseThrow();
        List<Float> heartRateData = heartRateStream.getData();
        StravaStreamDto timeStream = stravaStreams.stream().filter(s -> Objects.equals(s.getType(), "time")).findFirst().orElseThrow();
        List<Float> timeData = timeStream.getData();

        // Calculate time in zones
        return getTimeInZonesFromData(heartRateData, timeData, zones);
    }

    @Override
    public Map<Integer, Float> calculateTimeInZones(List<Float> heartRates, List<Float> timeStamps, ApplicationUser user) {
        LOGGER.trace("calculateTimeInZones({}, {}, {})", heartRates, timeStamps, user);
        // Find all zones for the athlete and fall back to zone calculation if missing
        List<AthleteZone> zones = getUserTimeZonesOrFallbackToApproximation(user, approximateMaxHr(user));
        // Calculate time in zones
        return getTimeInZonesFromData(heartRates, timeStamps, zones);
    }

    /*
    Approximates the max heart rate for a user based on their age.
    Returns the maximum of the highest recorded hr in an activity and the estimated max hr (Tanaka formula - lowest mean absolute error in compared formulas:
    https://journals.viamedica.pl/folia_cardiologica/article/view/FC.2022.0057/69749#:~:text=The%20most%20commonly%20used%20formula%20for%20estimating,HRmax%20remains%20direct%20measurement%20during%20maximal%20exertion.)
     */
    private int approximateMaxHr(ApplicationUser user) {
        int age = user.getBirthdate() != null ? Period.between(
            user.getBirthdate(),
            LocalDate.now()
        ).getYears() : 30;

        int maxRecordedHr = (int) Math.round(activityRepository.getActivitiesByUser(user).stream().mapToDouble(
            a -> a.getMaxHeartrate() == null ? -1.0 : a.getMaxHeartrate()
        ).max().orElse(-1.0));

        return Math.max(Math.round(208 - .7f * age), maxRecordedHr);
    }

    // Helper method to calculate time in zones from heart rate and time data
    private Map<Integer, Float> getTimeInZonesFromData(List<Float> heartRates, List<Float> timeStamps, List<AthleteZone> zones) {
        Map<Integer, Float> timeInZonesMap = new HashMap<>();
        // initialize empty zones
        for (int i = 1; i <= 5; i++) {
            timeInZonesMap.put(i, 0f);
        }

        for (int i = 0; i < heartRates.size() - 1; i++) {
            float hr = heartRates.get(i);
            float timeCurrent = timeStamps.get(i);
            float timeNext = timeStamps.get(i + 1);

            float timeDifference = timeNext - timeCurrent;

            AthleteZone zone = findZone(hr, zones);
            if (zone != null) {
                timeInZonesMap.merge(zone.getZoneIndex(), timeDifference, Float::sum);
            }
        }
        return timeInZonesMap;
    }

    // Helper method to get user zones or approximate if not available
    private List<AthleteZone> getUserTimeZonesOrFallbackToApproximation(ApplicationUser user, float maxHeartRate) {
        List<AthleteZone> zones = athleteZoneRepository.findAllByUser(user);
        if (zones.size() != 5) {
            zones = approximateZones(maxHeartRate);
        }
        return zones;
    }

    private List<AthleteZone> approximateZones(float maxHr) {
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
