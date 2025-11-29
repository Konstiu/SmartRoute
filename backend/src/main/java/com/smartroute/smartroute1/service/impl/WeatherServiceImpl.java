package com.smartroute.smartroute1.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.endpoint.dto.WeatherDto;
import com.smartroute.smartroute1.endpoint.mapper.WeatherMapper;
import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.entity.enums.HeatRiskCategory;
import com.smartroute.smartroute1.endpoint.dto.WeatherImpactDto;
import com.smartroute.smartroute1.exception.WeatherException;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.repository.WeatherRepository;
import com.smartroute.smartroute1.service.WeatherService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.smartroute.smartroute1.service.validators.WeatherValidator;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class WeatherServiceImpl implements WeatherService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final WebClient weatherWebClient;
    private final ObjectMapper mapper;
    private final WeatherValidator validator;
    private final WeatherRepository rep;
    private final WeatherMapper weatherMapper;
    private final WeatherRepository weatherRepository;

    @Override
    @Transactional
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
        List<WeatherResponse> existing = weatherRepository.findAll();
        WeatherResponse entity;

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


            if (i < existing.size()) {
                entity = weatherRepository.getByTimeAndLongitudeAndLatitude(time.get(i), longitude, latitude);
                entity = weatherMapper.toEntity(dto, entity, longitude, latitude);
            } else {
                entity = weatherMapper.toEntity(dto, null, longitude, latitude);
            }

            entities.add(entity);
        }

        rep.saveAll(entities);
        return result;
    }

    // Build url for open-meteo.
    private String buildUrl(double latitude, double longitude) {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + latitude
                + "&longitude=" + longitude
                + "&hourly=temperature_2m,precipitation,wind_speed_10m,relative_humidity_2m,shortwave_radiation";
    }

    // Fetch weather data from open-meteo.
    private JsonNode fetchWeatherData(String url) {
        try {
            String weather = weatherWebClient.get()
                    .uri(url)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new ResponseStatusException(
                                            HttpStatus.BAD_REQUEST, "Open-Meteo API 4xx: " + body
                                    )))
                    )
                    .onStatus(HttpStatusCode::is5xxServerError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new ResponseStatusException(
                                            HttpStatus.BAD_GATEWAY, "Open-Meteo API 5xx: " + body
                                    )))
                    )
                    .bodyToMono(String.class)
                    .block();

            return mapper.readTree(weather);

        } catch (JsonProcessingException e) {
            throw new WeatherException("Failed to parse Open-Meteo response", e);
        } catch (WebClientRequestException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Open-Meteo API unavailable" + e.getMessage());
        } catch (WebClientResponseException e) {
            throw new ResponseStatusException(e.getStatusCode(), "Open-Meteo error: " + e.getResponseBodyAsString(), e);
        }
    }

    // Extract contents of fetched data of type double.
    private List<Double> extractDoubleList(JsonNode hourly, String field) {
        if (!hourly.has(field)) {
            throw new WeatherException("Missing field: " + field);
        }
        List<Double> list = new ArrayList<>();
        hourly.get(field).forEach(node -> list.add(node.asDouble()));
        return list;
    }

    // Extract contents of fetched data of type String.
    private List<String> extractStringList(JsonNode hourly) {
        if (!hourly.has("time")) {
            throw new WeatherException("Missing field: time");
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

    public WeatherImpactDto estimateImpact(
            int distance,
            long baseTimeSeconds,
            double temperature,
            double relativeHumidity,
            double shortwaveRadiation,
            double windSpeed,
            double precipitation,
            int age
    ) {
        RunEventType eventType = mapDistanceToEvent(distance);
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
        return new WeatherImpactDto((penaltyPercent + precipitationPenaltyPercent), rainAdjustedTime, risk);
    }

    private enum RunEventType {
        FIVE_K_LIKE, TEN_K_LIKE, MARATHON_LIKE
    }

    // Maps distance of a run to the appropriate EventType.
    private RunEventType mapDistanceToEvent(int distance) {
        if (distance <= 7500) {
            return RunEventType.FIVE_K_LIKE;
        }
        if (distance <= 20000) {
            return RunEventType.TEN_K_LIKE;
        }
        return RunEventType.MARATHON_LIKE;
    }

    // The optimal wbgt values for different disciplines.
    private double optimalWbgt(RunEventType type) {
        return switch (type) {
            case MARATHON_LIKE -> 7.5;
            case TEN_K_LIKE -> 10.0;
            case FIVE_K_LIKE -> 15.0;
        };
    }

    // Impact of Heat for different disciplines.
    private double heatSlope(RunEventType type) {
        return switch (type) {
            case MARATHON_LIKE -> 0.20;
            case TEN_K_LIKE -> 0.04;
            case FIVE_K_LIKE -> 0.30;
        };
    }

    // Impact of Cold for different disciplines.
    private double coldSlope(RunEventType type) {
        return switch (type) {
            case MARATHON_LIKE -> 0.10;
            case TEN_K_LIKE -> 0.15;
            case FIVE_K_LIKE -> 0.20;
        };
    }

    // Classification of the heat risk: https://www.weather.gov/arx/wbgt
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