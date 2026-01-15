package com.smartroute.smartroute1.endpoint.dto.keysync;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
public class KeySyncSessionDto {
    private String sessionId;
    private String sessionKey;
    private String encryptedData;
    private boolean hasKeys;
    private Instant createdAt;
    private Instant expiresAt;
    private boolean downloaded;
    private int downloadCount;

    public KeySyncSessionDto(String sessionId, String sessionKey, Instant expiresAt) {
        this.sessionId = sessionId;
        this.sessionKey = sessionKey;
        this.hasKeys = false;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.downloaded = false;
        this.downloadCount = 0;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}