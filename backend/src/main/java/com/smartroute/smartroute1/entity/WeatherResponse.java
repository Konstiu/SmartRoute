package com.smartroute.smartroute1.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WeatherResponse {
    private List<String> time;
    private List<Double> temperature2m;
    private List<Double> windSpeed10m;
    private List<Double> precipitation;
}
