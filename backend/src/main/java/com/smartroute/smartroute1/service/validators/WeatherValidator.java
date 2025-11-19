package com.smartroute.smartroute1.service.validators;

import com.fasterxml.jackson.databind.JsonNode;
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
        JsonNode hourly = root.path("hourly");

        if (hourly.isMissingNode() || !hourly.has("time")) {
            errors.add("Weather API response is missing 'hourly.time' data");
        }

        if (!hourly.has("temperature_2m")
                || !hourly.has("precipitation")
                || !hourly.has("wind_speed_10m")
                || !hourly.has("relative_humidity_2m")
                || !hourly.has("shortwave_radiation")
                || !hourly.has("wind_direction")) {
            errors.add("Weather API response is missing required hourly fields");

        }

        int size = hourly.get("time").size();
        if (size == 0) {
            errors.add("Weather API returned no hourly entries");
        }

        if (!errors.isEmpty()) {
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
}
