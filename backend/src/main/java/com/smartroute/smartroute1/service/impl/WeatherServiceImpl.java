package com.smartroute.smartroute1.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.exception.ApiException;
import com.smartroute.smartroute1.service.WeatherService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.ArrayList;

@Service
public class WeatherServiceImpl implements WeatherService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public WeatherResponse getHourlyWeather(double latitude, double longitude) {
        String url = UriComponentsBuilder.fromHttpUrl("https://api.open-meteo.com/v1/forecast")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("hourly", "temperature_2m,precipitation,wind_speed_10m")
                .toUriString();

        LOGGER.trace("Calling Open-Meteo API with URL: {}", url);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode root = mapper.readTree(response.getBody());
            JsonNode hourly = root.path("hourly");

            List<String> time = new ArrayList<>();
            if (hourly.has("time")) {
                hourly.get("time").forEach(t -> time.add(t.asText()));
            }

            List<Double> temperature2m = new ArrayList<>();
            if (hourly.has("temperature_2m")) {
                hourly.get("temperature_2m").forEach(t -> temperature2m.add(t.asDouble()));
            }

            List<Double> precipitation = new ArrayList<>();
            if (hourly.has("precipitation")) {
                hourly.get("precipitation").forEach(p -> precipitation.add(p.asDouble()));
            }

            List<Double> windSpeed10m = new ArrayList<>();
            if (hourly.has("wind_speed_10m")) {
                hourly.get("wind_speed_10m").forEach(w -> windSpeed10m.add(w.asDouble()));
            }

            return new WeatherResponse(time, temperature2m, windSpeed10m, precipitation);
        } catch (Exception e) {
            LOGGER.warn("Error fetching or parsing hourly weather data", e);
            throw new ApiException("Failed to retrieve hourly weather data", e);
        }
    }
}