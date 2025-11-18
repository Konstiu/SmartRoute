package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.entity.WeatherResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class WeatherServiceTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testWeatherEndpointReturnsData() {
        String url = "/api/weather?lat=48.210033&lon=16.363449";
        ResponseEntity<WeatherResponse> response = restTemplate.getForEntity(url, WeatherResponse.class);

        final WeatherResponse body = response.getBody();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(body).isNotNull();
        assertThat(body.getTime()).isNotEmpty();
        assertThat(body.getTemperature2m()).isNotEmpty();
        assertThat(body.getWindSpeed10m()).isNotEmpty();
        assertThat(body.getPrecipitation()).isNotEmpty();
    }
}