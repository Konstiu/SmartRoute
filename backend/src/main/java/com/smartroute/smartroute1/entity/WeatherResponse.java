package com.smartroute.smartroute1.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

@Entity
public class WeatherResponse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String time;
    @Column
    private Double longitude;
    @Column
    private Double latitude;
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
}
