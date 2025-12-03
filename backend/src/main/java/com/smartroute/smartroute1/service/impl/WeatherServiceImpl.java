package com.smartroute.smartroute1.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.endpoint.dto.WeatherDto;
import com.smartroute.smartroute1.endpoint.dto.WeatherImpactDto;
import com.smartroute.smartroute1.endpoint.mapper.WeatherMapper;
import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.entity.enums.HeatRiskCategory;
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
import java.time.LocalDateTime;
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
    public List<WeatherDto> getHourlyWeather(double latitude, double longitude) throws ValidationException {
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
        List<Double> dewPoint = extractDoubleList(hourly, "dew_point_2m");
        List<Double> surfacePressure = extractDoubleList(hourly, "surface_pressure");
        List<Double> directRadiation = extractDoubleList(hourly, "direct_radiation");
        List<Double> diffuseRadiation = extractDoubleList(hourly, "diffuse_radiation");
        List<Double> snowDepth = extractDoubleList(hourly, "snow_depth");

        validator.validateListSizes(time, temperature, precipitation, windSpeed, humidity, radiation, dewPoint, directRadiation, diffuseRadiation, snowDepth);

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
                    radiation.get(i),
                    dewPoint.get(i),
                    surfacePressure.get(i),
                    directRadiation.get(i),
                    diffuseRadiation.get(i),
                    snowDepth.get(i)
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
                + "&hourly=temperature_2m,precipitation,wind_speed_10m,relative_humidity_2m,shortwave_radiation,dew_point_2m,surface_pressure,direct_radiation,diffuse_radiation,snow_depth";
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

    // Compute the natural wet-bulb temperature in C°. Source: https://journals.ametsoc.org/view/journals/apme/50/11/jamc-d-11-0143.1.xml
    private static double computeWetBulbTemp(double temperature, double relativeHumidity) {

        double wetBulb =
                temperature * Math.atan(0.151977 * Math.sqrt(relativeHumidity + 8.313659))
                        + Math.atan(temperature + relativeHumidity)
                        - Math.atan(relativeHumidity - 1.676331)
                        + 0.00391838 * Math.pow(relativeHumidity, 1.5) * Math.atan(0.023101 * relativeHumidity)
                        - 4.686035;

        return wetBulb;
    }

    // Compute the wet bulb globe-temperature.
    public double computeWbgt(WeatherResponse weather) {
        final double temperature = weather.getTemperature2m();
        final double relativeHumidity = weather.getRelativeHumidity();

        double twb = computeWetBulbTemp(temperature, relativeHumidity);
        double tg = computeGlobeTemperature(weather);

        return 0.7 * twb + 0.2 * tg + 0.1 * temperature;
    }

    //  Compute estimated black globe temperature (Tg) in °C. Source: https://www.weather.gov/media/tsa/pdf/WBGTpaper2.pdf
    public double computeGlobeTemperature(WeatherResponse weather) {
        final String time = weather.getTime();
        final double temperature = weather.getTemperature2m();
        double windSpeed = weather.getWindSpeed10m();
        double solarRadiation = weather.getShortWaveRadiation();
        final double longitude = weather.getLongitude();
        final double latitude = weather.getLatitude();
        final double dewPoint = weather.getDewPoint();
        final double surfacePressure = weather.getSurfacePressure();

        // approximation cannot cope with 0 wind speed or 0 shortwave radiation.
        if (windSpeed == 0.0) {
            windSpeed += 0.1;
        }

        if (solarRadiation == 0.0) {
            solarRadiation += 0.1;
        }

        final double directRadiation = weather.getDirectRadiation() / solarRadiation;
        final double diffuseRadiation = weather.getDiffuseRadiation() / solarRadiation;

        double windSpeedMetersPerHour = windSpeed * 1000;
        double zenithAngle = computeSolarZenithAngle(latitude, longitude, time);
        double vaporPressure = computeVaporPressure(temperature, dewPoint, surfacePressure);
        double atmosphericEmissivity = 0.575 * Math.pow(vaporPressure, 1.0 / 7.0);

        final double B = computeB(solarRadiation, directRadiation, diffuseRadiation, zenithAngle, atmosphericEmissivity, temperature);
        final double C = computeC(windSpeedMetersPerHour);

        return (B + C * temperature + 7680000.0) / (C + 256000.0);
    }

    // Computes vapor pressure.
    private double computeVaporPressure(double temperature, double dewPoint, double surfacePressure) {
        double term1 = Math.exp(17.67 * (dewPoint - temperature) / (dewPoint + 243.5));
        double term2 = (1.0007 + 0.00000346 * surfacePressure);
        double term3 = 6.112 * Math.exp((17.502 * temperature) / (240.97 + temperature));
        return term1 * term2 * term3;
    }

    // Computes constant B for the estimated black globe temperature.
    private double computeB(double solarRadiation, double directBeamRadiation, double diffuseBeamRadiation, double zenithAngle, double atmosphericEmissivity, double temperature) {
        final double Sigma = 5.67 * Math.pow(10, -8);

        double term1 = solarRadiation * (directBeamRadiation / (4 * Sigma * Math.cos(zenithAngle)) + (1.2 / Sigma) * diffuseBeamRadiation);

        double term2 = atmosphericEmissivity * Math.pow(temperature, 4);

        return term1 + term2;
    }

    // Computes constant C for the estimated black globe temperature.
    private double computeC(double windSpeed) {
        final double H = 0.315; // convective coefficient constant
        return (H * Math.pow(windSpeed, 0.58)) / (5.3865 * Math.pow(10, -8));
    }

    // Solar zenith angle using NOAA algorithm.
    private double computeSolarZenithAngle(double latitude, double longitude, String time) {
        double latRad = Math.toRadians(latitude);

        int dayOfYear = extractDayOfYear(time);
        int hour = extractHour(time);

        double gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1 + (double) (hour - 12) / 24);

        double decl = 0.006918
                - 0.399912 * Math.cos(gamma)
                + 0.070257 * Math.sin(gamma)
                - 0.006758 * Math.cos(2 * gamma)
                + 0.000907 * Math.sin(2 * gamma)
                - 0.002697 * Math.cos(3 * gamma)
                + 0.00148 * Math.sin(3 * gamma);

        double eqTime = 229.18 * (0.000075
                + 0.001868 * Math.cos(gamma)
                - 0.032077 * Math.sin(gamma)
                - 0.014615 * Math.cos(2 * gamma)
                - 0.040849 * Math.sin(2 * gamma));

        double timeOffset = eqTime + 4 * longitude;
        double tst = hour * 60 + timeOffset;
        double ha = Math.toRadians((tst / 4.0) - 180.0);

        double cosZ = Math.sin(latRad) * Math.sin(decl) + Math.cos(latRad) * Math.cos(decl) * Math.cos(ha);

        cosZ = Math.max(-1, Math.min(1, cosZ));

        return Math.acos(cosZ);
    }

    // Returns the hour (0–23)
    private int extractHour(String isoTime) {
        LocalDateTime dt = LocalDateTime.parse(isoTime);
        return dt.getHour();
    }

    // Returns the day of the year
    private static int extractDayOfYear(String isoTime) {
        LocalDateTime dt = LocalDateTime.parse(isoTime);
        return dt.getDayOfYear();
    }

    // Calculates wind chill, a measurement of perceived coldness. Source: https://www.canada.ca/en/environment-climate-change/services/weather-health/wind-chill-cold-weather/wind-chill-index.html
    private double calculateWindChill(double temperature, double windSpeed) {
        return 13.12 + 0.6215 * temperature + (0.3965 * temperature - 11.37) * Math.pow(windSpeed, 0.16);
    }

    // Classification of cold risk: https://www.canada.ca/en/environment-climate-change/services/weather-health/wind-chill-cold-weather/wind-chill-index.html
    private HeatRiskCategory classifyColdRisk(double windChill) {
        if (windChill <= -55) {
            return HeatRiskCategory.EXTREME_COLD;
        }
        if (windChill >= -54 && windChill <= -48) {
            return HeatRiskCategory.SEVERE_COLD;
        }
        if (windChill >= -47 && windChill <= -40) {
            return HeatRiskCategory.VERY_HIGH_COLD_RISK;
        }
        if (windChill >= -39 && windChill <= -28) {
            return HeatRiskCategory.HIGH_COLD_RISK;
        }
        if (windChill >= -27 && windChill <= -10) {
            return HeatRiskCategory.MODERATE_COLD;
        }
        if (windChill >= -9 && windChill <= 0) {
            return HeatRiskCategory.LOW_COLD;
        }
        return HeatRiskCategory.NEUTRAL;
    }

    public WeatherImpactDto calculateWeatherScore(WeatherResponse weather, int age, int distanceMeters) {
        RunEventType type = mapDistanceToEvent(distanceMeters);
        double wbgt = computeWbgt(weather);
        double optimal = optimalWbgt(type);

        double delta = wbgt - optimal;
        double heatSlope = heatSlope(type);
        double coldSlope = coldSlope(type);
        HeatRiskCategory riskClassification;

        double wbgtPenalty = delta > 0 ? delta * heatSlope : -delta * coldSlope;
        double modifier = complexityModifier(weather);
        double weightedWbgtPenalty = wbgtPenalty * modifier;
        weightedWbgtPenalty = clamp(weightedWbgtPenalty, 0, 40);

        HeatRiskCategory heat = classifyHeatRisk(wbgt);
        riskClassification = heat;
        double temperatureRiskPenalty = switch (heat) {
            case LOW_HEAT -> 25;
            case MODERATE_HEAT -> 50;
            case HIGH_HEAT -> 75;
            case EXTREME_HEAT -> 100; // severe risk, outdoor activity should be prohibited.
            default -> 0;
        };

        // if wbgt indicates lower than optimal temperature, estimate temperature risk using wind chill.
        if (heat == HeatRiskCategory.LOW_COLD) {
            double windChill = calculateWindChill(weather.getTemperature2m(), weather.getWindSpeed10m());
            HeatRiskCategory cold = classifyColdRisk(windChill);
            riskClassification = cold;

            temperatureRiskPenalty = switch (cold) {
                case EXTREME_COLD -> 100;  // severe risk, outdoor activity should be prohibited.
                case SEVERE_COLD -> 100;  // severe risk, outdoor activity should be prohibited.
                case VERY_HIGH_COLD_RISK -> 80;
                case HIGH_COLD_RISK -> 60;
                case MODERATE_COLD -> 40;
                case LOW_COLD -> 20;
                default -> 0;
            };
        }

        double rainPenalty = clamp(calculatePrecipitationImpact(weather.getPrecipitation(), age), 0, 25);

        double snowPenalty = clamp(snowDepthImpact(weather.getSnowDepth()), 0, 40);

        double totalPenalty =
                1
                        - (1 - weightedWbgtPenalty / 100.0)
                        * (1 - temperatureRiskPenalty / 100.0)
                        * (1 - rainPenalty / 100.0)
                        * (1 - snowPenalty / 100.0);

        double score = 1.0 - totalPenalty;
        double weatherScore = clamp(score, 0.0, 1.0);
        double performancePenalty = estimatePerformancePenalty(distanceMeters, weather, age) / 100;

        return new WeatherImpactDto(performancePenalty, weatherScore, riskClassification);
    }

    private double estimatePerformancePenalty(int distance, WeatherResponse weather, int age) {
        RunEventType eventType = mapDistanceToEvent(distance);
        double optimalWbgt = optimalWbgt(eventType);
        double heatSlope = heatSlope(eventType);
        double coldSlope = coldSlope(eventType);
        double wbgt = computeWbgt(weather);
        double delta = wbgt - optimalWbgt;
        final double precipitation = weather.getPrecipitation();

        double penaltyBase;
        if (delta > 0) {
            penaltyBase = delta * heatSlope;
        } else {
            penaltyBase = -delta * coldSlope;
        }

        double modifier = complexityModifier(weather);
        double penaltyPercent = penaltyBase * modifier;

        double totalPenalty = calculatePrecipitationImpact(precipitation, age)
                + snowDepthImpact(weather.getSnowDepth())
                + penaltyPercent;

        LOGGER.trace("Penalty: {}", totalPenalty);
        return totalPenalty;
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
            case TEN_K_LIKE -> 0.25;
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
        if (wbgt < 10.0) {
            return HeatRiskCategory.LOW_COLD;
        }
        if (wbgt >= 10.0 && wbgt < 18.3) {
            return HeatRiskCategory.NEUTRAL;
        }
        if (wbgt >= 18.3 && wbgt <= 22.2) {
            return HeatRiskCategory.LOW_HEAT;
        }
        if (wbgt > 22.2 && wbgt <= 25.56) {
            return HeatRiskCategory.MODERATE_HEAT;
        }
        if (wbgt > 25.56 && wbgt <= 27.8) {
            return HeatRiskCategory.HIGH_HEAT;
        }
        return HeatRiskCategory.EXTREME_HEAT;
    }

    // Estimates the complexity by weighing relevant weather data.
    private double complexityModifier(WeatherResponse weather) {
        final double temperature = weather.getTemperature2m();
        final double windSpeed = weather.getWindSpeed10m();
        final double relativeHumidity = weather.getRelativeHumidity();
        final double solarRadiation = weather.getShortWaveRadiation();

        double tempScore = clamp((temperature - 15.0) / 20.0, -1, 1);
        double humScore = clamp((relativeHumidity - 50.0) / 50.0, -1, 1);
        double solarScore = clamp((solarRadiation - 400.0) / 800.0, -1, 1);
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

    // Calculates the impact of precipitation in percent.
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

    // Returns the impact of snow depth in percent.
    private double snowDepthImpact(double snowDepth) {
        double snowImpact = 0.0;

        if (snowDepth <= 0) {
            snowImpact = 0.0; // No snow, no penalty
        }

        // <1 cm: small traction effect
        if (snowDepth < 1.0) {
            snowImpact = 0.01; // ~1%
        }

        // 1–5 cm: packed trail snow (5–10% slowdown)
        if (snowDepth <= 5.0) {
            // Linear interpolation from 5% to 10%
            double t = (snowDepth - 1.0) / (5.0 - 1.0);
            snowImpact = 0.05 + t * 0.05;
        }

        // 5–10 cm: soft snow (10–20% slowdown)
        if (snowDepth <= 10.0) {
            double t = (snowDepth - 5.0) / (10.0 - 5.0);
            snowImpact = 0.10 + t * 0.10;
        }

        // 10–20 cm: deep snow (20–40% slowdown)
        if (snowDepth <= 20.0) {
            double t = (snowDepth - 10.0) / (20.0 - 10.0);
            snowImpact = 0.20 + t * 0.20;
        }

        // >20 cm: extreme, running becomes power-hiking
        if (snowDepth > 20.0) {
            snowImpact = 0.40;
        }

        return snowImpact * 100;
    }
}