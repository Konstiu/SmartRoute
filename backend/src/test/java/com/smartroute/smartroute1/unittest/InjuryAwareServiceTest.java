package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.entity.Injuries;
import com.smartroute.smartroute1.entity.enums.BodyPart;
import com.smartroute.smartroute1.service.InjuryAwareTrainingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;


@SpringBootTest()
@ActiveProfiles({"test", "generateData"})
class InjuryAwareServiceTest extends BaseTest {
    @Autowired
    private InjuryAwareTrainingService injuryAwareTrainingService;
    private static final double epsilon = 1e-6;

    @Test
    void intensityScaling_matchesSpecExamples() {
        assertAll(
                () -> assertEquals(1.0, injuryAwareTrainingService.calculateIntensityScaling(0.0), epsilon),
                () -> assertEquals(0.2865047968601901, injuryAwareTrainingService.calculateIntensityScaling(0.5), epsilon),
                () -> assertEquals(0.0820849986238988, injuryAwareTrainingService.calculateIntensityScaling(1.0), epsilon)
        );
    }

    @Test
    void volumeScaling_matchesSpecExamples() {
        assertAll(
                () -> assertEquals(1.0, injuryAwareTrainingService.calculateVolumeScaling(0.0), epsilon),
                () -> assertEquals(0.7, injuryAwareTrainingService.calculateVolumeScaling(0.5), epsilon),
                () -> assertEquals(0.4, injuryAwareTrainingService.calculateVolumeScaling(1.0), epsilon)
        );
    }

    @Test
    void highImpactPenalty_behavesAsLogistic() {

        assertAll(
                () -> assertEquals(0.9820137900379085, injuryAwareTrainingService.calculateHighImpactPenalty(0.0), epsilon),
                () -> assertEquals(0.5, injuryAwareTrainingService.calculateHighImpactPenalty(0.4), epsilon),
                () -> assertEquals(0.1192029220221176, injuryAwareTrainingService.calculateHighImpactPenalty(0.6), epsilon),
                () -> assertEquals(0.0024726231566347743, injuryAwareTrainingService.calculateHighImpactPenalty(1.0), epsilon)
        );
    }

    @Test
    void constraintMap_singleKneeInjury_I05_matchesSpecExample() {
        Injuries kneeInjury = new Injuries();
        kneeInjury.setInjuryIndex(0.5);
        kneeInjury.setAffectedArea(BodyPart.KNEE_REGION);

        Map<BodyPart, Double> map = injuryAwareTrainingService.calculateInjuriesMap(List.of(kneeInjury));

        assertAll(
                () -> assertEquals(0.0820849986238988, map.get(BodyPart.KNEE_REGION), epsilon),
                () -> assertEquals(0.95, map.get(BodyPart.NECK_REGION), epsilon),
                () -> assertEquals(0.95, map.get(BodyPart.UPPER_REGION), epsilon),
                () -> assertEquals(0.95, map.get(BodyPart.CORE_REGION), epsilon),
                () -> assertEquals(0.95, map.get(BodyPart.FEET_REGION), epsilon)
        );
    }

    @Test
    void constraintMap_shoulderLikeInjury_I04_matchesSpecExample() {
        Injuries upperInjury = new Injuries();
        upperInjury.setInjuryIndex(0.4);
        upperInjury.setAffectedArea(BodyPart.UPPER_REGION);

        Map<BodyPart, Double> map = injuryAwareTrainingService.calculateInjuriesMap(List.of(upperInjury));

        assertAll(
                () -> assertEquals(0.1353352832366127, map.get(BodyPart.UPPER_REGION), epsilon),
                () -> assertEquals(0.96, map.get(BodyPart.NECK_REGION), epsilon),
                () -> assertEquals(0.96, map.get(BodyPart.CORE_REGION), epsilon),
                () -> assertEquals(0.96, map.get(BodyPart.KNEE_REGION), epsilon),
                () -> assertEquals(0.96, map.get(BodyPart.FEET_REGION), epsilon)
        );
    }

    @Test
    void constraintMap_shoulderLikeInjury_I02_matchesSpecExample() {
        Injuries upperInjury = new Injuries();
        upperInjury.setInjuryIndex(0.1);
        upperInjury.setAffectedArea(BodyPart.UPPER_REGION);

        Map<BodyPart, Double> map = injuryAwareTrainingService.calculateInjuriesMap(List.of(upperInjury));

        assertAll(
                () -> assertEquals(0.6065306597126334, map.get(BodyPart.UPPER_REGION), epsilon),
                () -> assertEquals(0.99, map.get(BodyPart.NECK_REGION), epsilon),
                () -> assertEquals(0.99, map.get(BodyPart.CORE_REGION), epsilon),
                () -> assertEquals(0.99, map.get(BodyPart.KNEE_REGION), epsilon),
                () -> assertEquals(0.99, map.get(BodyPart.FEET_REGION), epsilon)
        );
    }

    @Test
    void fullStopInjury_setsAllRegionsToZero() {
        Injuries fracture = new Injuries();
        fracture.setInjuryIndex(0.3);
        fracture.setAffectedArea(BodyPart.BONE_FRACTURE);
        fracture.setLastHealthyDate(LocalDate.now().minusDays(1));

        Map<BodyPart, Double> map = injuryAwareTrainingService.calculateInjuriesMap(List.of(fracture));

        for (BodyPart part : BodyPart.values()) {
            assertEquals(0.0, map.get(part));
        }
    }
}
