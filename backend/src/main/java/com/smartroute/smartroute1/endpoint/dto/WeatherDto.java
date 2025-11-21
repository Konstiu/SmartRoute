package com.smartroute.smartroute1.endpoint.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class WeatherDto {
    private Long id;

    private List<String> time;
    private List<Double> temperature2m;
    private List<Double> windSpeed10m;
    private List<Double> precipitation;
    private List<Double> relativeHumidity;
    private List<Double> shortWaveRadiation;
}
