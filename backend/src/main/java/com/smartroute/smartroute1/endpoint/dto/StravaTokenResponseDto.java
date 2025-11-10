package com.smartroute.smartroute1.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StravaTokenResponseDto {
    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("expires_at")
    private long expiresAt;

    @JsonProperty("expires_in")
    private long expiresIn;

    @JsonProperty("athlete_id")
    private long athleteId;

    @JsonProperty("scope")
    private String scope;
}