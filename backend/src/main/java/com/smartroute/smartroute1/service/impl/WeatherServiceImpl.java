package com.smartroute.smartroute1.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.endpoint.dto.WeatherDto;
import com.smartroute.smartroute1.endpoint.dto.WeatherSummaryDto;
import com.smartroute.smartroute1.endpoint.mapper.WeatherMapper;
import com.smartroute.smartroute1.entity.WeatherResponse;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WeatherServiceImpl implements WeatherService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final WebClient weatherWebClient;
    private final ObjectMapper mapper;
    private final WeatherValidator validator;
    private final WeatherMapper weatherMapper;
    private final WeatherRepository weatherRepository;

    @Transactional
    @Override
    public WeatherResponse getWeatherAtTime(double latitude, double longitude, String timeUtc) throws ValidationException {
        LOGGER.trace("Searching cached weather for latitude={}, longitude={} at hour={}", latitude, longitude, timeUtc);

        validator.validateCoordinates(latitude, longitude);
        validator.validateTimeFormat(timeUtc);
        validator.validateForecastTime(timeUtc);

        // Try to load from repository
        Optional<WeatherResponse> cached = Optional.ofNullable(weatherRepository.getByTimeAndLatitudeAndLongitude(timeUtc, latitude, longitude));

        if (cached.isPresent()) {
            LOGGER.trace("Found cached weather in repository");
            WeatherResponse old = cached.get();

            LocalDate fetchedDay = old.getForecastGeneratedAt();
            LocalDate today = LocalDate.now(ZoneOffset.UTC);

            if (fetchedDay.equals(today)) {
                LOGGER.trace("Cached weather is still fresh");
                return old; // cache hit and still fresh
            }

            LOGGER.trace("Stale data found -> deleting stale entries");
            weatherRepository.deleteAllByCoordinates(latitude, longitude); // if staleness detected for a coordinate, delete all weather data of that entry
        }

        // Fetch new data
        LOGGER.trace("Weather not cached, calling open-meteo");
        importHourlyWeather(latitude, longitude);

        // Load from repository
        return weatherRepository.getByTimeAndLatitudeAndLongitude(timeUtc, latitude, longitude);
    }

    // Stores hourly weather data over the next 3 days for a given coordinate from open-meteo.
    private void importHourlyWeather(double latitude, double longitude) throws ValidationException {
        validator.validateCoordinates(latitude, longitude);

        String url = buildUrl(latitude, longitude);
        LOGGER.trace("Calling Open-Meteo API with URL: {}", url);

        JsonNode root = fetchWeatherData(url);

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

        List<WeatherResponse> entities = new ArrayList<>();

        LocalDate fetchedAt = LocalDate.now(ZoneOffset.UTC);
        LocalDate nowUtc = fetchedAt;
        LocalDate cutoff = nowUtc.plusDays(3); // keep only the next 72 hours

        for (int i = 0; i < time.size(); i++) {
            LocalDate entryTime = LocalDate.parse(time.get(i), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
            // Skip anything beyond the 3-day window
            if (entryTime.isAfter(cutoff)) {
                continue;
            }

            WeatherDto dto = new WeatherDto(
                    time.get(i),
                    temperature.get(i),
                    windSpeed.get(i),
                    precipitation.get(i),
                    humidity.get(i),
                    radiation.get(i),
                    directRadiation.get(i),
                    diffuseRadiation.get(i),
                    surfacePressure.get(i),
                    dewPoint.get(i),
                    snowDepth.get(i)
            );

            // Check if existing entry in DB
            WeatherResponse entity = weatherRepository.getByTimeAndLatitudeAndLongitude(time.get(i), latitude, longitude);

            // Map the dto into entity (create new if null)
            entity = weatherMapper.toEntity(dto, entity, latitude, longitude);

            // assign fetch timestamp
            entity.setForecastGeneratedAt(fetchedAt);

            entities.add(entity);
        }

        weatherRepository.saveAll(entities);
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

    @Override
    public double calculateWeatherScore(WeatherResponse weather) throws ValidationException {
        validator.validateWeatherValues(weather);

        double wbgt = computeWbgt(weather);

        HeatRiskCategory heat = classifyHeatRisk(wbgt);
        double temperatureRiskPenalty = weatherPenalty(wbgt, 100.0, 0.35, 25.0, 1.25);

        // if wbgt indicates lower than optimal temperature, estimate temperature risk using wind chill.
        if (heat == HeatRiskCategory.BELOW_WBGT_RANGE) {
            double windChill = calculateWindChill(weather.getTemperature2m(), weather.getWindSpeed10m());
            temperatureRiskPenalty = 100 - weatherPenalty(windChill, 100.0, 0.18, -5.0, 1.35);
        }

        double precipitation = weather.getPrecipitation();
        double precipitationPenalty = weatherPenalty(precipitation, 100.0, 0.25, 25.0, 1.2);

        double windSpeed = weather.getWindSpeed10m();
        double windPenalty = weatherPenalty(windSpeed, 100.0, 0.12, 40.0, 1.4);

        double snowPenalty = weatherPenalty(weather.getSnowDepth(), 100.0, 2.0, 7.0, 1.0);

        double slipRisk = 0.0;
        if (weather.getTemperature2m() <= 4.0) {
            slipRisk = 0.1;
        }

        double totalPenalty =
                1
                        - (1 - temperatureRiskPenalty / 100.0)
                        * (1 - precipitationPenalty / 100.0)
                        * (1 - windPenalty / 100.0)
                        * (1 - snowPenalty / 100.0)
                        * 1 - slipRisk;

        double score = 1.0 - totalPenalty;
        score = (double) Math.round(score * 1000.0) / 1000; // round to 3 decimals.
        double weatherScore = clamp(score, 0.0, 1.0);

        return weatherScore;
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
    private double computeWbgt(WeatherResponse weather) {
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

        // approximation cannot cope with very small wind speed or shortwave radiation values.
        if (windSpeed <= 1.0) {
            windSpeed = 1.0;
        }

        if (solarRadiation <= 1.0) {
            solarRadiation = 1.0;
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
        final double H = 0.315;
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
        // formula is only valid for wind speeds above 5km/h. If it is below, simply return ambient temperature.
        if (windSpeed < 5.0) {
            return temperature;
        }
        return 13.12 + 0.6215 * temperature + (0.3965 * temperature - 11.37) * Math.pow(windSpeed, 0.16);
    }

    // non-linear continuous function to calculate the weather penalties imposed on the weather score.
    private double weatherPenalty(double weatherParameter, double maximumPenalty, double steepness, double midPoint, double shape) {
        // Generalized logistic / Richards curve
        double penalty = maximumPenalty / Math.pow(1.0 + Math.pow(Math.E, -steepness * (weatherParameter - midPoint)), 1.0 / shape);

        // clamp for safety
        return Math.min(100.0, Math.max(0.0, penalty));
    }

    // Estimates the speed penalty using different weather factors.
    @Override
    public double estimatePerformancePenalty(WeatherResponse weather) throws ValidationException {
        validator.validateWeatherValues(weather);
        final double optimalWbgt = 10.0;
        final double heatSlope = 0.25;
        final double coldSlope = 0.15;

        final double wbgt = computeWbgt(weather);
        final double delta = wbgt - optimalWbgt;
        final double precipitation = weather.getPrecipitation();

        double penaltyBase;
        if (delta > 0) {
            penaltyBase = delta * heatSlope;
        } else {
            penaltyBase = -delta * coldSlope;
        }

        final double modifier = complexityModifier(weather);
        final double penaltyPercent = penaltyBase * modifier;

        final double totalPenalty = precipitationSlowdown(precipitation) + penaltyPercent;

        LOGGER.trace("Estimated performance penalty: {}", totalPenalty);
        return totalPenalty;
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

    // Helper function to normalize values.
    private double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) {
            return 0.0;
        }
        return Math.max(min, Math.min(max, v));
    }

    // Calculates the impact of precipitation in percent.
    private double precipitationSlowdown(double precipMm) {
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
        return impact;
    }

    private enum WindIntensity {
        CALM,
        GENTLE_BREEZE,
        MODERATE_BREEZE,
        STRONG_BREEZE,
        GALE_AND_BEYOND
    }

    private enum HeatRiskCategory {
        EXTREME_COLD,
        SEVERE_COLD,
        VERY_HIGH_COLD_RISK,
        HIGH_COLD_RISK,
        MODERATE_COLD,
        LOW_COLD,
        NEUTRAL_COLD,
        BELOW_WBGT_RANGE,
        OPTIMAL,
        LOW_HEAT,
        MODERATE_HEAT,
        HIGH_HEAT,
        EXTREME_HEAT
    }

    public enum PrecipitationIntensity {
        NONE,
        TRACE,
        VERY_LIGHT,
        LIGHT,
        MODERATE,
        HEAVY,
        VIOLENT
    }

    // Classification of the heat risk: https://www.weather.gov/arx/wbgt
    private HeatRiskCategory classifyHeatRisk(double wbgt) {
        if (wbgt < 10.0) {
            return HeatRiskCategory.BELOW_WBGT_RANGE;
        }
        if (wbgt >= 10.0 && wbgt < 18.3) {
            return HeatRiskCategory.OPTIMAL;
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
        return HeatRiskCategory.NEUTRAL_COLD;
    }

    // Classifies precipitation (mm/h) into categories of severity. Source: https://rainsimulator.com/guides/intensity-categories
    private PrecipitationIntensity classifyPrecipitationSeverity(double precipitation) {
        if (precipitation == 0.0) {
            return PrecipitationIntensity.NONE;
        }
        if (precipitation > 0.0 && precipitation < 0.25) {
            return PrecipitationIntensity.TRACE;
        }
        if (precipitation >= 0.25 && precipitation < 1.0) {
            return PrecipitationIntensity.VERY_LIGHT;
        }
        if (precipitation >= 1 && precipitation < 2.5) {
            return PrecipitationIntensity.LIGHT;
        }
        if (precipitation >= 2.5 && precipitation < 10) {
            return PrecipitationIntensity.MODERATE;
        }
        if (precipitation >= 10 && precipitation < 50) {
            return PrecipitationIntensity.HEAVY;
        }
        return PrecipitationIntensity.VIOLENT;
    }

    // Classifies wind speed (km/h | 10 m above ground) into categories of severity. Source: https://www.rmets.org/metmatters/beaufort-wind-scale
    private WindIntensity classifyWindSeverity(double windSpeed) {
        if (windSpeed < 1) {
            return WindIntensity.CALM;
        }
        if (windSpeed >= 1 && windSpeed <= 19) {
            return WindIntensity.GENTLE_BREEZE;
        }
        if (windSpeed >= 20 && windSpeed < 38) {
            return WindIntensity.MODERATE_BREEZE;
        }
        if (windSpeed >= 38 && windSpeed <= 49) {
            return WindIntensity.STRONG_BREEZE;
        }
        return WindIntensity.GALE_AND_BEYOND;
    }

    @Override
    public String evaluateWeatherScore(double weatherScore) {
        if (weatherScore < 0.0 || weatherScore > 1.0) {
            return "Invalid weather score.";
        }

        if (weatherScore <= 0.1) {
            return "Extremely unfavorable conditions";
        } else if (weatherScore <= 0.2) {
            return "Very challenging conditions";
        } else if (weatherScore <= 0.3) {
            return "Unfavorable weather";
        } else if (weatherScore <= 0.4) {
            return "Challenging conditions";
        } else if (weatherScore <= 0.5) {
            return "Some impairments present";
        } else if (weatherScore <= 0.6) {
            return "Acceptable conditions";
        } else if (weatherScore <= 0.7) {
            return "Good running conditions";
        } else if (weatherScore <= 0.8) {
            return "Very favorable conditions";
        } else if (weatherScore <= 0.9) {
            return "Excellent weather";
        } else {
            return "Near-perfect conditions.";
        }
    }

    @Override
    public WeatherSummaryDto buildWeatherDescription(WeatherResponse weather) throws ValidationException {
        validator.validateWeatherValues(weather);
        double wbgt = computeWbgt(weather);
        double windChill;
        HeatRiskCategory heatRisk = classifyHeatRisk(wbgt);
        if (heatRisk == HeatRiskCategory.BELOW_WBGT_RANGE) {
            windChill = calculateWindChill(weather.getTemperature2m(), weather.getWindSpeed10m());
            heatRisk = classifyColdRisk(windChill);
        }

        WindIntensity windIntensity = classifyWindSeverity(weather.getWindSpeed10m());
        PrecipitationIntensity precipitationIntensity = classifyPrecipitationSeverity(weather.getPrecipitation());

        return build(heatRisk, windIntensity, precipitationIntensity);
    }

    // Build the extended weather summary.
    private WeatherSummaryDto build(HeatRiskCategory heatRisk, WindIntensity windIntensity, PrecipitationIntensity precipitationIntensity) {
        return new WeatherSummaryDto(
                temperatureToText(heatRisk),
                windToText(windIntensity),
                precipitationToText(precipitationIntensity));
    }

    // Provides a description to temperature.
    private static String temperatureToText(HeatRiskCategory heatRisk) {
        return switch (heatRisk) {
            case EXTREME_COLD -> "Extreme cold. DANGER! Outdoor conditions are hazardous. Stay indoors.";
            case SEVERE_COLD -> """
                    Severe cold. \
                    
                    Severe risk of hypothermia if outside for long periods without adequate clothing or shelter from wind and cold.\
                    
                    Severe risk of frostbite: Check face and extremities frequently for numbness or whiteness.\
                    
                    Cover all exposed skin in layers of warm clothing, keep active and stay dry. Be prepared to cut short or cancel your run.""";
            case VERY_HIGH_COLD_RISK -> """
                    Very cold conditions. \
                    
                    Very high risk of frostbite: Check face and extremities for numbness or whiteness.\
                    
                    Very high risk of hypothermia if outside for long periods without adequate clothing or shelter from wind and cold.\
                    
                    Cover all exposed skin in layers of warm clothing, keep active and stay dry. Be prepared to cut short or cancel your run.""";
            case HIGH_COLD_RISK -> """
                    Beyond uncomfortable cold conditions.
                    
                    High risk of frostnip or frostbite: Check face and extremities for numbness or whiteness.\
                    
                    High risk of hypothermia if outside for long periods without adequate clothing or shelter from wind and cold.\
                    
                    Cover all exposed skin in layers of warm clothing, keep active and stay dry. Be prepared to cut short or cancel your run.""";
            case MODERATE_COLD -> """
                    Uncomfortably cold conditions.\
                    
                    Risk of hypothermia and frostbite if outside for long periods without adequate protection.\
                    
                    Dress in layers of warm clothing, keep active and stay dry.""";
            case LOW_COLD -> """
                    Very cool conditions.\
                    
                    Slight increase in discomfort.\
                    
                    Dress warmly and stay dry.""";
            case NEUTRAL_COLD -> "Cool conditions, generally favorable for running.";
            case BELOW_WBGT_RANGE ->
                    "Slightly cool conditions, favorable for running."; // this case should never happen
            case OPTIMAL -> "Optimal temperature for running.";
            case LOW_HEAT -> """
                    Warm conditions.\
                    
                    Heat stress and other heat illnesses are possible.\
                    
                    If you are a high risk individual, monitor yourself.""";
            case MODERATE_HEAT -> """
                    Hot conditions.\
                    
                    Risk of heat illnesses for everyboy are increased.""";
            case HIGH_HEAT -> """
                    Very hot conditions.\
                    
                    If you are unfit or not acclimatized, running becomes dangerous.""";
            case EXTREME_HEAT -> """
                    Extremely hot conditions.\
                    
                    Cancel your run.""";
        };
    }

    // Provides a description to wind speed.
    private static String windToText(WindIntensity windIntensity) {
        return switch (windIntensity) {
            case CALM -> "Barely any wind, expect no difficulties.";
            case GENTLE_BREEZE -> "Light breeze that may slightly affect your pacing.";
            case MODERATE_BREEZE -> "Noticeable wind, expect some resistance.";
            case STRONG_BREEZE -> "These strong winds will cause a significant impact on your run.";
            case GALE_AND_BEYOND -> "Dangerous wind conditions, seek shelter and avoid the outside.";
        };
    }

    // Provides a description to precipitation.
    private static String precipitationToText(PrecipitationIntensity precipitationIntensity) {
        return switch (precipitationIntensity) {
            case NONE -> "Dry conditions with optimal traction.";
            case TRACE -> "Light drizzle, slightly slick surfaces possible.";
            case VERY_LIGHT -> "Very light precipitation causes a mild cooling effect and reduced traction.";
            case LIGHT -> "Light precipitation causes a moderate cooling effect and reduced traction.";
            case MODERATE -> "In this moderate precipitation expect wet clothing and a noticeable impact on your pace.";
            case HEAVY -> "In this heavy precipitation you will be completely drenched. Expect reduced visibility and significant traction loss.";
            case VIOLENT -> "Very violent precipitation, consider staying at home.";
        };
    }
}