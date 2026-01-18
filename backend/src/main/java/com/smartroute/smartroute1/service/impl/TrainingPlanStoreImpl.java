package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlan7dDto;
import com.smartroute.smartroute1.service.TrainingPlanStore;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TrainingPlanStoreImpl implements TrainingPlanStore {

    private static final Duration TTL = Duration.ofHours(24);

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

    private static final ZoneId ZONE = ZoneId.of("Europe/Vienna");

    @Override
    public void put(String email, String planId, TrainingPlan7dDto plan) {
        if (email == null || planId == null || plan == null) {
            return;
        }

        Instant now = Instant.now(clock);
        Instant expiresAt = nextMondayStartInstant(now);

        store.put(key(email, planId), new Entry(plan, now, expiresAt));
    }

    @Override
    public Optional<TrainingPlan7dDto> get(String email, String planId) {
        if (email == null || planId == null) {
            return Optional.empty();
        }

        String k = key(email, planId);
        Entry e = store.get(k);
        if (e == null) {
            return Optional.empty();
        }

        if (Instant.now(clock).isAfter(e.expiresAt())) {
            store.remove(k);
            return Optional.empty();
        }

        return Optional.of(e.plan());
    }


    @Override
    public void remove(String email, String planId) {
        if (email == null || planId == null) {
            return;
        }
        store.remove(key(email, planId));
    }

    private String key(String email, String planId) {
        return email + "::" + planId;
    }

    private Instant nextMondayStartInstant(Instant nowUtc) {
        LocalDate todayVienna = nowUtc.atZone(ZONE).toLocalDate();
        LocalDate nextMonday = todayVienna.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return nextMonday.atStartOfDay(ZONE).toInstant();
    }

    private record Entry(TrainingPlan7dDto plan, Instant createdAt, Instant expiresAt) {}
}
