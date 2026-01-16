package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.DailySummary;
import com.smartroute.smartroute1.service.DailyAggregationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    private final ZoneId zoneId = ZoneId.of("Europe/Vienna");

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
