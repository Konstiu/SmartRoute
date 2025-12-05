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
import com.smartroute.smartroute1.util.Coordinate;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private final static String timeUtc = LocalDate.now(ZoneOffset.UTC).atTime(0, 0).format(TIME_FORMAT);
    private final static Coordinate coordinate = new Coordinate(20.0, 40.0);

    private static WeatherDto getTestWeatherDto() {
        WeatherDto weatherDto = new WeatherDto();
        weatherDto.setTime(timeUtc);
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

        WeatherResponse result = service.getWeatherAtTime(coordinate.getLatitude(), coordinate.getLongitude(), timeUtc);

        assertAll(
                () -> assertNotNull(result)
        );

        List<WeatherResponse> stored = weatherRepository.findAll();

        WeatherResponse saved = stored.getFirst();

        assertAll(
                () -> assertEquals(timeUtc, saved.getTime()),
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
        String timeUtcPlus1 = LocalDateTime.parse(timeUtc, TIME_FORMAT).plusHours(1).format(TIME_FORMAT);
        weather2.setTime(timeUtcPlus1);
        weather2.setTemperature2m(-20.5);

        String json = openMeteoJsonFromDtos(List.of(weather1, weather2));

        mockWeatherApi.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(json)
        );

        WeatherResponse result = service.getWeatherAtTime(coordinate.getLatitude(), coordinate.getLongitude(), timeUtc);

        assertAll(
                () -> assertNotNull(result)
        );

        List<WeatherResponse> stored = weatherRepository.findAll();

        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(2, stored.size()),
                () -> assertEquals(timeUtc, stored.getFirst().getTime()),
                () -> assertEquals(timeUtcPlus1, stored.get(1).getTime())
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
                () -> service.getWeatherAtTime(coordinate.getLatitude(), coordinate.getLongitude(), timeUtc)
        );

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
                () -> service.getWeatherAtTime(coordinate.getLatitude(), coordinate.getLongitude(), timeUtc)
        );

        assertAll(
                () -> assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode()),
                () -> assertTrue(ex.getReason().contains("Open-Meteo API 5xx"))
        );
    }

    @Test
    void testGetWeather_tooSmallLongAndLat_throwsValidationException() {
        Coordinate testCoordinate = new Coordinate(-100.0, -200.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.getWeatherAtTime(testCoordinate.getLatitude(), testCoordinate.getLongitude(), timeUtc)
        );


        assertAll(
                () -> assertTrue(ex.errors().contains("latitude is smaller than -90")),
                () -> assertTrue(ex.errors().contains("longitude is smaller than -180"))
        );
    }

    @Test
    void testGetWeather_tooLargeLongAndLat_throwsValidationException() {
        Coordinate testCoordinate = new Coordinate(100.0, 200.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.getWeatherAtTime(testCoordinate.getLatitude(), testCoordinate.getLongitude(), timeUtc)
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


    private WeatherResponse getStandardWeatherResponse() {
        WeatherResponse weather = new WeatherResponse();
        weather.setPrecipitation(0.0);
        weather.setTemperature2m(3.9);
        weather.setRelativeHumidity(80.0);
        weather.setWindSpeed10m(8.4);
        weather.setShortWaveRadiation(168.0);
        weather.setDirectRadiation(55.0);
        weather.setDiffuseRadiation(113.0);
        weather.setSurfacePressure(1013.0);
        weather.setDewPoint(0.8);
        weather.setSnowDepth(0.0);
        weather.setLatitude(48.0);
        weather.setLongitude(16.0);
        weather.setTime(timeUtc);

        return weather;
    }
    
    final static int AGE = 20;
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Test
    void goodConditions_calculatingWeatherScore_highScoreAndLowRisk() throws ValidationException {

        WeatherImpactDto result = service.calculateWeatherScore(getStandardWeatherResponse(), AGE);

        assertAll(
                () -> assertTrue(result.getWeatherScore() >= 0.8),
                () -> assertEquals(HeatRiskCategory.LOW_COLD, result.getTemperatureRiskCategory())
        );
    }


    private WeatherResponse getStd() {
        return getStandardWeatherResponse();
    }

    @Test
    void decreasingTemperatureBeyondOptimum_calculatingWeatherScore_decreasingWeatherScore() throws ValidationException {
        WeatherResponse weatherTest1 = getStd();
        WeatherResponse weatherTest2 = getStd();
        WeatherResponse weatherTest3 = getStd();
        WeatherResponse weatherTest4 = getStd();
        WeatherResponse weatherTest5 = getStd();
        WeatherResponse weatherTest6 = getStd();

        weatherTest1.setDewPoint(-70.0);
        weatherTest1.setTemperature2m(-5.0);

        weatherTest2.setDewPoint(-70.0);
        weatherTest2.setTemperature2m(-15.0);

        weatherTest3.setDewPoint(-70.0);
        weatherTest3.setTemperature2m(-30.0);

        weatherTest4.setDewPoint(-70.0);
        weatherTest4.setTemperature2m(-45.0);

        weatherTest5.setDewPoint(-70.0);
        weatherTest5.setTemperature2m(-49.0);

        weatherTest6.setDewPoint(-70.0);
        weatherTest6.setTemperature2m(-60.0);

        WeatherImpactDto result1 = service.calculateWeatherScore(weatherTest1, AGE);
        WeatherImpactDto result2 = service.calculateWeatherScore(weatherTest2, AGE);
        WeatherImpactDto result3 = service.calculateWeatherScore(weatherTest3, AGE);
        WeatherImpactDto result4 = service.calculateWeatherScore(weatherTest4, AGE);
        WeatherImpactDto result5 = service.calculateWeatherScore(weatherTest5, AGE);
        WeatherImpactDto result6 = service.calculateWeatherScore(weatherTest6, AGE);

        assertAll(
                () -> assertTrue(result1.getWeatherScore() > result2.getWeatherScore()),
                () -> assertTrue(result2.getWeatherScore() > result3.getWeatherScore()),
                () -> assertTrue(result3.getWeatherScore() > result4.getWeatherScore()),
                () -> assertTrue(result4.getWeatherScore() >= result5.getWeatherScore()),
                () -> assertTrue(result5.getWeatherScore() >= result6.getWeatherScore()) // weather score values for both are so low, that they are equal
        );
    }

    @Test
    void increasingTemperatureBeyondOptimum_calculatingWeatherScore_decreasingWeatherScore() throws ValidationException {
        WeatherResponse weatherTest1 = getStd();
        WeatherResponse weatherTest2 = getStd();
        WeatherResponse weatherTest3 = getStd();
        WeatherResponse weatherTest4 = getStd();
        WeatherResponse weatherTest5 = getStd();

        weatherTest1.setTemperature2m(15.0);
        weatherTest2.setTemperature2m(20.0);
        weatherTest3.setTemperature2m(23.0);
        weatherTest4.setTemperature2m(26.0);
        weatherTest5.setTemperature2m(29.0);

        WeatherImpactDto result1 = service.calculateWeatherScore(weatherTest1, AGE);
        WeatherImpactDto result2 = service.calculateWeatherScore(weatherTest2, AGE);
        WeatherImpactDto result3 = service.calculateWeatherScore(weatherTest3, AGE);
        WeatherImpactDto result4 = service.calculateWeatherScore(weatherTest4, AGE);
        WeatherImpactDto result5 = service.calculateWeatherScore(weatherTest5, AGE);


        assertAll(
                () -> assertTrue(result1.getWeatherScore() > result2.getWeatherScore()),
                () -> assertTrue(result2.getWeatherScore() > result3.getWeatherScore()),
                () -> assertTrue(result3.getWeatherScore() > result4.getWeatherScore()),
                () -> assertTrue(result4.getWeatherScore() > result5.getWeatherScore())
        );
    }

    //
    // TEMPERATURE 2m
    //
    @Test
    void temperatureNull_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setTemperature2m(null);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("temperature2m is null"));
    }

    @Test
    void temperatureTooLow_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setTemperature2m(-150.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("temperature2m is unrealistically low (< -100°C)"));
    }

    @Test
    void temperatureTooHigh_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setTemperature2m(80.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("temperature2m is unrealistically high (> 70°C)"));
    }


    //
    // WIND SPEED 10m
    //
    @Test
    void windSpeedNull_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setWindSpeed10m(null);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("windSpeed10m is null"));
    }

    @Test
    void windSpeedNegative_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setWindSpeed10m(-1.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("windSpeed10m cannot be negative"));
    }

    @Test
    void windSpeedTooHigh_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setWindSpeed10m(200.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("windSpeed10m is unrealistically high (> 120 m/s)"));
    }


    //
    // PRECIPITATION
    //
    @Test
    void precipitationNull_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setPrecipitation(null);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("precipitation is null"));
    }

    @Test
    void precipitationNegative_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setPrecipitation(-5.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("precipitation cannot be negative"));
    }

    @Test
    void precipitationTooHigh_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setPrecipitation(300.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("precipitation is unrealistically high (> 200 mm/h)"));
    }


    //
    // RELATIVE HUMIDITY
    //
    @Test
    void humidityNull_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setRelativeHumidity(null);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("relativeHumidity is null"));
    }

    @Test
    void humidityNegative_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setRelativeHumidity(-1.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("relativeHumidity must be between 0 and 100%"));
    }

    @Test
    void humidityTooHigh_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setRelativeHumidity(150.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("relativeHumidity must be between 0 and 100%"));
    }


    //
    // SHORTWAVE RADIATION
    //
    @Test
    void shortwaveNull_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setShortWaveRadiation(null);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("shortWaveRadiation is null"));
    }

    @Test
    void shortwaveNegative_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setShortWaveRadiation(-10.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("shortWaveRadiation cannot be negative"));
    }

    @Test
    void shortwaveTooHigh_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setShortWaveRadiation(2000.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("shortWaveRadiation is unrealistically high (> 1500 W/m²)"));
    }


    //
    // DIRECT RADIATION
    //
    @Test
    void directRadiationNull_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setDirectRadiation(null);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("directRadiation is null"));
    }

    @Test
    void directRadiationNegative_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setDirectRadiation(-5.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("directRadiation cannot be negative"));
    }

    @Test
    void directRadiationTooHigh_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setDirectRadiation(3000.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("directRadiation is unrealistically high (> 1500 W/m²)"));
    }


    //
    // DIFFUSE RADIATION
    //
    @Test
    void diffuseRadiationNull_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setDiffuseRadiation(null);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("diffuseRadiation is null"));
    }

    @Test
    void diffuseRadiationNegative_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setDiffuseRadiation(-2.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("diffuseRadiation cannot be negative"));
    }

    @Test
    void diffuseRadiationTooHigh_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setDiffuseRadiation(2000.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("diffuseRadiation is unrealistically high (> 800 W/m²)"));
    }


    //
    // SURFACE PRESSURE
    //
    @Test
    void pressureNull_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setSurfacePressure(null);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("surfacePressure is null"));
    }

    @Test
    void pressureTooLow_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setSurfacePressure(700.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("surfacePressure is unrealistically low (< 800 hPa)"));
    }

    @Test
    void pressureTooHigh_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setSurfacePressure(1200.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("surfacePressure is unrealistically high (> 1100 hPa)"));
    }


    //
    // DEW POINT
    //
    @Test
    void dewPointNull_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setDewPoint(null);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("dewPoint is null"));
    }

    @Test
    void dewPointTooLow_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setDewPoint(-150.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("dewPoint is unrealistically low (< -100°C)"));
    }

    @Test
    void dewPointTooHigh_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setDewPoint(80.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("dewPoint is unrealistically high (> 50°C)"));
    }

    @Test
    void dewPointHigherThanTemp_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setTemperature2m(10.0);
        weatherTest.setDewPoint(15.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("dewPoint cannot be higher than temperature2m"));
    }


    //
    // SNOW DEPTH
    //
    @Test
    void snowDepthNull_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setSnowDepth(null);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("snowDepth is null"));
    }

    @Test
    void snowDepthNegative_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setSnowDepth(-1.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("snowDepth cannot be negative"));
    }

    @Test
    void snowDepthTooHigh_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setSnowDepth(5000.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest, AGE)
        );

        assertTrue(ex.errors().contains("snowDepth is unrealistically high (> 2000 cm)"));
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