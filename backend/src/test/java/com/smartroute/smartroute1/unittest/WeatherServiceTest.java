package com.smartroute.smartroute1.unittest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartroute.smartroute1.endpoint.dto.WeatherDto;
import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.WeatherRepository;
import com.smartroute.smartroute1.service.WeatherService;
import com.smartroute.smartroute1.util.Coordinate;
import jakarta.transaction.Transactional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    @Autowired
    private ActivityRepository activityRepository;

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
        activityRepository.deleteAll();
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

    @Test
    void goodConditions_calculatingWeatherScore_highScoreAndLowRisk() throws ValidationException {

        double result = service.calculateWeatherScore(getStandardWeatherResponse());

        assertAll(
                () -> assertTrue(result >= 0.8)
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

        double result1 = service.calculateWeatherScore(weatherTest1);
        double result2 = service.calculateWeatherScore(weatherTest2);
        double result3 = service.calculateWeatherScore(weatherTest3);
        double result4 = service.calculateWeatherScore(weatherTest4);
        double result5 = service.calculateWeatherScore(weatherTest5);
        double result6 = service.calculateWeatherScore(weatherTest6);

        assertAll(
                () -> assertTrue(result1 > result2),
                () -> assertTrue(result2 > result3),
                () -> assertTrue(result3 > result4),
                () -> assertTrue(result4 >= result5),
                () -> assertTrue(result5 >= result6) // weather score values for both are so low, that they are equal
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

        double result1 = service.calculateWeatherScore(weatherTest1);
        double result2 = service.calculateWeatherScore(weatherTest2);
        double result3 = service.calculateWeatherScore(weatherTest3);
        double result4 = service.calculateWeatherScore(weatherTest4);
        double result5 = service.calculateWeatherScore(weatherTest5);


        assertAll(
                () -> assertTrue(result1 > result2),
                () -> assertTrue(result2 > result3),
                () -> assertTrue(result3 > result4),
                () -> assertTrue(result4 > result5)
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
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("temperature2m is null"));
    }

    @Test
    void temperatureTooLow_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setTemperature2m(-150.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("temperature2m is unrealistically low (< -100°C)"));
    }

    @Test
    void temperatureTooHigh_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setTemperature2m(80.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest)
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
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("windSpeed10m is null"));
    }

    @Test
    void windSpeedNegative_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setWindSpeed10m(-1.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("windSpeed10m cannot be negative"));
    }

    @Test
    void windSpeedTooHigh_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setWindSpeed10m(200.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest)
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
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("precipitation is null"));
    }

    @Test
    void precipitationNegative_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setPrecipitation(-5.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("precipitation cannot be negative"));
    }

    @Test
    void precipitationTooHigh_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setPrecipitation(300.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest)
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
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("relativeHumidity is null"));
    }

    @Test
    void humidityNegative_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setRelativeHumidity(-1.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("relativeHumidity must be between 0 and 100%"));
    }

    @Test
    void humidityTooHigh_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setRelativeHumidity(150.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest)
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
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("shortWaveRadiation is null"));
    }

    @Test
    void shortwaveNegative_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setShortWaveRadiation(-10.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("shortWaveRadiation cannot be negative"));
    }

    @Test
    void shortwaveTooHigh_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setShortWaveRadiation(2000.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest)
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
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("directRadiation is null"));
    }

    @Test
    void directRadiationNegative_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setDirectRadiation(-5.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("directRadiation cannot be negative"));
    }

    @Test
    void directRadiationTooHigh_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setDirectRadiation(3000.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest)
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
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("diffuseRadiation is null"));
    }

    @Test
    void diffuseRadiationNegative_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setDiffuseRadiation(-2.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("diffuseRadiation cannot be negative"));
    }

    @Test
    void diffuseRadiationTooHigh_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setDiffuseRadiation(2000.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest)
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
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("surfacePressure is null"));
    }

    @Test
    void pressureTooLow_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setSurfacePressure(700.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("surfacePressure is unrealistically low (< 800 hPa)"));
    }

    @Test
    void pressureTooHigh_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setSurfacePressure(1200.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest)
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
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("dewPoint is null"));
    }

    @Test
    void dewPointTooLow_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setDewPoint(-150.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("dewPoint is unrealistically low (< -100°C)"));
    }

    @Test
    void dewPointTooHigh_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setDewPoint(80.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest)
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
                () -> service.calculateWeatherScore(weatherTest)
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
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("snowDepth is null"));
    }

    @Test
    void snowDepthNegative_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setSnowDepth(-1.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("snowDepth cannot be negative"));
    }

    @Test
    void snowDepthTooHigh_calculatingWeatherScore_throwsValidationException() {
        WeatherResponse weatherTest = getStd();
        weatherTest.setSnowDepth(5000.0);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.calculateWeatherScore(weatherTest)
        );

        assertTrue(ex.errors().contains("snowDepth is unrealistically high (> 2000 cm)"));
    }

    @Test
    void differentWeatherScores_evaluateWeatherScore_correctAnswerString() {
        assertAll(
                () -> assertEquals("Invalid weather score", service.evaluateWeatherScore(-10)),
                () -> assertEquals("Invalid weather score", service.evaluateWeatherScore(10)),
                () -> assertEquals("Extremely unfavorable conditions", service.evaluateWeatherScore(0.1)),
                () -> assertEquals("Very challenging conditions", service.evaluateWeatherScore(0.2)),
                () -> assertEquals("Unfavorable weather", service.evaluateWeatherScore(0.3)),
                () -> assertEquals("Challenging conditions", service.evaluateWeatherScore(0.4)),
                () -> assertEquals("Some impairments present", service.evaluateWeatherScore(0.5)),
                () -> assertEquals("Acceptable conditions", service.evaluateWeatherScore(0.6)),
                () -> assertEquals("Good running conditions", service.evaluateWeatherScore(0.7)),
                () -> assertEquals("Very favorable conditions", service.evaluateWeatherScore(0.8)),
                () -> assertEquals("Excellent weather", service.evaluateWeatherScore(0.9)),
                () -> assertEquals("Near-perfect conditions", service.evaluateWeatherScore(1.))
        );
    }

    @Test
    void differentWindConditions_buildWeatherDescription_correctAnswerString() {
        WeatherResponse windCalm = getStd();
        windCalm.setWindSpeed10m(0.0);
        WeatherResponse windGentle = getStd();
        windGentle.setWindSpeed10m(1.0);
        WeatherResponse windModerate = getStd();
        windModerate.setWindSpeed10m(25.0);
        WeatherResponse windStrong = getStd();
        windStrong.setWindSpeed10m(40.0);
        WeatherResponse windGale = getStd();
        windGale.setWindSpeed10m(55.0);

        String calm = "Barely any wind, expect no difficulties.";
        String gentle = "Light breeze that may slightly affect your pacing.";
        String moderate = "Noticeable wind, expect some resistance.";
        String strong = "These strong winds will cause a significant impact on your run.";
        String gale = "Dangerous wind conditions, seek shelter and avoid the outside.";

        assertAll(
                () -> assertEquals(calm, service.buildWeatherDescription(windCalm).getWindText()),
                () -> assertEquals(gentle, service.buildWeatherDescription(windGentle).getWindText()),
                () -> assertEquals(moderate, service.buildWeatherDescription(windModerate).getWindText()),
                () -> assertEquals(strong, service.buildWeatherDescription(windStrong).getWindText()),
                () -> assertEquals(gale, service.buildWeatherDescription(windGale).getWindText())
        );
    }

    @Test
    void differentPrecipitationConditions_buildWeatherDescription_correctAnswerString() {
        WeatherResponse preNone = getStd();
        preNone.setPrecipitation(0.0);
        WeatherResponse preTrace = getStd();
        preTrace.setPrecipitation(0.2);
        WeatherResponse preVeryLight = getStd();
        preVeryLight.setPrecipitation(0.8);
        WeatherResponse preLight = getStd();
        preLight.setPrecipitation(1.5);
        WeatherResponse preModerate = getStd();
        preModerate.setPrecipitation(5.0);
        WeatherResponse preHeavy = getStd();
        preHeavy.setPrecipitation(20.0);
        WeatherResponse preViolent = getStd();
        preViolent.setPrecipitation(60.0);

        String none = "Dry conditions with optimal traction.";
        String trace = "Light drizzle, slightly slick surfaces possible.";
        String veryLight = "Very light precipitation causes a mild cooling effect and reduced traction.";
        String light = "Light precipitation causes a moderate cooling effect and reduced traction.";
        String moderate = "In this moderate precipitation expect wet clothing and a noticeable impact on your pace.";
        String heavy = "In this heavy precipitation you will be completely drenched. Expect reduced visibility and significant traction loss.";
        String violent = "Very violent precipitation, consider staying at home.";

        assertAll(
                () -> assertEquals(none, service.buildWeatherDescription(preNone).getPrecipitationText()),
                () -> assertEquals(trace, service.buildWeatherDescription(preTrace).getPrecipitationText()),
                () -> assertEquals(veryLight, service.buildWeatherDescription(preVeryLight).getPrecipitationText()),
                () -> assertEquals(light, service.buildWeatherDescription(preLight).getPrecipitationText()),
                () -> assertEquals(moderate, service.buildWeatherDescription(preModerate).getPrecipitationText()),
                () -> assertEquals(heavy, service.buildWeatherDescription(preHeavy).getPrecipitationText()),
                () -> assertEquals(violent, service.buildWeatherDescription(preViolent).getPrecipitationText())
        );
    }

    @Test
    void differentTemperatures_buildWeatherDescription_correctAnswerString() {
        WeatherResponse tempExtremeCold = getStd();
        tempExtremeCold.setTemperature2m(-60.0);
        tempExtremeCold.setDewPoint(-61.0);
        WeatherResponse tempSevereCold = getStd();
        tempSevereCold.setTemperature2m(-43.0);
        tempSevereCold.setDewPoint(-44.0);
        WeatherResponse tempVeryHighCold = getStd();
        tempVeryHighCold.setTemperature2m(-35.0);
        tempVeryHighCold.setDewPoint(-41.0);
        WeatherResponse tempHighCold = getStd();
        tempHighCold.setTemperature2m(-30.0);
        tempHighCold.setDewPoint(-31.0);
        WeatherResponse tempModerateCold = getStd();
        tempModerateCold.setTemperature2m(-15.0);
        tempModerateCold.setDewPoint(-16.0);
        WeatherResponse tempLowCold = getStd();
        tempLowCold.setTemperature2m(-5.0);
        tempLowCold.setDewPoint(-6.0);
        WeatherResponse tempNeutralCold = getStd();
        tempNeutralCold.setTemperature2m(4.0);
        tempNeutralCold.setDewPoint(-1.0);
        WeatherResponse tempOptimal = getStd();
        tempOptimal.setTemperature2m(15.0);
        WeatherResponse tempLowHeat = getStd();
        tempLowHeat.setTemperature2m(20.0);
        WeatherResponse tempModerateHeat = getStd();
        tempModerateHeat.setTemperature2m(24.0);
        WeatherResponse tempHighHeat = getStd();
        tempHighHeat.setTemperature2m(29.0);
        WeatherResponse tempExtremeHeat = getStd();
        tempExtremeHeat.setTemperature2m(35.0);

        String extremeCold = "Extreme cold. DANGER! Outdoor conditions are hazardous. Stay indoors.";
        String severeCold = """
                Severe cold. \
                
                Severe risk of hypothermia if outside for long periods without adequate clothing or shelter from wind and cold.\
                
                Severe risk of frostbite: Check face and extremities frequently for numbness or whiteness.\
                
                Cover all exposed skin in layers of warm clothing, keep active and stay dry. Be prepared to cut short or cancel your run.""";
        String veryHighCold = """
                Very cold conditions. \
                
                Very high risk of frostbite: Check face and extremities for numbness or whiteness.\
                
                Very high risk of hypothermia if outside for long periods without adequate clothing or shelter from wind and cold.\
                
                Cover all exposed skin in layers of warm clothing, keep active and stay dry. Be prepared to cut short or cancel your run.""";
        String highCold = """
                Beyond uncomfortable cold conditions.
                
                High risk of frostnip or frostbite: Check face and extremities for numbness or whiteness.\
                
                High risk of hypothermia if outside for long periods without adequate clothing or shelter from wind and cold.\
                
                Cover all exposed skin in layers of warm clothing, keep active and stay dry. Be prepared to cut short or cancel your run.""";
        String moderateCold = """
                Uncomfortably cold conditions.\
                
                Risk of hypothermia and frostbite if outside for long periods without adequate protection.\
                
                Dress in layers of warm clothing, keep active and stay dry.""";
        String lowCold = """
                Very cool conditions.\
                
                Slight increase in discomfort.\
                
                Dress warmly and stay dry.""";
        String neutralCold = "Cool conditions, generally favorable for running.";
        String optimal = "Optimal temperature for running.";
        String lowHeat = """
                Warm conditions.\
                
                Heat stress and other heat illnesses are possible.\
                
                If you are a high risk individual, monitor yourself.""";
        String moderateHeat = """
                Hot conditions.\
                
                Risk of heat illnesses for everyboy are increased.""";
        String highHeat = """
                Very hot conditions.\
                
                If you are unfit or not acclimatized, running becomes dangerous.""";
        String extremeHeat = """
                Extremely hot conditions.\
                
                Cancel your run.""";


        assertAll(
                () -> assertEquals(extremeCold, service.buildWeatherDescription(tempExtremeCold).getTemperatureText()),
                () -> assertEquals(severeCold, service.buildWeatherDescription(tempSevereCold).getTemperatureText()),
                () -> assertEquals(veryHighCold, service.buildWeatherDescription(tempVeryHighCold).getTemperatureText()),
                () -> assertEquals(highCold, service.buildWeatherDescription(tempHighCold).getTemperatureText()),
                () -> assertEquals(moderateCold, service.buildWeatherDescription(tempModerateCold).getTemperatureText()),
                () -> assertEquals(lowCold, service.buildWeatherDescription(tempLowCold).getTemperatureText()),
                () -> assertEquals(neutralCold, service.buildWeatherDescription(tempNeutralCold).getTemperatureText()),
                () -> assertEquals(optimal, service.buildWeatherDescription(tempOptimal).getTemperatureText()),
                () -> assertEquals(lowHeat, service.buildWeatherDescription(tempLowHeat).getTemperatureText()),
                () -> assertEquals(moderateHeat, service.buildWeatherDescription(tempModerateHeat).getTemperatureText()),
                () -> assertEquals(highHeat, service.buildWeatherDescription(tempHighHeat).getTemperatureText()),
                () -> assertEquals(extremeHeat, service.buildWeatherDescription(tempExtremeHeat).getTemperatureText())
        );
    }

}