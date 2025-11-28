package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.service.impl.WeatherServiceImpl;
import com.smartroute.smartroute1.entity.enums.HeatRiskCategory;
import com.smartroute.smartroute1.endpoint.dto.WeatherImpactDto;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class WeatherServiceTest {
    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private WeatherServiceImpl service;

    /*
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

    @BeforeEach
    void resetData() {
        weatherRepository.deleteAll();
    }
    */


    @Test
    @DisplayName("Neutral WBGT should produce minimal penalty and NEUTRAL heat risk")
    void givenNeutralWeatherWhenEstimatingImpactThenNeutralRiskAndMinimalPenalty() {

        long baseTime = 3600; // 1 hour

        WeatherImpactDto result = service.estimateImpact(
                10000,
                baseTime,
                15,    // temperature
                50,    // humidity
                200,   // solar radiation
                3,      // wind speed
                0,
                20
        );

        assertAll("NEUTRAL+TEN_K_LIKE impact calculations",
                () -> assertEquals(HeatRiskCategory.NEUTRAL, result.getRisk()),
                () -> assertTrue(result.getAdjustedTimeSeconds() >= 3500),
                () -> assertTrue(result.getAdjustedTimeSeconds() <= 3700)
        );
    }

    @Test
    @DisplayName("Hot weather should increase penalty for marathon-like event")
    void givenHotWeatherWhenEstimatingImpactThenExtremeHeatRiskAndIncreasedPenalty() {

        long baseTime = 7200; // 2 hours

        WeatherImpactDto result = service.estimateImpact(
                40000,
                baseTime,
                30,    // hot temperature
                70,    // humid
                800,   // strong sun
                1,      // low wind
                0,
                20
        );

        assertAll("EXTREME_HEAT+MARATHON impact calculations",
                () -> assertEquals(HeatRiskCategory.EXTREME_HEAT, result.getRisk()),
                () -> assertTrue(result.getAdjustedTimeSeconds() > baseTime),
                () -> assertTrue(result.getPenaltyPercent() > 0)
        );
    }

    @Test
    @DisplayName("Cold weather should produce time penalty due to cold slope")
    void givenColdWeatherWhenEstimatingImpactThenColdCoolRiskAndAdjustedTime()  {

        long baseTime = 5000;

        WeatherImpactDto result = service.estimateImpact(
                5000,
                baseTime,
                0,       // freezing temperature
                30,
                0,
                5,
                0,
                20
        );

        assertAll("COLD_COOL+FIVE_K_LIKE impact calculations",
                () -> assertEquals(HeatRiskCategory.COLD_COOL, result.getRisk()),
                () -> assertTrue(result.getAdjustedTimeSeconds() > 0),
                () -> assertNotEquals(baseTime, result.getAdjustedTimeSeconds())
        );
    }

    @Test
    @DisplayName("Extreme heat should classify as EXTREME_HEAT")
    void givenExtremeHeatConditionsWhenEstimatingImpactThenExtremeHeatRisk() {

        WeatherImpactDto result = service.estimateImpact(
                10000,
                3600,
                40,
                90,
                1000,
                0,
                0,
                20
        );

        assertAll("EXTREME_HEAT+TEN_K_LIKE impact calculations",
                () -> assertEquals(HeatRiskCategory.EXTREME_HEAT, result.getRisk()),
                () -> assertTrue(result.getAdjustedTimeSeconds() > 3600)
        );
    }

    @Test
    @DisplayName("High solar radiation + low wind should trigger extra WBGT sun correction")
    void givenHighSolarLowWindWhenEstimatingImpactThenAdditionalSunCorrectionApplied() {

        WeatherImpactDto lowWindHighSun = service.estimateImpact(
                40000,
                3600,
                25,
                60,
                700, // > 600 = strong sun
                1,    // low wind (<2)
                0,
                20
        );

        WeatherImpactDto normal = service.estimateImpact(
                40000,
                3600,
                25,
                60,
                100,
                5,
                0,
                20
        );

        assertAll("High solar test",
                () -> assertTrue(lowWindHighSun.getAdjustedTimeSeconds() > normal.getAdjustedTimeSeconds())
        );
    }

    @Test
    @DisplayName("Precipitation Impact Test")
    void givenDifferentPrecipLevelsWhenEstimatingImpactThenHigherPrecipitationSlowsRunner() {

        WeatherImpactDto noPrecipitation = service.estimateImpact(
                40000,
                3600,
                25,
                60,
                700, // > 600 = strong sun
                1,    // low wind (<2)
                0,
                20
        );

        WeatherImpactDto mildPrecipitation = service.estimateImpact(
                40000,
                3600,
                25,
                60,
                100,
                5,
                15,
                20
        );

        WeatherImpactDto highPrecipitation = service.estimateImpact(
                40000,
                3600,
                25,
                60,
                100,
                5,
                40,
                20
        );

        assertAll("Slower with higher precipitation",
                () -> assertTrue(highPrecipitation.getAdjustedTimeSeconds() > mildPrecipitation.getAdjustedTimeSeconds()),
                () -> assertTrue(mildPrecipitation.getAdjustedTimeSeconds() > noPrecipitation.getAdjustedTimeSeconds())
        );
    }

    @Test
    @DisplayName("Influence of Age on Precipitation Impact Test")
    void givenDifferentAgesWithPrecipitationWhenEstimatingImpactThenOlderRunnersGetHigherPenalty() {

        WeatherImpactDto youngest = service.estimateImpact(
                40000,
                3600,
                25,
                60,
                700, // > 600 = strong sun
                1,    // low wind (<2)
                30,
                20
        );

        WeatherImpactDto secondOldest = service.estimateImpact(
                40000,
                3600,
                25,
                60,
                100,
                5,
                30,
                30
        );

        WeatherImpactDto oldest = service.estimateImpact(
                40000,
                3600,
                25,
                60,
                100,
                5,
                30,
                40
        );

        assertAll("Slower with higher age in precipitation",
                () -> assertTrue(secondOldest.getAdjustedTimeSeconds() > youngest.getAdjustedTimeSeconds()),
                () -> assertTrue(oldest.getAdjustedTimeSeconds() > secondOldest.getAdjustedTimeSeconds())
        );
    }
}