package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.service.impl.WeatherServiceImpl;
import com.smartroute.smartroute1.entity.weather.EventType;
import com.smartroute.smartroute1.entity.weather.HeatRiskCategory;
import com.smartroute.smartroute1.entity.weather.WeatherImpactResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

    @Test
    @DisplayName("Neutral WBGT should produce minimal penalty and NEUTRAL heat risk")
    void testEstimateImpactNeutral() {

        long baseTime = 3600; // 1 hour

        WeatherImpactResult result = service.estimateImpact(
                EventType.TEN_K_LIKE,
                baseTime,
                15,    // temperature
                50,    // humidity
                200,   // solar radiation
                3,      // wind speed
                0,
                20
        );

        assertAll("NEUTRAL+TEN_K_LIKE impact calculations",
                () -> assertEquals(HeatRiskCategory.NEUTRAL, result.risk()),
                () -> assertTrue(result.adjustedTimeSeconds() >= 3500),
                () -> assertTrue(result.adjustedTimeSeconds() <= 3700)
        );
    }

    @Test
    @DisplayName("Hot weather should increase penalty for marathon-like event")
    void testEstimateImpactHeat() {

        long baseTime = 7200; // 2 hours

        WeatherImpactResult result = service.estimateImpact(
                EventType.MARATHON_LIKE,
                baseTime,
                30,    // hot temperature
                70,    // humid
                800,   // strong sun
                1,      // low wind
                0,
                20
        );

        assertAll("EXTREME_HEAT+MARATHON impact calculations",
                () -> assertEquals(HeatRiskCategory.EXTREME_HEAT, result.risk()),
                () -> assertTrue(result.adjustedTimeSeconds() > baseTime),
                () -> assertTrue(result.penaltyPercent() > 0)
        );
    }

    @Test
    @DisplayName("Cold weather should produce time penalty due to cold slope")
    void testEstimateImpactCold() {

        long baseTime = 5000;

        WeatherImpactResult result = service.estimateImpact(
                EventType.FIVE_K_LIKE,
                baseTime,
                0,       // freezing temperature
                30,
                0,
                5,
                0,
                20
        );

        assertAll("COLD_COOL+FIVE_K_LIKE impact calculations",
                () -> assertEquals(HeatRiskCategory.COLD_COOL, result.risk()),
                () -> assertTrue(result.adjustedTimeSeconds() > 0),
                () -> assertNotEquals(baseTime, result.adjustedTimeSeconds())
        );
    }

    @Test
    @DisplayName("Extreme heat should classify as EXTREME_HEAT")
    void testExtremeHeatClassification() {

        WeatherImpactResult result = service.estimateImpact(
                EventType.TEN_K_LIKE,
                3600,
                40,
                90,
                1000,
                0,
                0,
                20
        );

        assertAll("EXTREME_HEAT+TEN_K_LIKE impact calculations",
                () -> assertEquals(HeatRiskCategory.EXTREME_HEAT, result.risk()),
                () -> assertTrue(result.adjustedTimeSeconds() > 3600)
        );
    }

    @Test
    @DisplayName("High solar radiation + low wind should trigger extra WBGT sun correction")
    void testWbgtSunCorrection() {

        WeatherImpactResult lowWindHighSun = service.estimateImpact(
                EventType.MARATHON_LIKE,
                3600,
                25,
                60,
                700, // > 600 = strong sun
                1,    // low wind (<2)
                0,
                20
        );

        WeatherImpactResult normal = service.estimateImpact(
                EventType.MARATHON_LIKE,
                3600,
                25,
                60,
                100,
                5,
                0,
                20
        );

        assertAll("High solar test",
                () -> assertTrue(lowWindHighSun.adjustedTimeSeconds() > normal.adjustedTimeSeconds())
        );
    }

    @Test
    @DisplayName("Precipitation Impact Test")
    void testPrecipitationImpact() {

        WeatherImpactResult noPrecipitation = service.estimateImpact(
                EventType.MARATHON_LIKE,
                3600,
                25,
                60,
                700, // > 600 = strong sun
                1,    // low wind (<2)
                0,
                20
        );

        WeatherImpactResult mildPrecipitation = service.estimateImpact(
                EventType.MARATHON_LIKE,
                3600,
                25,
                60,
                100,
                5,
                15,
                20
        );

        WeatherImpactResult highPrecipitation = service.estimateImpact(
                EventType.MARATHON_LIKE,
                3600,
                25,
                60,
                100,
                5,
                40,
                20
        );

        assertAll("Slower with higher precipitation",
                () -> assertTrue(highPrecipitation.adjustedTimeSeconds() > mildPrecipitation.adjustedTimeSeconds()),
                () -> assertTrue(mildPrecipitation.adjustedTimeSeconds() > noPrecipitation.adjustedTimeSeconds())
        );
    }

    @Test
    @DisplayName("Influence of Age on Precipitation Impact Test")
    void testAgePrecipitationImpact() {

        WeatherImpactResult youngest = service.estimateImpact(
                EventType.MARATHON_LIKE,
                3600,
                25,
                60,
                700, // > 600 = strong sun
                1,    // low wind (<2)
                30,
                20
        );

        WeatherImpactResult secondOldest = service.estimateImpact(
                EventType.MARATHON_LIKE,
                3600,
                25,
                60,
                100,
                5,
                30,
                30
        );

        WeatherImpactResult oldest = service.estimateImpact(
                EventType.MARATHON_LIKE,
                3600,
                25,
                60,
                100,
                5,
                30,
                40
        );

        assertAll("Slower with higher age in precipitation",
                () -> assertTrue(secondOldest.adjustedTimeSeconds() > youngest.adjustedTimeSeconds()),
                () -> assertTrue(oldest.adjustedTimeSeconds() > secondOldest.adjustedTimeSeconds())
        );
    }
}