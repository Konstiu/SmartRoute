package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.endpoint.dto.CreateInjuryStateDto;
import com.smartroute.smartroute1.endpoint.dto.CreateUserDto;
import com.smartroute.smartroute1.endpoint.dto.UpdateInjuryDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Injuries;
import com.smartroute.smartroute1.entity.enums.BodyPart;
import com.smartroute.smartroute1.repository.InjuryRepository;
import com.smartroute.smartroute1.service.InjuryAwareTrainingService;
import com.smartroute.smartroute1.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.*;

import static com.smartroute.smartroute1.basetest.TestData.ORIGIN;
import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest()
@ActiveProfiles({"test", "generateData"})
class InjuryAwareServiceTest extends BaseTest {
    @Autowired
    private InjuryAwareTrainingService injuryAwareTrainingService;
    private static final double epsilon = 1e-6;

    @Autowired
    private UserService userService;

    @Autowired
    private InjuryRepository injuryRepository;

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

    @Test
    void calculateInjuriesMap_withNullInjuries_returnsAllOnes() {
        Map<BodyPart, Double> map = injuryAwareTrainingService.calculateInjuriesMap(null);

        for (BodyPart part : BodyPart.values()) {
            assertEquals(1.0, map.get(part), "All body parts should be unrestricted with null injuries");
        }
    }

    @Test
    void calculateInjuriesMap_withEmptyList_returnsAllOnes() {
        Map<BodyPart, Double> map = injuryAwareTrainingService.calculateInjuriesMap(new ArrayList<>());

        for (BodyPart part : BodyPart.values()) {
            assertEquals(1.0, map.get(part), "All body parts should be unrestricted with empty injury list");
        }
    }

    @Test
    void calculateInjuriesMap_withNullLastInjuryDate_treatsAsDaysAgoZero() {
        Injuries injury = new Injuries();
        injury.setInjuryIndex(0.5);
        injury.setAffectedArea(BodyPart.KNEE_REGION);
        injury.setLastInjuryDate(null); // null date

        Map<BodyPart, Double> map = injuryAwareTrainingService.calculateInjuriesMap(List.of(injury));

        assertTrue(map.get(BodyPart.KNEE_REGION) < 1.0, "Injury with null date should still affect the region");
    }

    @Test
    void calculateInjuriesMap_withOldInjury_shouldBeIgnored() {
        Injuries oldInjury = new Injuries();
        oldInjury.setInjuryIndex(0.8);
        oldInjury.setAffectedArea(BodyPart.KNEE_REGION);
        oldInjury.setLastInjuryDate(LocalDate.now().minusDays(20)); // Beyond 14-day window

        Map<BodyPart, Double> map = injuryAwareTrainingService.calculateInjuriesMap(List.of(oldInjury));

        assertEquals(1.0, map.get(BodyPart.KNEE_REGION), epsilon,
                "Injury older than 14 days should not affect constraint");
    }

    @Test
    void hasFullStopInjury_withSpinalInjury_returnsTrue() {
        Injuries spinalInjury = new Injuries();
        spinalInjury.setAffectedArea(BodyPart.SPINAL_INJURY);
        spinalInjury.setLastInjuryDate(LocalDate.now().minusDays(5));

        Map<BodyPart, Double> map = injuryAwareTrainingService.calculateInjuriesMap(List.of(spinalInjury));

        for (BodyPart part : BodyPart.values()) {
            assertEquals(0.0, map.get(part), "Spinal injury should stop all training");
        }
    }

    @Test
    void hasFullStopInjury_withRespirationInjury_returnsTrue() {
        Injuries respirationInjury = new Injuries();
        respirationInjury.setAffectedArea(BodyPart.RESPIRATION_REGION);
        respirationInjury.setLastInjuryDate(LocalDate.now().minusDays(3));

        Map<BodyPart, Double> map = injuryAwareTrainingService.calculateInjuriesMap(List.of(respirationInjury));

        for (BodyPart part : BodyPart.values()) {
            assertEquals(0.0, map.get(part), "Respiration injury should stop all training");
        }
    }


    @Test
    void hasFullStopInjury_withRespirationInjury_WithoutLastInjuryDate_returnsTrue() {
        Injuries respirationInjury = new Injuries();
        respirationInjury.setAffectedArea(BodyPart.RESPIRATION_REGION);

        Map<BodyPart, Double> map = injuryAwareTrainingService.calculateInjuriesMap(List.of(respirationInjury));

        for (BodyPart part : BodyPart.values()) {
            assertEquals(0.0, map.get(part), "Respiration injury should stop all training");
        }
    }


