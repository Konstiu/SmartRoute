package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.WeatherDto;
import com.smartroute.smartroute1.endpoint.dto.WeatherImpactDto;
import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.util.Coordinate;

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
     * Estimates the performance impact of weather conditions on a running event.
     *
     *
     * @param weather Weather Data.
     *
     * @param age Runners age.
     *
     * @param distanceMeters The distance of the running event in meters.
     *
     * @return
     *        A {@link WeatherImpactDto} containing:
     *        <ul>
     *            <li>The estimated percentage performance penalty</li>
     *            <li>The weather score</li>
     *            <li>A heat-risk category based on WBGT or windchill</li>
     *            <li>Classification of precipitation</li>
     *            <li>Classification of wind speed</li>
     *        </ul>
     *
     * @throws ValidationException if weather data is abnormal.
     */
    WeatherImpactDto calculateWeatherScore(WeatherResponse weather, int age, int distanceMeters) throws ValidationException;
}