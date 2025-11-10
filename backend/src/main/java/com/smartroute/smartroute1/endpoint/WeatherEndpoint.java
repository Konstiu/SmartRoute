package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.service.WeatherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
public class WeatherEndpoint {

    private final WeatherService weatherService;

    public WeatherEndpoint(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping
    public WeatherResponse getWeather(
            @RequestParam double lat,
            @RequestParam double lon
    ) {
        return weatherService.getHourlyWeather(lat, lon);
    }
}
