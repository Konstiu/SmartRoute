package com.smartroute.smartroute1.endpoint.dto.keysync;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeySyncSessionResponseDto {
    private String sessionId;
    private String sessionKey;
    private Instant expiresAt;

}