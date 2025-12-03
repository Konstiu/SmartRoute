package com.smartroute.smartroute1.unittest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartroute.smartroute1.endpoint.dto.WeatherDto;
import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.repository.WeatherRepository;
import com.smartroute.smartroute1.service.WeatherService;
import com.smartroute.smartroute1.entity.enums.HeatRiskCategory;
import com.smartroute.smartroute1.endpoint.dto.WeatherImpactDto;
import com.smartroute.smartroute1.service.impl.WeatherServiceImpl;
import jakarta.transaction.Transactional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles({"test", "generateData"})
@Transactional
class WeatherServiceTest {
    @Autowired
    private WeatherService service;

    public static MockWebServer mockWeatherApi;

    @Autowired
    private WeatherRepository weatherRepository;

    @BeforeAll
    static void setupServer() throws IOException {
        mockWeatherApi = new MockWebServer();
        mockWeatherApi.start();
    }

    @AfterAll
    static void shutdownServer() throws IOException {
        mockWeatherApi.shutdown();
    }

    private static WeatherDto getTestWeatherDto() {
        WeatherDto weatherDto = new WeatherDto();
        weatherDto.setTime("2025-11-28T00:00");
        weatherDto.setTemperature2m(-21.4);
        weatherDto.setPrecipitation(0.0);
        weatherDto.setRelativeHumidity(87.0);
        weatherDto.setWindSpeed10m(13.7);
        weatherDto.setShortWaveRadiation(0.0);

        return weatherDto;
    }

    @BeforeEach
    void resetData() {
        weatherRepository.deleteAll();
    }

    private String openMeteoJsonFromDtos(List<WeatherDto> dtos) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode root = mapper.createObjectNode();
        ObjectNode hourly = root.putObject("hourly");

        ArrayNode t = hourly.putArray("time");
        ArrayNode temp = hourly.putArray("temperature_2m");
        ArrayNode prec = hourly.putArray("precipitation");
        ArrayNode wind = hourly.putArray("wind_speed_10m");
        ArrayNode hum = hourly.putArray("relative_humidity_2m");
        ArrayNode rad = hourly.putArray("shortwave_radiation");
        ArrayNode dewPoint = hourly.putArray("dew_point_2m");
        ArrayNode surfacePressure = hourly.putArray("surface_pressure");
        ArrayNode directRadiation = hourly.putArray("direct_radiation");
        ArrayNode diffuseRadiation = hourly.putArray("diffuse_radiation");
        ArrayNode snowDepth = hourly.putArray("snow_depth");

        for (WeatherDto dto : dtos) {
            t.add(dto.getTime());
            temp.add(dto.getTemperature2m());
            prec.add(dto.getPrecipitation());
            wind.add(dto.getWindSpeed10m());
            hum.add(dto.getRelativeHumidity());
            rad.add(dto.getShortWaveRadiation());
            dewPoint.add(dto.getDewPoint());
            surfacePressure.add(dto.getSurfacePressure());
            directRadiation.add(dto.getDirectRadiation());
            diffuseRadiation.add(dto.getDiffuseRadiation());
            snowDepth.add(dto.getSnowDepth());
        }

