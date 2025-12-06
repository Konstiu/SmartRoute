package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherEndpoint {
    private final WeatherService weatherService;

    @GetMapping("/hourly")
    @Secured("ROLE_USER")
    @Operation(summary = "Get hourly weather data",
            description = "Returns hourly weather information including temperature, precipitation, wind and radiation values.")
    public ResponseEntity<WeatherResponse> getWeatherAtTime(@RequestParam("latitude") double latitude, @RequestParam("longitude") double longitude, @RequestParam("timeUtc") String timeUtc) throws ValidationException {
        WeatherResponse weatherResponse = weatherService.getWeatherAtTime(latitude, longitude, timeUtc);
        return ResponseEntity.ok(weatherResponse);
    }

    @Operation(
            description = "Returns the weather score ranging from 0 to 1, where 1 are best conditions for outdoor activities.",
            summary = "Get weather score.")
    @PostMapping("/score")
    @PermitAll
    public double calculateWeatherScore(@RequestBody WeatherResponse weather) throws ValidationException {
        return weatherService.calculateWeatherScore(weather);
    }

    @Operation(
            description = "Estimates the penalty, the weather imposes on running speed in percent.",
            summary = "Get performance penalty")
    @PostMapping("/penalty")
    @PermitAll
    public double estimatePerformancePenalty(@RequestBody WeatherResponse weather) throws ValidationException {
        return weatherService.estimatePerformancePenalty(weather);
    }

    @Operation(
            description = "Generates a description fitting of the given weather data.",
            summary = "Get weather description")
    @PostMapping("/description")
    @PermitAll
    public String buildWeatherDescription(@RequestBody WeatherResponse weather) throws ValidationException {
        return weatherService.buildWeatherDescription(weather);
    }

    @Operation(
            description = "Maps the weather score (0.0 - 1.0) to a very brief description.",
            summary = "Get weather score description")
    @PostMapping("/evaluate")
    @PermitAll
    String evaluateWeatherScore(@RequestParam("weatherScore") double weatherScore) {
        return weatherService.evaluateWeatherScore(weatherScore);
    }
}

