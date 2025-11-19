package com.smartroute.smartroute1.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.entity.weather.WeatherResponse;
import com.smartroute.smartroute1.entity.weather.EventType;
import com.smartroute.smartroute1.entity.weather.HeatRiskCategory;
import com.smartroute.smartroute1.entity.weather.WeatherImpactResult;
import com.smartroute.smartroute1.exception.ApiException;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.service.WeatherService;
import com.smartroute.smartroute1.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.smartroute.smartroute1.service.validators.WeatherValidator;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.ArrayList;

@Service
public class WeatherServiceImpl implements WeatherService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    private final WeatherValidator validator = new WeatherValidator();

    @Override
    public WeatherResponse getHourlyWeather(double latitude, double longitude) throws ValidationException {
        validator.validateCoordinates(latitude, longitude);
        String url = UriComponentsBuilder.fromHttpUrl("https://api.open-meteo.com/v1/forecast")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam(
                        "hourly",
                        "temperature_2m,precipitation,wind_speed_10m,"
                                + "relative_humidity_2m,shortwave_radiation,wind_direction_10m")
                .toUriString();

        LOGGER.trace("Calling Open-Meteo API with URL: {}", url);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode root = mapper.readTree(response.getBody());
            validator.validateHourlyData(root);
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

            List<Double> shortwaveRadiation = new ArrayList<>();
            if (hourly.has("shortwave_radiation")) {
                hourly.get("shortwave_radiation").forEach(sr -> shortwaveRadiation.add(sr.asDouble()));
            }

            List<Double> windDirection = new ArrayList<>();
            if (hourly.has("wind_direction_10m")) {
                hourly.get("wind_direction_10m").forEach(wd -> windDirection.add(wd.asDouble()));
            }

            return new WeatherResponse(time, temperature2m, windSpeed10m, precipitation, windDirection, shortwaveRadiation);
        } catch (Exception e) {
            LOGGER.warn("Error fetching or parsing hourly weather data", e);
            throw new ApiException("Failed to retrieve hourly weather data", e);
        }
    }

    private double valueAt(JsonNode node, int index) {
        if (node == null || !node.isArray() || index >= node.size()) {
            return 0.0;
        }
        return node.get(index).asDouble(0.0);
    }

    private static double computeWetBulbStull(double temperature, double relativeHumidity) {

        double wetBulb =
                temperature * Math.atan(0.151977 * Math.sqrt(relativeHumidity + 8.313659))
                        + Math.atan(temperature + relativeHumidity)
                        - Math.atan(relativeHumidity - 1.676331)
                        + 0.00391838 * Math.pow(relativeHumidity, 1.5) * Math.atan(0.023101 * relativeHumidity)
                        - 4.686035;

        return wetBulb;
    }

    private static double computeWbgtShade(double temperature, double relativeHumidity) {
        double tw = computeWetBulbStull(temperature, relativeHumidity);
        return 0.7 * tw + 0.3 * temperature;
    }

    private static double computeWbgtOutdoorApprox(double temperature,
                                                  double relativeHumidity,
                                                  double solarRadWm2,
                                                  double windMs) {
        double wbgtShade = computeWbgtShade(temperature, relativeHumidity);

        double sunCorrection = 0.0;
        if (solarRadWm2 > 600 && windMs < 2.0) {
            sunCorrection = 2.0;
        } else if (solarRadWm2 > 300) {
            sunCorrection = 1.0;
        }

        return wbgtShade + sunCorrection;
    }

    public WeatherImpactResult estimateImpact(
            EventType eventType,
            long baseTimeSeconds,
            double temperature,
            double relativeHumidity,
            double shortwaveRadiation,
            double windSpeed
    ) {
        double optimalWbgt = optimalWbgt(eventType);
        double heatSlope   = heatSlope(eventType);
        double coldSlope   = coldSlope(eventType);
        double wbgt = computeWbgtOutdoorApprox(temperature, relativeHumidity, shortwaveRadiation, windSpeed);
        double delta = wbgt - optimalWbgt;

        double penaltyBase;
        if (delta > 0) {
            penaltyBase = delta * heatSlope;
        } else {
            penaltyBase = -delta * coldSlope;
        }

        double modifier = complexityModifier(temperature, relativeHumidity, shortwaveRadiation, windSpeed);
        double penaltyPercent = penaltyBase * modifier;

        double factor = 1.0 + penaltyPercent / 100.0;
        long adjustedTime = (long) Math.round(baseTimeSeconds * factor);

        HeatRiskCategory risk = classifyHeatRisk(wbgt);

        return new WeatherImpactResult(penaltyPercent, adjustedTime, risk);
    }

    private double optimalWbgt(EventType type) {
        return switch (type) {
            case MARATHON_LIKE -> 7.5;
            case TEN_K_LIKE -> 10.0;
            case FIVE_K_LIKE -> 15.0;
        };
    }

    private double heatSlope(EventType type) {
        return switch (type) {
            case MARATHON_LIKE -> 0.20;
            case TEN_K_LIKE -> 0.04;
            case FIVE_K_LIKE -> 0.30;
        };
    }

    private double coldSlope(EventType type) {
        return switch (type) {
            case MARATHON_LIKE -> 0.10;
            case TEN_K_LIKE -> 0.15;
            case FIVE_K_LIKE -> 0.20;
        };
    }

    private HeatRiskCategory classifyHeatRisk(double wbgt) {
        if (wbgt <= 10.0) {
            return HeatRiskCategory.COLD_COOL;
        }
        if (wbgt <= 18.0) {
            return HeatRiskCategory.NEUTRAL;
        }
        if (wbgt <= 23.0) {
            return HeatRiskCategory.MODERATE_HEAT;
        }
        if (wbgt <= 28.0) {
            return HeatRiskCategory.HIGH_HEAT;
        }
        return HeatRiskCategory.EXTREME_HEAT;
    }

    private double complexityModifier(
            double temperature,
            double relativeHumidity,
            double shortwaveRadiation,
            double windSpeed
    ) {
        double tempScore  = clamp((temperature - 15.0) / 20.0, -1, 1);
        double humScore   = clamp((relativeHumidity - 50.0) / 50.0, -1, 1);
        double solarScore = clamp((shortwaveRadiation - 400.0) / 800.0, -1, 1);
        double windScore  = clamp((windSpeed - 2.0) / 8.0, -1, 1);

        double combined =
                0.40 * tempScore
                        + 0.26 * humScore
                        + 0.18 * solarScore
                        + 0.16 * windScore;

        return 1.0 + 0.3 * combined; // [0.7, 1.3]
    }

    private double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) {
            return 0.0;
        }
        return Math.max(min, Math.min(max, v));
    }
}