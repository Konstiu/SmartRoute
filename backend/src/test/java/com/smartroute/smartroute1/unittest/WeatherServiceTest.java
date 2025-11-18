package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.entity.WeatherResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class WeatherServiceTest {
    @Autowired
    private TestRestTemplate restTemplate;

    @DisplayName("GET /api/weather returns 200 OK and valid hourly weather data")
    @Test
    void testWeatherEndpointReturnsData_200() {
        String url = "/api/weather?lat=48.210033&lon=16.363449";
        ResponseEntity<WeatherResponse> response = restTemplate.getForEntity(url, WeatherResponse.class);

        final WeatherResponse body = response.getBody();

        assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertNotNull(body),
                () -> {
                    assertNotNull(body);
                    assertFalse(body.getTime().isEmpty());
                },
                () -> {
                    assertNotNull(body);
                    assertFalse(body.getTemperature2m().isEmpty());
                },
                () -> {
                    assertNotNull(body);
                    assertFalse(body.getWindSpeed10m().isEmpty());
                },
                () -> {
                    assertNotNull(body);
                    assertFalse(body.getPrecipitation().isEmpty());
                }
        );
    }
}