package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.keysync.KeySyncSessionDto;

import java.util.Optional;

/**
 * Service for managing encryption key sync sessions in memory.
 */
public interface KeySyncStore {

    /**
     * Create an empty session (device requesting keys).
     */
    void createEmptySession(String sessionId, String sessionKey);

    /**
     * Create a session with keys already uploaded.
     */
    void createSessionWithKeys(String sessionId, String encryptedData);

    /**
     * Upload keys to an existing session.
     */
    void uploadKeysToSession(String sessionId, String encryptedData);

    /**
     * Get a session by ID.
     */
    Optional<KeySyncSessionDto> getSession(String sessionId);

    /**
     * Check if a session has keys available.
     */
    boolean sessionHasKeys(String sessionId);

    /**
     * Mark a session as downloaded.
     */
    void markAsDownloaded(String sessionId);

    /**
     * Get the number of active sessions (for monitoring).
     */
    long getActiveSessionCount();
}