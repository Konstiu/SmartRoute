package com.smartroute.smartroute1.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.endpoint.dto.WeatherDto;
import com.smartroute.smartroute1.endpoint.mapper.WeatherMapper;
import com.smartroute.smartroute1.entity.weather.WeatherResponse;
import com.smartroute.smartroute1.entity.weather.EventType;
import com.smartroute.smartroute1.entity.weather.HeatRiskCategory;
import com.smartroute.smartroute1.entity.weather.WeatherImpactResult;
import com.smartroute.smartroute1.exception.WeatherException;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.repository.WeatherRepository;
import com.smartroute.smartroute1.service.WeatherService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
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
    private final WeatherRepository rep;
    private final WeatherMapper weatherMapper = new WeatherMapper();

    public WeatherServiceImpl(WeatherRepository rep) {
        this.rep = rep;
    }

    @Override
    public List<WeatherDto> getHourlyWeather(double latitude, double longitude)
            throws ValidationException {

        validator.validateCoordinates(latitude, longitude);

        String url = buildUrl(latitude, longitude);
        LOGGER.trace("Calling Open-Meteo API with URL: {}", url);

        JsonNode root = fetchWeatherData(url);
        validator.validateHourlyData(root);

        JsonNode hourly = root.path("hourly");
        List<String> time = extractStringList(hourly);
        List<Double> temperature = extractDoubleList(hourly, "temperature_2m");
        List<Double> precipitation = extractDoubleList(hourly, "precipitation");
        List<Double> windSpeed = extractDoubleList(hourly, "wind_speed_10m");
        List<Double> humidity = extractDoubleList(hourly, "relative_humidity_2m");
        List<Double> radiation = extractDoubleList(hourly, "shortwave_radiation");

        validator.validateListSizes(time, temperature, precipitation, windSpeed, humidity, radiation);

        List<WeatherDto> result = new ArrayList<>();
        List<WeatherResponse> entities = new ArrayList<>();

        for (int i = 0; i < time.size(); i++) {
            WeatherDto dto = new WeatherDto(
                    time.get(i),
                    temperature.get(i),
                    windSpeed.get(i),
                    precipitation.get(i),
                    humidity.get(i),
                    radiation.get(i)
            );
            result.add(dto);
            entities.add(weatherMapper.toEntity(dto));
        }

        rep.saveAll(entities);
        return result;
    }

    // Build the Url to open-meteo with necessary parameters.
    private String buildUrl(double latitude, double longitude) {
        return UriComponentsBuilder.fromHttpUrl("https://api.open-meteo.com/v1/forecast")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("hourly",
                        "temperature_2m,precipitation,wind_speed_10m,"
                                + "relative_humidity_2m,shortwave_radiation")
                .toUriString();
    }

    // Fetch the weather data from the open-meteo Url.
    private JsonNode fetchWeatherData(String url) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return mapper.readTree(response.getBody());
        } catch (JsonProcessingException e) {
            LOGGER.error("Error parsing JSON from Open-Meteo", e);
            throw new WeatherException("Failed to parse Open-Meteo response", e);
        } catch (RestClientException e) {
            LOGGER.error("Error calling Open-Meteo API", e);
            throw new WeatherException("Failed to call Open-Meteo API", e);
        }
    }

    // Extract the contents of the fetched weather data given as Double.
    private List<Double> extractDoubleList(JsonNode hourly, String field) {
        if (!hourly.has(field)) {
            throw new WeatherException("Missing field: " + field);
        }
        List<Double> list = new ArrayList<>();
        hourly.get(field).forEach(node -> list.add(node.asDouble()));
        return list;
    }

    // Extract the contents of the fetched weather data given as String.
    private List<String> extractStringList(JsonNode hourly) {
        if (!hourly.has("time")) {
            throw new WeatherException("Missing field: " + "time");
        }
        List<String> list = new ArrayList<>();
        hourly.get("time").forEach(node -> list.add(node.asText()));
        return list;
    }

    // compute the natural wet-bulb temperature in C°. Source: https://journals.ametsoc.org/view/journals/apme/50/11/jamc-d-11-0143.1.xml
    private static double computeWetBulbStull(double temperature, double relativeHumidity) {

        double wetBulb =
                temperature * Math.atan(0.151977 * Math.sqrt(relativeHumidity + 8.313659))
                        + Math.atan(temperature + relativeHumidity)
                        - Math.atan(relativeHumidity - 1.676331)
                        + 0.00391838 * Math.pow(relativeHumidity, 1.5) * Math.atan(0.023101 * relativeHumidity)
                        - 4.686035;

        return wetBulb;
    }

    // compute the wet globe-temperature indoors.
    private static double computeWbgtShade(double temperature, double relativeHumidity) {
        double tw = computeWetBulbStull(temperature, relativeHumidity);
        return 0.7 * tw + 0.3 * temperature;
    }

    // approximate the outdoor wet globe-temperature outdoors.
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
            double windSpeed,
            double precipitation,
            int age
    ) {
        double optimalWbgt = optimalWbgt(eventType);
        double heatSlope = heatSlope(eventType);
        double coldSlope = coldSlope(eventType);
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

        double precipitationPenaltyPercent = calculatePrecipitationImpact(precipitation, age);
        long rainAdjustedTime = (long) (adjustedTime + (adjustedTime * (precipitationPenaltyPercent / 100)));

        HeatRiskCategory risk = classifyHeatRisk(wbgt);

        LOGGER.trace("Penalty: {} Adjusted Time: {}", (penaltyPercent + precipitationPenaltyPercent), rainAdjustedTime);
        return new WeatherImpactResult((penaltyPercent + precipitationPenaltyPercent), rainAdjustedTime, risk);
    }

    // The optimal wbgt values for different disciplines.
    private double optimalWbgt(EventType type) {
        return switch (type) {
            case MARATHON_LIKE -> 7.5;
            case TEN_K_LIKE -> 10.0;
            case FIVE_K_LIKE -> 15.0;
        };
    }

    // Impact of Heat for different disciplines.
    private double heatSlope(EventType type) {
        return switch (type) {
            case MARATHON_LIKE -> 0.20;
            case TEN_K_LIKE -> 0.04;
            case FIVE_K_LIKE -> 0.30;
        };
    }

    // Impact of Cold for different disciplines.
    private double coldSlope(EventType type) {
        return switch (type) {
            case MARATHON_LIKE -> 0.10;
            case TEN_K_LIKE -> 0.15;
            case FIVE_K_LIKE -> 0.20;
        };
    }

    // Classification of the heat risk. https://www.weather.gov/arx/wbgt
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

    // Estimates the complexity by weighing relevant weather data.
    private double complexityModifier(
            double temperature,
            double relativeHumidity,
            double shortwaveRadiation,
            double windSpeed
    ) {
        double tempScore = clamp((temperature - 15.0) / 20.0, -1, 1);
        double humScore = clamp((relativeHumidity - 50.0) / 50.0, -1, 1);
        double solarScore = clamp((shortwaveRadiation - 400.0) / 800.0, -1, 1);
        double windScore = clamp((windSpeed - 2.0) / 8.0, -1, 1);

        double combined =
                0.40 * tempScore
                        + 0.26 * humScore
                        + 0.18 * solarScore
                        + 0.16 * windScore;

        return 1.0 + 0.3 * combined; // [0.7, 1.3]
    }

    // Normalize values.
    private double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) {
            return 0.0;
        }
        return Math.max(min, Math.min(max, v));
    }

    // Calculates the impact of precipitation.
    private double calculatePrecipitationImpact(double precipMm, int runnerAge) {
        if (precipMm <= 0.0) {
            return 0.0;
        }

        // Base: 0.07% slowdown per mm rain
        double baseSlope = 0.07;

        double intensityFactor;
        if (precipMm < 10) {
            intensityFactor = 0.7;
        } else if (precipMm < 20) {
            intensityFactor = 1.0;
        } else {
            intensityFactor = 1.3;
        }

        double impact = precipMm * baseSlope * intensityFactor;

        // higher age = higher impact
        double ageFactor;
        if (runnerAge < 30) {
            ageFactor = 0.5;
        } else if (runnerAge < 40) {
            ageFactor = 0.8;
        } else {
            ageFactor = 1.0;
        }

        return impact *= ageFactor;
    }

}