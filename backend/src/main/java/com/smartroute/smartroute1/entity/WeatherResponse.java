package com.smartroute.smartroute1.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

@Entity
@Setter
@Getter
@ToString
public class WeatherResponse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String time;
    @Column
    private LocalDate forecastGeneratedAt; // stored in UTC
    @Column
    private Double longitude;
    @Column
    private Double latitude;
    @Column
    private Double temperature2m; // °C
    @Column
    private Double windSpeed10m; // km/h
    @Column
    private Double precipitation; // mm/h
    @Column
    private Double relativeHumidity; // %
    @Column
    private Double shortWaveRadiation; // W/m^2
    @Column
    private Double directRadiation; // W/m^2
    @Column
    private Double diffuseRadiation; // W/m^2
    @Column
    private Double surfacePressure; // hPa
    @Column
    private Double dewPoint; // °C
    @Column
    private Double snowDepth; // cm
    @Column
    private Double uvIndex;
}
