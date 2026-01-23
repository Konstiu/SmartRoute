package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.FitUserModelResponse;
import com.smartroute.smartroute1.service.UserModelStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserModelStoreImpl implements UserModelStore {

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

    private static final ZoneId ZONE = ZoneId.of("Europe/Vienna");

    @Override
    public Optional<FitUserModelResponse> get(String email, String key) {
        if (email == null || key == null) {
            return Optional.empty();
        }

        String k = compositeKey(email, key);
        Entry e = store.get(k);
        if (e == null) {
            return Optional.empty();
        }

        if (Instant.now(clock).isAfter(e.expiresAt())) {
            store.remove(k);
            return Optional.empty();
        }

        return Optional.of(e.model());
    }

    @Override
    public void put(String email, String key, FitUserModelResponse model) {
        if (email == null || key == null || model == null) {
            return;
        }

        Instant now = Instant.now(clock);
        Instant expiresAt = nextMondayStartInstant(now);

        store.put(compositeKey(email, key), new Entry(model, now, expiresAt));
    }

    @Override
    public void remove(String email, String key) {
        if (email == null || key == null) {
            return;
        }
        store.remove(compositeKey(email, key));
    }

    private String compositeKey(String email, String key) {
        return email + "::" + key;
    }

    private Instant nextMondayStartInstant(Instant nowUtc) {
        LocalDate todayVienna = nowUtc.atZone(ZONE).toLocalDate();
        LocalDate nextMonday = todayVienna.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return nextMonday.atStartOfDay(ZONE).toInstant();
    }

    private record Entry(FitUserModelResponse model, Instant createdAt, Instant expiresAt) {}
}
