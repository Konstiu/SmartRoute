package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.WeatherDto;
import com.smartroute.smartroute1.endpoint.dto.WeatherImpactDto;
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
            description = "Get weather score, penalty percent and risk evaluations.",
            summary = "Get weather score.")
    @PostMapping("/score")
    @PermitAll
    public ResponseEntity<WeatherImpactDto> calculateWeatherScore(@RequestBody WeatherResponse weather, @RequestParam("age") int age, @RequestParam("distance") int distanceMeters) throws ValidationException {
        WeatherImpactDto weatherImpactDto = weatherService.calculateWeatherScore(weather, age, distanceMeters);
        return ResponseEntity.ok(weatherImpactDto);
    }
}

