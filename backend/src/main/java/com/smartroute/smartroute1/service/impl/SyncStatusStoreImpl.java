package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.SyncStatusStoreEntryDto;
import com.smartroute.smartroute1.entity.enums.SyncState;
import com.smartroute.smartroute1.service.SyncStatusStore;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;


import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class SyncStatusStoreImpl implements SyncStatusStore {

    private final Cache<UUID, SyncStatusStoreEntryDto> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(50_000)
            .build();

    public void running(UUID requestId, String email) {
        cache.put(requestId, new SyncStatusStoreEntryDto(email, SyncState.RUNNING, Instant.now(), null));
    }

    public void success(UUID requestId, String email) {
        cache.put(requestId, new SyncStatusStoreEntryDto(email, SyncState.SUCCESS, Instant.now(), null));
    }

    public void failed(UUID requestId, String email, String message) {
        cache.put(requestId, new SyncStatusStoreEntryDto(email, SyncState.FAILED, Instant.now(), message));
    }

    public Optional<SyncStatusStoreEntryDto> get(UUID requestId) {
        return Optional.ofNullable(cache.getIfPresent(requestId));
    }
}
