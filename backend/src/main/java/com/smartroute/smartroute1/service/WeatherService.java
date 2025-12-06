package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.WeatherImpactDto;
import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.exception.ValidationException;

import java.util.List;

/** Provides methods to retrieve hourly weather forecast data from an external API. */
public interface WeatherService {
    /**
     * Fetches the hourly weather data of a given location of the next seven days.
     *
     * @param latitude latitude coordinate from where the weather data should be fetched.
     *
     * @param longitude latitude coordinate from where the weather data should be fetched.
     *
     * @param timeUtc time from which the weather data should be fetched for.
     *
     * @throws ValidationException coordinates contain invalid latitude or longitude values, time is in a wrong format or data is not available for that time.
     */
    WeatherResponse getWeatherAtTime(double latitude, double longitude, String timeUtc) throws ValidationException;

    /**
     * Calculates the weather score, a metric that quantifies the outdoor conditions.
     *
     * @param weather Weather Data.
     *
     * @return weatherScore between 0 and 1.
     *
     * @throws ValidationException if weather data is abnormal.
     */
    double calculateWeatherScore(WeatherResponse weather) throws ValidationException;

    /**
     * Estimates the negative impact the weather has on performance, in comparison to optimal conditions.
     *
     * @param weather Weather Data.
     *
     * @return estimated performance penalty in %.
     *
     * @throws ValidationException if weather data is abnormal.
     */
    double estimatePerformancePenalty(WeatherResponse weather) throws ValidationException;
}