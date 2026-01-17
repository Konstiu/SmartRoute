package com.smartroute.smartroute1.endpoint.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RouteWithFacilitiesDto {
    @NotNull
    private String originalRoute;

    private boolean includeToilets = false;

    @Min(1)
    private int toiletIntervalMeters = 5000;

    private boolean includeFountains = false;

    @Min(1)
    private int fountainIntervalMeters = 3000;

    @Min(1)
    private int maxFacilityDistance = 500;
}
