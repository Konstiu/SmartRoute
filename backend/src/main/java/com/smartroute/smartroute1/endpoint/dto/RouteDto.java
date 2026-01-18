package com.smartroute.smartroute1.endpoint.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RouteDto {
    Double distance;
    Double pace;
    Double elevation;

    int seed; // optional

    public RouteDto(Double distance, Double pace, Double elevation) {
        this(distance, pace, elevation, 0);
    }
}
