package com.smartroute.smartroute1.service.validators;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartroute.smartroute1.endpoint.dto.WeatherDto;
import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

@Component
public class WeatherValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public void validateHourlyData(JsonNode root) throws ValidationException {
        List<String> errors = new ArrayList<>();
        LOGGER.trace("Validation of hourly data: {}", root);

        if (root == null || root.isMissingNode()) {
            errors.add("Root hourly not found");
            throw new ValidationException("Errors while verifying weather data:", errors);
        }

        JsonNode hourly = root.path("hourly");

        if (!hourly.has("time")
                || !hourly.has("temperature_2m")
                || !hourly.has("precipitation")
                || !hourly.has("wind_speed_10m")
                || !hourly.has("relative_humidity_2m")
                || !hourly.has("shortwave_radiation")
                || !hourly.has("dew_point_2m")
                || !hourly.has("surface_pressure")
                || !hourly.has("direct_radiation")
                || !hourly.has("diffuse_radiation")
                || !hourly.has("snow_depth")) {

            errors.add("Weather API response is missing required hourly fields");
            throw new ValidationException("Errors while verifying weather data:", errors);
        }
    }


    public void validateCoordinates(double latitude, double longitude) throws ValidationException {
        List<String> errors = new ArrayList<>();
        LOGGER.trace("Validation of latitude and longitude: {}, {}", latitude, longitude);

        if (latitude < -90) {
            errors.add("latitude is smaller than -90");
        }

        if (latitude > 90) {
            errors.add("latitude is larger than 90");
        }

        if (longitude < -180) {
            errors.add("longitude is smaller than -180");
        }

        if (longitude > 180) {
            errors.add("longitude is larger than 180");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Errors while verifying lat. and long.:", errors);
        }
    }

    public void validateListSizes(List<?>... lists) throws ValidationException {
        LOGGER.trace("Validation of the size of weather data lists : {}", lists.length);
        int size = lists[0].size();
        for (List<?> list : lists) {
            if (list.size() != size) {
                throw new ValidationException("Hourly lists differ in size — invalid API data");
            }
        }
    }

    public void validateWeatherValues(WeatherResponse weather) throws ValidationException {
        List<String> errors = new ArrayList<>();
        LOGGER.trace("Validating weather values: {}", weather);

        // Temperature 2m (°C) — Earth range roughly −90°C to +60°C
        if (weather.getTemperature2m() == null) {
            errors.add("temperature2m is null");
        } else if (weather.getTemperature2m() < -100) {
            errors.add("temperature2m is unrealistically low (< -100°C)");
        } else if (weather.getTemperature2m() > 70) {
            errors.add("temperature2m is unrealistically high (> 70°C)");
        }

        // Wind speed 10m (m/s) — Max recorded around 113 m/s in storms
        if (weather.getWindSpeed10m() == null) {
            errors.add("windSpeed10m is null");
        } else if (weather.getWindSpeed10m() < 0) {
            errors.add("windSpeed10m cannot be negative");
        } else if (weather.getWindSpeed10m() > 120) {
            errors.add("windSpeed10m is unrealistically high (> 120 m/s)");
        }

        // Precipitation (mm/h)
        // Extreme: ~80–100 mm/h in tropical events
        if (weather.getPrecipitation() == null) {
            errors.add("precipitation is null");
        } else if (weather.getPrecipitation() < 0) {
            errors.add("precipitation cannot be negative");
        } else if (weather.getPrecipitation() > 200) {
            errors.add("precipitation is unrealistically high (> 200 mm/h)");
        }

        // Relative humidity (%)
        if (weather.getRelativeHumidity() == null) {
            errors.add("relativeHumidity is null");
        } else if (weather.getRelativeHumidity() < 0 || weather.getRelativeHumidity() > 100) {
            errors.add("relativeHumidity must be between 0 and 100%");
        }

        // Shortwave radiation (W/m²) — Solar max ~ 1200 W/m² at Earth's surface
        if (weather.getShortWaveRadiation() == null) {
            errors.add("shortWaveRadiation is null");
        } else if (weather.getShortWaveRadiation() < 0) {
            errors.add("shortWaveRadiation cannot be negative");
        } else if (weather.getShortWaveRadiation() > 1500) {
            errors.add("shortWaveRadiation is unrealistically high (> 1500 W/m²)");
        }

        // Direct radiation (W/m²)
        if (weather.getDirectRadiation() == null) {
            errors.add("directRadiation is null");
        } else if (weather.getDirectRadiation() < 0) {
            errors.add("directRadiation cannot be negative");
        } else if (weather.getDirectRadiation() > 1500) {
            errors.add("directRadiation is unrealistically high (> 1500 W/m²)");
        }

        // Diffuse radiation (W/m²)
        if (weather.getDiffuseRadiation() == null) {
            errors.add("diffuseRadiation is null");
        } else if (weather.getDiffuseRadiation() < 0) {
            errors.add("diffuseRadiation cannot be negative");
        } else if (weather.getDiffuseRadiation() > 800) {
            errors.add("diffuseRadiation is unrealistically high (> 800 W/m²)");
        }

        // Surface pressure (hPa)
        // Realistic range: 870 (typhoon) to ~1085 (cold high pressure)
        if (weather.getSurfacePressure() == null) {
            errors.add("surfacePressure is null");
        } else if (weather.getSurfacePressure() < 800) {
            errors.add("surfacePressure is unrealistically low (< 800 hPa)");
        } else if (weather.getSurfacePressure() > 1100) {
            errors.add("surfacePressure is unrealistically high (> 1100 hPa)");
        }

        // Dew point (°C)
        // Dew point cannot exceed air temperature. Realistic global min ~ −80°C.
        if (weather.getDewPoint() == null) {
            errors.add("dewPoint is null");
        } else {
            double dew = weather.getDewPoint();
            Double temp = weather.getTemperature2m();

            if (dew < -100) {
                errors.add("dewPoint is unrealistically low (< -100°C)");
            }
            if (dew > 50) {
                errors.add("dewPoint is unrealistically high (> 50°C)");
            }
            if (temp != null && dew > temp) {
                errors.add("dewPoint cannot be higher than temperature2m");
            }
        }

        // Snow depth (cm)
        // Extreme cases: ~1000 cm (10 m) in some mountain regions
        if (weather.getSnowDepth() == null) {
            errors.add("snowDepth is null");
        } else if (weather.getSnowDepth() < 0) {
            errors.add("snowDepth cannot be negative");
        } else if (weather.getSnowDepth() > 2000) { // 20 m — absolute upper bound
            errors.add("snowDepth is unrealistically high (> 2000 cm)");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Weather data validation failed:", errors);
        }
    }

    public void validateAgeAndDistance(int age, int distanceMeters) throws ValidationException {
        List<String> errors = new ArrayList<>();
        LOGGER.trace("Validating age and distance: {}, {}", age, distanceMeters);

        if (age <= 0 || age > 150) {
            errors.add("age is invalid");
        }

        if (distanceMeters <= 0) {
            errors.add("distance is too small");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Age or distance validation failed:", errors);
        }
    }
}
