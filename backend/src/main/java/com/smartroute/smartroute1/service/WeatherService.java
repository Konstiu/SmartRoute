package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.entity.WeatherResponse;

/** Provides methods to retrieve hourly weather forecast data from an external API. */
public interface WeatherService {
    /**
     * Fetches the hourly weather data of a given location of the next seven days.
     *
     * @param latitude latitude of the location from which the weather data should be fetched
     *
     * @param longitude longitude of the location from which the weather data should be fetched
     *
     * @return a {@link WeatherResponse} containing hourly weather data for the next seven days
     *
     * @throws RuntimeException if weather data could not be fetched
     */
    WeatherResponse getHourlyWeather(double latitude, double longitude);
}