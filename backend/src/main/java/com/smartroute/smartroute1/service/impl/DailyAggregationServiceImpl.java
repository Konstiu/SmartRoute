package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.PplDailyObs;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.GymWorkout;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.DailySummary;
import com.smartroute.smartroute1.repository.GymWorkoutRepository;
import com.smartroute.smartroute1.service.DailyAggregationService;
import com.smartroute.smartroute1.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;
import java.time.ZoneId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.TreeMap;


@Service
@RequiredArgsConstructor
public class DailyAggregationServiceImpl implements DailyAggregationService {

    private final ActivityRepository activityRepository;
    private final GymWorkoutRepository gymWorkoutRepository;
    private final WeatherService weatherService;
    private final ZoneId zoneId = ZoneId.of("Europe/Vienna");
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public List<DailySummary> getDailySummaries(ApplicationUser user, int daysBack) {
        if (daysBack <= 0) {
            return List.of();
        }

        Instant from = LocalDate.now(zoneId)
                .minusDays(daysBack - 1L)
                .atStartOfDay(zoneId)
                .toInstant();

        List<Activity> activities = activityRepository.findRecentActivitiesForUser(user.getId(), from);

        // Aggregate in a stable order
        Map<LocalDate, MutableDaily> perDay = new TreeMap<>();

        for (Activity a : activities) {
            LocalDate day = toLocalDate(a.getStartDateLocal());
            MutableDaily m = perDay.computeIfAbsent(day, d -> new MutableDaily(d));

            m.totalDistance += safeFloat(a.getDistance());
            m.totalMovingTime += safeInt(a.getMovingTime());
            m.totalElevation += safeFloat(a.getTotalElevationGain());

            int load = pickLoad(a);
            m.totalLoad += load;

            if (a.getAverageHeartrate() != null || a.getMaxHeartrate() != null) {
                m.hasHeartRate = true;
            }
        }

        // Fill missing days with zeros to simplify forecasting
        List<DailySummary> result = new ArrayList<>(daysBack);
        LocalDate start = LocalDate.now(zoneId).minusDays(daysBack - 1L);
        LocalDate end = LocalDate.now(zoneId);

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            MutableDaily m = perDay.getOrDefault(d, new MutableDaily(d));
            result.add(m.toSummary());
        }

        return result;
    }

    @Override
    public List<PplDailyObs> getPplDailyObs(ApplicationUser user, int daysBack) {
        if (daysBack <= 0) {
            return List.of();
        }

        LocalDate end = LocalDate.now(zoneId);
        LocalDate start = end.minusDays(daysBack - 1L);

        Instant from = start.atStartOfDay(zoneId).toInstant();

        // 1) activities in window
        List<Activity> activities = activityRepository.findRecentActivitiesForUser(user.getId(), from);

        // 2) gym days in window (one query)
        var gym = gymWorkoutRepository.findGymWorkoutByUserBetweenDatesOrderByStartDateAsc(user, start, end);
        var gymDays = gym.stream().map(GymWorkout::getCreationDate).collect(java.util.stream.Collectors.toSet());

        // 3) aggregate
        Map<LocalDate, MutablePplDaily> perDay = new TreeMap<>();

        for (Activity a : activities) {
            LocalDate day = toLocalDate(a.getStartDateLocal());
            MutablePplDaily m = perDay.computeIfAbsent(day, d -> new MutablePplDaily(d));

            m.totalDistance += safeFloat(a.getDistance());
            m.totalMovingTime += safeInt(a.getMovingTime());
            m.totalElevation += safeFloat(a.getTotalElevationGain());
            m.totalLoad += pickLoad(a);

            // label: keep best run of the day (highest load)
            if ("Run".equals(a.getType())) {
                int load = pickLoad(a);
                if (load >= m.bestRunLoad) {
                    m.bestRunLoad = load;
                    m.bestRun = a;
                }
            }

            // optional: weatherScore avg
            if (a.getWeather() != null) {
                try {
                    double ws = weatherService.calculateWeatherScore(a.getWeather());
                    if (Double.isFinite(ws)) {
                        m.weatherSum += ws;
                        m.weatherN++;
                    }
                } catch (Exception e) {
                    LOGGER.debug("Weather score failed for activityId={} userId={} date={} weatherPresent={}",
                            a.getId(), user.getId(), day, true, e);
                }
            }
        }

        // 4) build result with missing days filled
        List<PplDailyObs> out = new ArrayList<>(daysBack);

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            MutablePplDaily m = perDay.getOrDefault(d, new MutablePplDaily(d));

            WorkoutType wt = labelDayFromAggregate(m, gymDays.contains(d));
            Double weatherScore = (m.weatherN == 0) ? null : (m.weatherSum / m.weatherN);

            out.add(new PplDailyObs(
                    d.toString(),
                    wt.name(),
                    m.totalLoad,
                    m.totalDistance,
                    m.totalMovingTime,
                    m.totalElevation,
                    weatherScore,
                    null // tsb: you can fill elsewhere, or inject FatigueAndOverloadService here too
            ));
        }

        return out;
    }

    private WorkoutType labelDayFromAggregate(MutablePplDaily m, boolean hasGym) {
        if (m.bestRun != null) {
            var dec = m.bestRun.getRunTypeClassification();
            if (dec != null && dec.getRunType() != null) {
                return switch (dec.getRunType()) {
                    case EASY_RUN -> WorkoutType.EASY_RUN;
                    case TEMPO_RUN -> WorkoutType.TEMPO_RUN;
                    case INTERVAL_RUN -> WorkoutType.INTERVAL_RUN;
                    case LONG_RUN -> WorkoutType.LONG_RUN;
                };
            }
            if (m.bestRun.getWorkoutType() != null) {
                return m.bestRun.getWorkoutType();
            }
            return WorkoutType.EASY_RUN;
        }
        if (hasGym) {
            return WorkoutType.GYM_PREHAB;
        }
        return WorkoutType.REST_DAY;
    }

    private static class MutablePplDaily extends MutableDaily {
        Activity bestRun = null;
        int bestRunLoad = -1;

        double weatherSum = 0.0;
        int weatherN = 0;

        MutablePplDaily(LocalDate date) {
            super(date);
        }
    }


    private LocalDate toLocalDate(Instant startDateLocal) {
        return startDateLocal.atZone(zoneId).toLocalDate();
    }

    private int pickLoad(Activity a) {
        if (a.getSessionLoad() != null) {
            return Math.max(0, a.getSessionLoad());
        }
        if (a.getSufferScore() != null) {
            return Math.max(0, a.getSufferScore());
        }
        if (a.getGarminActivityTrainingsLoad() != null) {
            return Math.max(0, (int) Math.round(a.getGarminActivityTrainingsLoad()));
        }
        return 0;
    }

    private float safeFloat(float v) {
        return Float.isFinite(v) ? v : 0f;
    }

    private int safeInt(int v) {
        return Math.max(0, v);
    }

    private static class MutableDaily {
        final LocalDate date;
        int totalLoad = 0;
        float totalDistance = 0f;
        int totalMovingTime = 0;
        float totalElevation = 0f;
        boolean hasHeartRate = false;

        MutableDaily(LocalDate date) {
            this.date = date;
        }

        DailySummary toSummary() {
            return new DailySummary(
                    date,
                    totalLoad,
                    totalDistance,
                    totalMovingTime,
                    totalElevation,
                    hasHeartRate
            );
        }
    }
}
