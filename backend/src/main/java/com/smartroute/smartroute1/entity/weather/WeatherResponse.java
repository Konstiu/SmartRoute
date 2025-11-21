package com.smartroute.smartroute1.entity.weather;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import java.util.List;

@Data
@Entity
public class WeatherResponse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private List<String> time;
    private List<Double> temperature2m;
    private List<Double> windSpeed10m;
    private List<Double> precipitation;
    private List<Double> relativeHumidity;
    private List<Double> shortWaveRadiation;

    public WeatherResponse() {
    }

    public WeatherResponse(List<String> time, List<Double> temperature2m, List<Double> windSpeed10m,
                           List<Double> precipitation, List<Double> relativeHumidity, List<Double> shortWaveRadiation) {
        this.time = time;
        this.temperature2m = temperature2m;
        this.windSpeed10m = windSpeed10m;
        this.precipitation = precipitation;
        this.relativeHumidity = relativeHumidity;
        this.shortWaveRadiation = shortWaveRadiation;
    }
}