        return mapper.writeValueAsString(root);
    }


    @Test
    void testGetWeather_savesWeather() throws Exception {
        WeatherDto weatherDto = getTestWeatherDto();
        String json = openMeteoJsonFromDtos(List.of(weatherDto));

        mockWeatherApi.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(json)
        );

        List<WeatherDto> result = service.getHourlyWeather(20, 40);

        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(1, result.size())
        );

        List<WeatherResponse> stored = weatherRepository.findAll();

        WeatherResponse saved = stored.getFirst();

        assertAll(
                () -> assertEquals("2025-11-28T00:00", saved.getTime()),
                () -> assertEquals(-21.4, saved.getTemperature2m()),
                () -> assertEquals(0.0, saved.getPrecipitation()),
                () -> assertEquals(87.0, saved.getRelativeHumidity()),
                () -> assertEquals(13.7, saved.getWindSpeed10m()),
                () -> assertEquals(0.0, saved.getShortWaveRadiation())
        );
    }

    @Test
    void testGetWeather_multipleWeatherDates_savedCorrectly() throws Exception {
        WeatherDto weather1 = getTestWeatherDto();
        WeatherDto weather2 = getTestWeatherDto();
        weather2.setTime("2025-11-29T00:00");
        weather2.setTemperature2m(-20.5);

        String json = openMeteoJsonFromDtos(List.of(weather1, weather2));

        mockWeatherApi.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(json)
        );

        List<WeatherDto> result = service.getHourlyWeather(20, 40);

        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(2, result.size())
        );

        List<WeatherResponse> stored = weatherRepository.findAll();

        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(2, result.size()),
                () -> assertEquals(2, stored.size()),
                () -> assertEquals("2025-11-28T00:00", result.getFirst().getTime()),
                () -> assertEquals("2025-11-29T00:00", result.get(1).getTime())
        );
    }

    @Test
    void testGetWeather_api4xx_throwsBadRequest() throws Exception {
        mockWeatherApi.enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Authorization Error\"}")
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getHourlyWeather(20, 40));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode()),
                () -> assertTrue(ex.getReason().contains("Open-Meteo API 4xx"))
        );
    }

    @Test
    void testGetWeather_api5xx_throwsBadGateway() throws Exception {
        mockWeatherApi.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Internal Server Error\"}")
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getHourlyWeather(20, 40));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode()),
                () -> assertTrue(ex.getReason().contains("Open-Meteo API 5xx"))
        );
    }

    @Test
    void testGetWeather_missingFields_throwsValidationException() throws Exception {
        String json = """
                {
                    "hourly": {
                        "time": ["2025-11-28T00:00"]
                    }
                }
                """;

        mockWeatherApi.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json)
        );

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.getHourlyWeather(20, 40)
        );

        assertAll(
                () -> assertTrue(ex.errors().contains("Weather API response is missing required hourly fields"))
        );
    }


    @Test
    void testGetWeather_tooSmallLongAndLat_throwsValidationException() {
        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.getHourlyWeather(-200, -200)
        );

        assertAll(
                () -> assertTrue(ex.errors().contains("latitude is smaller than -90")),
                () -> assertTrue(ex.errors().contains("longitude is smaller than -180"))
        );
    }

    @Test
    void testGetWeather_tooLargeLongAndLat_throwsValidationException() {
        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.getHourlyWeather(200, 200)
        );

        assertAll(
                () -> assertTrue(ex.errors().contains("latitude is larger than 90")),
                () -> assertTrue(ex.errors().contains("longitude is larger than 180"))
        );
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public WebClient mockWeatherWebClient() {
            return WebClient.builder()
                    .baseUrl(mockWeatherApi.url("/").toString())
                    .filter((request, next) -> {

                        URI rewritten = mockWeatherApi.url("/").resolve(
                                request.url().getPath()
                        ).uri();

                        ClientRequest newRequest = ClientRequest.create(request.method(), rewritten)
                                .headers(h -> h.addAll(request.headers()))
                                .body(request.body())
                                .build();

                        return next.exchange(newRequest);
                    })
                    .build();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Test
    void testComputeWbgt_realisticValues() {
        WeatherResponse weather = new WeatherResponse();
        weather.setTemperature2m(30.0);
        weather.setRelativeHumidity(60.0);
        weather.setWindSpeed10m(2.0);
        weather.setShortWaveRadiation(500.0);
        weather.setDirectRadiation(350.0);
        weather.setDiffuseRadiation(150.0);
        weather.setLongitude(16.37);
        weather.setLatitude(48.21);
        weather.setSurfacePressure(1013.0);
        weather.setDewPoint(20.0);
        weather.setTime("2025-06-20T14:00");

    }

    @Test
    @DisplayName("Neutral WBGT should produce minimal penalty and NEUTRAL heat risk")
    void test() {
        WeatherResponse weather = new WeatherResponse();
        weather.setPrecipitation(0.0);
        weather.setTemperature2m(25.0);
        weather.setRelativeHumidity(60.0);
        weather.setWindSpeed10m(5.0);
        weather.setShortWaveRadiation(500.0);
        weather.setDirectRadiation(350.0);
        weather.setDiffuseRadiation(150.0);
        weather.setLongitude(16.37);
        weather.setLatitude(48.21);
        weather.setSurfacePressure(1013.0);
        weather.setDewPoint(20.0);
        weather.setSnowDepth(0.0);
        weather.setTime("2025-06-20T14:00");

        WeatherImpactDto result = service.calculateWeatherScore(weather, 20, 50000);

        LOGGER.info("Weather Penalty, Weather Score, RISK: {}, {}, {}", result.getPenaltyPercent(), result.getWeatherScore(), result.getRiskCategory());
    }


//
//    @Test
//    @DisplayName("Neutral WBGT should produce minimal penalty and NEUTRAL heat risk")
//    void givenNeutralWeatherWhenEstimatingImpactThenNeutralRiskAndMinimalPenalty() {
//
//        long baseTime = 3600; // 1 hour
//
//        WeatherImpactDto result = service.estimateImpact(
//                10000,
//                baseTime,
//                15,    // temperature
//                50,    // humidity
//                200,   // solar radiation
//                3,      // wind speed
//                0,
//                20
//        );
//
//        assertAll("NEUTRAL+TEN_K_LIKE impact calculations",
//                () -> assertEquals(HeatRiskCategory.NEUTRAL, result.getRisk()),
//                () -> assertTrue(result.getAdjustedTimeSeconds() >= 3500),
//                () -> assertTrue(result.getAdjustedTimeSeconds() <= 3700)
//        );
//    }
//
//    @Test
//    @DisplayName("Hot weather should increase penalty for marathon-like event")
//    void givenHotWeatherWhenEstimatingImpactThenExtremeHeatRiskAndIncreasedPenalty() {
//
//        long baseTime = 7200; // 2 hours
//
//        WeatherImpactDto result = service.estimateImpact(
//                40000,
//                baseTime,
//                30,    // hot temperature
//                70,    // humid
//                800,   // strong sun
//                1,      // low wind
//                0,
//                20
//        );
//
//        assertAll("EXTREME_HEAT+MARATHON impact calculations",
//                () -> assertEquals(HeatRiskCategory.EXTREME_HEAT, result.getRisk()),
//                () -> assertTrue(result.getAdjustedTimeSeconds() > baseTime),
//                () -> assertTrue(result.getPenaltyPercent() > 0)
//        );
//    }
//
//    @Test
//    @DisplayName("Cold weather should produce time penalty due to cold slope")
//    void givenColdWeatherWhenEstimatingImpactThenColdCoolRiskAndAdjustedTime() {
//
//        long baseTime = 5000;
//
//        WeatherImpactDto result = service.estimateImpact(
//                5000,
//                baseTime,
//                0,       // freezing temperature
//                30,
//                0,
//                5,
//                0,
//                20
//        );
//
//        assertAll("COLD_COOL+FIVE_K_LIKE impact calculations",
//                () -> assertEquals(HeatRiskCategory.COLD_COOL, result.getRisk()),
//                () -> assertTrue(result.getAdjustedTimeSeconds() > 0),
//                () -> assertNotEquals(baseTime, result.getAdjustedTimeSeconds())
//        );
//    }
//
//    @Test
//    @DisplayName("Extreme heat should classify as EXTREME_HEAT")
//    void givenExtremeHeatConditionsWhenEstimatingImpactThenExtremeHeatRisk() {
//
//        WeatherImpactDto result = service.estimateImpact(
//                10000,
//                3600,
//                40,
//                90,
//                1000,
//                0,
//                0,
//                20
//        );
//
//        assertAll("EXTREME_HEAT+TEN_K_LIKE impact calculations",
//                () -> assertEquals(HeatRiskCategory.EXTREME_HEAT, result.getRisk()),
//                () -> assertTrue(result.getAdjustedTimeSeconds() > 3600)
//        );
//    }
//
//    @Test
//    @DisplayName("High solar radiation + low wind should trigger extra WBGT sun correction")
//    void givenHighSolarLowWindWhenEstimatingImpactThenAdditionalSunCorrectionApplied() {
//
//        WeatherImpactDto lowWindHighSun = service.estimateImpact(
//                40000,
//                3600,
//                25,
//                60,
//                700, // > 600 = strong sun
//                1,    // low wind (<2)
//                0,
//                20
//        );
//
//        WeatherImpactDto normal = service.estimateImpact(
//                40000,
//                3600,
//                25,
//                60,
//                100,
//                5,
//                0,
//                20
//        );
//
//        assertAll("High solar test",
//                () -> assertTrue(lowWindHighSun.getAdjustedTimeSeconds() > normal.getAdjustedTimeSeconds())
//        );
//    }
//
//    @Test
//    @DisplayName("Precipitation Impact Test")
//    void givenDifferentPrecipLevelsWhenEstimatingImpactThenHigherPrecipitationSlowsRunner() {
//
//        WeatherImpactDto noPrecipitation = service.estimateImpact(
//                40000,
//                3600,
//                25,
//                60,
//                700, // > 600 = strong sun
//                1,    // low wind (<2)
//                0,
//                20
//        );
//
//        WeatherImpactDto mildPrecipitation = service.estimateImpact(
//                40000,
//                3600,
//                25,
//                60,
//                100,
//                5,
//                15,
//                20
//        );
//
//        WeatherImpactDto highPrecipitation = service.estimateImpact(
//                40000,
//                3600,
//                25,
//                60,
//                100,
//                5,
//                40,
//                20
//        );
//
//        assertAll("Slower with higher precipitation",
//                () -> assertTrue(highPrecipitation.getAdjustedTimeSeconds() > mildPrecipitation.getAdjustedTimeSeconds()),
//                () -> assertTrue(mildPrecipitation.getAdjustedTimeSeconds() > noPrecipitation.getAdjustedTimeSeconds())
//        );
//    }
//
//    @Test
//    @DisplayName("Influence of Age on Precipitation Impact Test")
//    void givenDifferentAgesWithPrecipitationWhenEstimatingImpactThenOlderRunnersGetHigherPenalty() {
//
//        WeatherImpactDto youngest = service.estimateImpact(
//                40000,
//                3600,
//                25,
//                60,
//                700, // > 600 = strong sun
//                1,    // low wind (<2)
//                30,
//                20
//        );
//
//        WeatherImpactDto secondOldest = service.estimateImpact(
//                40000,
//                3600,
//                25,
//                60,
//                100,
//                5,
//                30,
//                30
//        );
//
//        WeatherImpactDto oldest = service.estimateImpact(
//                40000,
//                3600,
//                25,
//                60,
//                100,
//                5,
//                30,
//                40
//        );
//
//        assertAll("Slower with higher age in precipitation",
//                () -> assertTrue(secondOldest.getAdjustedTimeSeconds() > youngest.getAdjustedTimeSeconds()),
//                () -> assertTrue(oldest.getAdjustedTimeSeconds() > secondOldest.getAdjustedTimeSeconds())
//        );
//    }
}