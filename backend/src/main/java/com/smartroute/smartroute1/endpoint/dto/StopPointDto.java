package com.smartroute.smartroute1.endpoint.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StopPointDto {
    private double latitude;
    private double longitude;
    private Double altitude;
}