    @Test
    void hasFullStopInjury_withNullDateAndBoneFracture_returnsTrue() {
        Injuries fracture = new Injuries();
        fracture.setAffectedArea(BodyPart.BONE_FRACTURE);
        fracture.setLastInjuryDate(null); // null date

        Map<BodyPart, Double> map = injuryAwareTrainingService.calculateInjuriesMap(List.of(fracture));

        for (BodyPart part : BodyPart.values()) {
            assertEquals(0.0, map.get(part), "Bone fracture with null date should stop all training");
        }
    }

    @Test
    void hasFullStopInjury_withNullDateAndNonCriticalInjury_continuesNormally() {
        Injuries kneeInjury = new Injuries();
        kneeInjury.setAffectedArea(BodyPart.KNEE_REGION);
        kneeInjury.setLastInjuryDate(null);
        kneeInjury.setInjuryIndex(0.5);

        Map<BodyPart, Double> map = injuryAwareTrainingService.calculateInjuriesMap(List.of(kneeInjury));

        assertTrue(map.get(BodyPart.KNEE_REGION) < 1.0, "Non-critical injury should still affect region");
        assertTrue(map.get(BodyPart.UPPER_REGION) < 1.0, "Other regions should be slightly affected");
    }

    @Test
    void hasFullStopInjury_withOldBoneFracture_returnsFalse() {
        Injuries oldFracture = new Injuries();
        oldFracture.setAffectedArea(BodyPart.BONE_FRACTURE);
        oldFracture.setLastInjuryDate(LocalDate.now().minusDays(20)); // Beyond window

        Map<BodyPart, Double> map = injuryAwareTrainingService.calculateInjuriesMap(List.of(oldFracture));

        for (BodyPart part : BodyPart.values()) {
            assertEquals(1.0, map.get(part), "Old bone fracture should not affect training");
        }
    }

    @Test
    void calculateIntensityScaling_withNegativeValue_clampsToZero() {
        double result = injuryAwareTrainingService.calculateIntensityScaling(-0.5);
        assertEquals(1.0, result, epsilon, "Negative injury index should be clamped to 0");
    }

    @Test
    void calculateIntensityScaling_withValueAboveOne_clampsToOne() {
        double result = injuryAwareTrainingService.calculateIntensityScaling(1.5);
        assertEquals(0.0820849986238988, result, epsilon, "Injury index > 1 should be clamped to 1");
    }

    @Test
    void calculateVolumeScaling_withNegativeValue_clampsToZero() {
        double result = injuryAwareTrainingService.calculateVolumeScaling(-0.5);
        assertEquals(1.0, result, epsilon, "Negative injury index should be clamped to 0");
    }

    @Test
    void calculateVolumeScaling_withValueAboveOne_clampsToOne() {
        double result = injuryAwareTrainingService.calculateVolumeScaling(1.5);
        assertEquals(0.4, result, epsilon, "Injury index > 1 should be clamped to 1");
    }

    @Test
    void calculateHighImpactPenalty_withNegativeValue_clampsToZero() {
        double result = injuryAwareTrainingService.calculateHighImpactPenalty(-0.5);
        assertTrue(result > 0.98, "Negative injury index should be clamped to 0");
    }

    @Test
    void calculateHighImpactPenalty_withValueAboveOne_clampsToOne() {
        double result = injuryAwareTrainingService.calculateHighImpactPenalty(1.5);
        assertTrue(result < 0.01, "Injury index > 1 should be clamped to 1");
    }

    @Test
    void calculateInjuriesMap_multipleInjuries_takesMinConstraint() {
        Injuries injury1 = new Injuries();
        injury1.setInjuryIndex(0.3);
        injury1.setAffectedArea(BodyPart.KNEE_REGION);
        injury1.setLastInjuryDate(LocalDate.now().minusDays(2));

        Injuries injury2 = new Injuries();
        injury2.setInjuryIndex(0.6);
        injury2.setAffectedArea(BodyPart.KNEE_REGION);
        injury2.setLastInjuryDate(LocalDate.now().minusDays(1));

        Map<BodyPart, Double> map = injuryAwareTrainingService.calculateInjuriesMap(List.of(injury1, injury2));

        assertTrue(map.get(BodyPart.KNEE_REGION) < 0.3,
                "Multiple injuries to same region should use minimum constraint");
    }

