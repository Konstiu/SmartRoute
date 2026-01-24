package com.smartroute.smartroute1.endpoint.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeatherDto {
    private String time;
    private Double temperature2m;
    private Double windSpeed10m;
    private Double precipitation;
    private Double relativeHumidity;
    private Double shortWaveRadiation;
    private Double directRadiation;
    private Double diffuseRadiation;
    private Double surfacePressure;
    private Double dewPoint;
    private Double snowDepth;
    private Double uvIndex;
}
