package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.WeatherDto;
import com.smartroute.smartroute1.endpoint.mapper.WeatherMapper;
import com.smartroute.smartroute1.entity.weather.EventType;
import com.smartroute.smartroute1.entity.weather.WeatherImpactResult;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@RestController
@RequestMapping("/api/weather")
public class WeatherEndpoint {

    @Autowired
    private final WeatherService weatherService;

    @Autowired
    private final WeatherMapper weatherMapper;

    public WeatherEndpoint(WeatherService weatherService, WeatherMapper weatherMapper) {
        this.weatherService = weatherService;
        this.weatherMapper = weatherMapper;
    }

    @GetMapping("/hourly")
    @Secured("ROLE_USER")
    @Operation(summary = "Get hourly weather data",
            description = "Returns hourly weather information including temperature, precipitation, wind and radiation values.")
    public ResponseEntity<List<WeatherDto>> getWeather(@RequestParam double lat, @RequestParam double lon) throws ValidationException {
        List<WeatherDto> weatherResponse = weatherService.getHourlyWeather(lat, lon);
        return ResponseEntity.ok(weatherResponse);
    }

    @Operation(
            description = "Get the impact the weather data has on a running route.",
            summary = "Get weather impact.")
    @PostMapping("/impact")
    @PermitAll
    public WeatherImpactResult estimateImpact(@RequestParam EventType eventType,
                                              @RequestParam long baseTimeSeconds,
                                              @RequestParam double temperature,
                                              @RequestParam double relativeHumidity,
                                              @RequestParam double shortwaveRadiation,
                                              @RequestParam double windSpeed,
                                              @RequestParam double precipitation,
                                              @RequestParam int age) {
        WeatherImpactResult impactResult = weatherService.estimateImpact(eventType, baseTimeSeconds, temperature,
                relativeHumidity, shortwaveRadiation, windSpeed, precipitation, age);
        return impactResult;
    }
}
