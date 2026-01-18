package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlan7dDto;
import com.smartroute.smartroute1.service.TrainingPlanStore;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TrainingPlanStoreImpl implements TrainingPlanStore {

    private static final Duration TTL = Duration.ofHours(24);

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

    @Override
    public void put(String email, String planId, TrainingPlan7dDto plan) {
        if (email == null || planId == null || plan == null) {
            return;
        }
        store.put(key(email, planId), new Entry(plan, Instant.now(clock)));
    }

    @Override
    public Optional<TrainingPlan7dDto> get(String email, String planId) {
        if (email == null || planId == null) {
            return Optional.empty();
        }

        Entry e = store.get(key(email, planId));
        if (e == null) {
            return Optional.empty();
        }

        if (Instant.now(clock).isAfter(e.createdAt().plus(TTL))) {
            store.remove(key(email, planId));
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

    private record Entry(TrainingPlan7dDto plan, Instant createdAt) {}
}
