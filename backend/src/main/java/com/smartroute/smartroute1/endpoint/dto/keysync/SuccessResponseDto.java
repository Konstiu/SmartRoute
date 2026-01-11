package com.smartroute.smartroute1.endpoint.dto.keysync;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SuccessResponseDto {
    private boolean success;
    private String sessionId;
}