package com.smartroute.smartroute1.entity.weather;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import lombok.Data;

@Data
@Entity
public class WeatherResponse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String time;
    @Column
    private Double temperature2m;
    @Column
    private Double windSpeed10m;
    @Column
    private Double precipitation;
    @Column
    private Double relativeHumidity;
    @Column
    private Double shortWaveRadiation;

    public WeatherResponse() {
    }

    public WeatherResponse(String time, Double temperature2m, Double windSpeed10m,
                           Double precipitation, Double relativeHumidity, Double shortWaveRadiation) {
        this.time = time;
        this.temperature2m = temperature2m;
        this.windSpeed10m = windSpeed10m;
        this.precipitation = precipitation;
        this.relativeHumidity = relativeHumidity;
        this.shortWaveRadiation = shortWaveRadiation;
    }
}
