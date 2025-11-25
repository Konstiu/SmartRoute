package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.WeatherDto;
import com.smartroute.smartroute1.entity.weather.EventType;
import com.smartroute.smartroute1.entity.weather.WeatherImpactResult;
import com.smartroute.smartroute1.entity.weather.WeatherResponse;
import com.smartroute.smartroute1.exception.ApiException;
import com.smartroute.smartroute1.exception.ValidationException;

import java.util.List;

/** Provides methods to retrieve hourly weather forecast data from an external API. */
public interface WeatherService {
    /**
     * Fetches the hourly weather data of a given location of the next seven days.
     *
     * @param latitude latitude of the location from which the weather data should be fetched
     *
     * @param longitude longitude of the location from which the weather data should be fetched
     *
     * @return a list of {@link WeatherDto} objects, each representing one hour of forecast data
     *
     * @throws ApiException if weather data could not be fetched
     */
    List<WeatherDto> getHourlyWeather(double latitude, double longitude) throws ValidationException;

    /**
     * Estimates the performance impact of weather conditions on a running event.
     *
     * @param eventType The type of running event (5K/10K/marathon-like).
     *
     * @param baseTimeSeconds The runner's baseline expected time for the event, in seconds, under optimal conditions.
     *
     * @param temperature Temperature in degrees Celsius.
     *
     * @param relativeHumidity Relative humidity as a percentage (0–100).
     *
     * @param shortwaveRadiation Solar radiation in W/m².
     *
     * @param windSpeed Wind speed in m/s.
     *
     * @param precipitation rain, showers, snow in mm
     *
     * @param age Runners age
     *
     * @return
     *        A {@link WeatherImpactResult} containing:
     *        <ul>
     *            <li>The estimated percentage performance penalty (positive or negative)</li>
     *            <li>The adjusted predicted finish time in seconds</li>
     *            <li>A heat-risk category based on WBGT</li>
     *        </ul>
     */
    WeatherImpactResult estimateImpact(
            EventType eventType,
            long baseTimeSeconds,
            double temperature,
            double relativeHumidity,
            double shortwaveRadiation,
            double windSpeed,
            double precipitation,
            int age
    );
}