package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.WeatherDto;
import com.smartroute.smartroute1.endpoint.dto.WeatherImpactDto;
import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.exception.WeatherException;
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
     * @throws WeatherException if weather data could not be fetched
     */
    List<WeatherDto> getHourlyWeather(double latitude, double longitude) throws ValidationException;

    /**
     * Estimates the performance impact of weather conditions on a running event.
     *
     *
     * @param weather Weather Data.
     *
     * @param age Runners age
     *
     * @param distanceMeters The distance of the running event in meters.
     *
     * @return
     *        A {@link WeatherImpactDto} containing:
     *        <ul>
     *            <li>The estimated percentage performance penalty</li>
     *            <li>The weather score</li>
     *            <li>A heat-risk category based on WBGT or windchill</li>
     *        </ul>
     */
    WeatherImpactDto calculateWeatherScore(WeatherResponse weather, int age, int distanceMeters);
}