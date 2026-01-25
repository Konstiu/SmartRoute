package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.DailySummary;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.service.DailyAggregationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Objects;
import java.util.HashMap;


@Service
public class DailyAggregationServiceImpl implements DailyAggregationService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Vienna");

    private final ActivityRepository activityRepository;
    private final Clock clock;
    private final ZoneId zoneId;

    // prod
    @Autowired
    public DailyAggregationServiceImpl(ActivityRepository activityRepository) {
        this(activityRepository, Clock.system(DEFAULT_ZONE));
    }

    // testable
    public DailyAggregationServiceImpl(ActivityRepository activityRepository, Clock clock) {
        this.activityRepository = Objects.requireNonNull(activityRepository);
        this.clock = Objects.requireNonNull(clock);
        this.zoneId = clock.getZone() != null ? clock.getZone() : DEFAULT_ZONE;
    }

    @Override
    public List<DailySummary> getDailySummaries(ApplicationUser user, int daysBack) {
        if (daysBack <= 0) {
            return List.of();
        }
        if (user == null || user.getId() == null) {
            return List.of();
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate start = today.minusDays(daysBack - 1L);
        LocalDate end = today;

        Instant from = start.atStartOfDay(zoneId).toInstant();

        List<Activity> activities = activityRepository.findRecentActivitiesForUser(user.getId(), from);

        Map<LocalDate, MutableDaily> perDay = new HashMap<>();

        if (activities != null) {
            for (Activity a : activities) {
                if (a == null || a.getStartDateLocal() == null) {
                    continue;
                }

                LocalDate day = a.getStartDateLocal().atZone(zoneId).toLocalDate();

                // keep only the output window (important if repo returns older stuff)
                if (day.isBefore(start) || day.isAfter(end)) {
                    continue;
                }

                MutableDaily m = perDay.computeIfAbsent(day, MutableDaily::new);

                m.totalDistance += safeFloat(a.getDistance());
                m.totalMovingTime += safeInt(a.getMovingTime());
                m.totalElevation += safeFloat(a.getTotalElevationGain());
                m.totalLoad += pickLoad(a);

                if (a.getAverageHeartrate() != null || a.getMaxHeartrate() != null) {
                    m.hasHeartRate = true;
                }
            }
        }

        List<DailySummary> result = new ArrayList<>(daysBack);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            result.add(perDay.getOrDefault(d, new MutableDaily(d)).toSummary());
        }
        return List.copyOf(result);
    }

    private int pickLoad(Activity a) {
        // keep your original semantics (but safe): sessionLoad > sufferScore > garmin
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

    private float safeFloat(Float v) {
        if (v == null) {
            return 0f;
        }
        return Float.isFinite(v) ? v : 0f;
    }

    private int safeInt(Integer v) {
        if (v == null) {
            return 0;
        }
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
            return new DailySummary(date, totalLoad, totalDistance, totalMovingTime, totalElevation, hasHeartRate);
        }
    }
}
