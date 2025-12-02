package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.WeatherDto;
import com.smartroute.smartroute1.endpoint.dto.WeatherImpactDto;
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

import java.util.List;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherEndpoint {
    private final WeatherService weatherService;

    @GetMapping("/hourly")
    @Secured("ROLE_USER")
    @Operation(summary = "Get hourly weather data",
            description = "Returns hourly weather information including temperature, precipitation, wind and radiation values.")
    public ResponseEntity<List<WeatherDto>> getWeather(@RequestParam("latitude") double lat, @RequestParam("longitude") double lon) throws ValidationException {
        List<WeatherDto> weatherResponse = weatherService.getHourlyWeather(lat, lon);
        return ResponseEntity.ok(weatherResponse);
    }
}

//    @Operation(
//            description = "Get the impact the weather data has on a running route.",
//            summary = "Get weather impact.")
//    @PostMapping("/impact")
//    @PermitAll
//    public WeatherImpactDto estimateImpact(@RequestParam("distance") int distance,
//                                           @RequestParam("time") long baseTimeSeconds,
//                                           @RequestParam("temperature") double temperature,
//                                           @RequestParam("relativeHumidity") double relativeHumidity,
//                                           @RequestParam("shortwaveRadiation") double shortwaveRadiation,
//                                           @RequestParam("windSpeed") double windSpeed,
//                                           @RequestParam("precipitation") double precipitation,
//                                           @RequestParam("runnerAge") int age) {
//        return weatherService.estimateImpact(distance, baseTimeSeconds, temperature,
//                relativeHumidity, shortwaveRadiation, windSpeed, precipitation, age);
//    }

