package com.smartroute.smartroute1.endpoint.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StravaAccountConnectionStateDto {
    private boolean connected;
    private String scopes;
}
