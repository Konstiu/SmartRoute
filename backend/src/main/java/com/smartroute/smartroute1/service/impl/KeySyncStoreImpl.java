package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.keysync.KeySyncSessionDto;
import com.smartroute.smartroute1.service.KeySyncStore;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * In-memory store for encryption key sync sessions using Caffeine cache.
 * Sessions automatically expire after 5 minutes
 */
@Service
public class KeySyncStoreImpl implements KeySyncStore {

    private static final Logger log = LoggerFactory.getLogger(KeySyncStoreImpl.class);

    private final Cache<String, KeySyncSessionDto> sessionCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))  // Sessions expire after 5 minutes
            .maximumSize(10_000)  // Max 10k concurrent sessions
            .removalListener((key, value, cause) -> {
                log.debug("Session removed: {} - Cause: {}", key, cause);
            })
            .build();

    @Override
    public void createEmptySession(String sessionId, String sessionKey) {
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));
        KeySyncSessionDto session = new KeySyncSessionDto(sessionId, sessionKey, expiresAt);
        sessionCache.put(sessionId, session);
        log.info("Created empty sync session: {}", sessionId);
    }

    @Override
    public void createSessionWithKeys(String sessionId, String encryptedData) {
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));
        KeySyncSessionDto session = new KeySyncSessionDto(sessionId, null, expiresAt);
        session.setEncryptedData(encryptedData);
        session.setHasKeys(true);
        sessionCache.put(sessionId, session);
        log.info("Created sync session with keys: {} and session {}", sessionId, session.getEncryptedData());
    }

    @Override
    public void uploadKeysToSession(String sessionId, String encryptedData) {
        KeySyncSessionDto session = sessionCache.getIfPresent(sessionId);
        if (session != null && !session.isExpired()) {
            session.setEncryptedData(encryptedData);
            session.setHasKeys(true);
            sessionCache.put(sessionId, session);
            log.info("Uploaded keys to session: {}", sessionId);
        } else {
            log.warn("Attempted to upload keys to expired or non-existent session: {}", sessionId);
        }
    }

    @Override
    public Optional<KeySyncSessionDto> getSession(String sessionId) {
        KeySyncSessionDto session = sessionCache.getIfPresent(sessionId);

        // Check if expired
        if (session != null && session.isExpired()) {
            sessionCache.invalidate(sessionId);
            log.debug("Session expired and removed: {}", sessionId);
            return Optional.empty();
        }

        return Optional.ofNullable(session);
    }

    @Override
    public boolean sessionHasKeys(String sessionId) {
        return getSession(sessionId)
                .map(session -> session.isHasKeys() && session.getEncryptedData() != null)
                .orElse(false);
    }

    @Override
    public void markAsDownloaded(String sessionId) {
        KeySyncSessionDto session = sessionCache.getIfPresent(sessionId);
        if (session != null) {
            session.setDownloaded(true);
            session.setDownloadCount(session.getDownloadCount() + 1);
            sessionCache.put(sessionId, session);
            log.info("Session marked as downloaded: {} (download count: {})",
                    sessionId, session.getDownloadCount());
        }
    }

    @Override
    public long getActiveSessionCount() {
        return sessionCache.estimatedSize();
    }
}