    @Test
    void createInjuries_withValidUser_createsInjuryAndReturnExactlyOneInjury() throws Exception {
        CreateInjuryStateDto dto = new CreateInjuryStateDto();
        dto.setInjuryIndex(0.5);
        dto.setAffectedArea(BodyPart.KNEE_REGION);
        dto.setLastHealthyDate(LocalDate.now().minusDays(10));

        String email = "test@example.com";
        createAndVerifyUser(email, "password");

        Injuries result = injuryAwareTrainingService.createInjuries(dto, email);
        List<Injuries> injuries = injuryAwareTrainingService.findInjuriesByEmail(email);

        assertAll(
                () -> assertEquals(0.5, result.getInjuryIndex()),
                () -> assertEquals(BodyPart.KNEE_REGION, result.getAffectedArea()),
                () -> assertEquals(1, injuries.size())
        );

    }

    @Test
    void createInjuries_withInvalidUser_returnsNull() {
        CreateInjuryStateDto dto = new CreateInjuryStateDto();
        dto.setInjuryIndex(0.5);
        dto.setAffectedArea(BodyPart.KNEE_REGION);

        Injuries result = injuryAwareTrainingService.createInjuries(dto, "nonexistent@example.com");

        assertNull(result, "Creating injury for non-existent user should return null");
    }

    @Test
    void updateInjuries_withInvalidInjury_returnsNull() {
        UpdateInjuryDto dto = new UpdateInjuryDto();
        dto.setInjuryId(999999L); // Non-existent ID
        dto.setInjuryIndex(0.3);

        Injuries result = injuryAwareTrainingService.updateInjuries(dto, "test@example.com");

        assertNull(result, "Updating non-existent injury should return null");
    }

    @Test
    void calculateInjuriesMap_withInjuryIndexOutOfBounds_clampsCorrectly() {
        Injuries injury = new Injuries();
        injury.setInjuryIndex(2.0); // Above 1.0
        injury.setAffectedArea(BodyPart.KNEE_REGION);
        injury.setLastInjuryDate(LocalDate.now());

        Map<BodyPart, Double> map = injuryAwareTrainingService.calculateInjuriesMap(List.of(injury));

        assertTrue(map.get(BodyPart.KNEE_REGION) >= 0.0 && map.get(BodyPart.KNEE_REGION) <= 1.0,
                "Constraint should be clamped between 0 and 1");
    }

    @Test
    void updateInjuryWithValidInjury_returnsCorrectly() {
        ApplicationUser user = userRepository.findAll().getFirst();
        Injuries toUpdate = injuryRepository.getAllByApplicationUser(user).getFirst();

        double newIdx;
        double oldIdx = toUpdate.getInjuryIndex();
        if (oldIdx > 0.9 || oldIdx < 0.1) {
            newIdx = 0.5;
        } else {
            newIdx = oldIdx + 0.1;
        }
        toUpdate.setInjuryIndex(newIdx);
        UpdateInjuryDto dto = new UpdateInjuryDto();
        dto.setInjuryIndex(toUpdate.getInjuryIndex());
        dto.setAffectedArea(toUpdate.getAffectedArea());
        dto.setLastInjuryDate(toUpdate.getLastInjuryDate());
        dto.setLastHealthyDate(toUpdate.getLastHealthyDate());
        dto.setInjuryId(toUpdate.getId());
        dto.setInjuryIndex(newIdx);

        Injuries in = injuryAwareTrainingService.updateInjuries(dto, user.getEmail());
        assertAll(
                () -> assertEquals(toUpdate.getAffectedArea(), in.getAffectedArea()),
                () -> assertNotEquals(oldIdx, in.getInjuryIndex())
        );

    }


    // Helper Methods
    private ApplicationUser createUnverifiedUser(String email, String password) throws Exception {
        CreateUserDto userDto = new CreateUserDto();
        userDto.setEmail(email);
        userDto.setPassword(password);
        userDto.setFirstname("Test");
        userDto.setLastname("User");

        return userService.create(userDto, ORIGIN);
    }

    private ApplicationUser createAndVerifyUser(String email, String password) throws Exception {
        ApplicationUser user = createUnverifiedUser(email, password);
        user.setVerified(true);
        return userRepository.save(user);
    }
}