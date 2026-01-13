package com.smartroute.smartroute1.endpoint.dto.keysync;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateSessionRequestDto {
    private String sessionId;
    private String sessionKey;
}