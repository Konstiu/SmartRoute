package com.smartroute.smartroute1.unittest;


import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.datagenerator.ExerciseDataGenerator;
import com.smartroute.smartroute1.endpoint.dto.GymWorkoutDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.GymWorkout;

import com.smartroute.smartroute1.entity.enums.BodyPart;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.exception.NotFoundException;
import com.smartroute.smartroute1.repository.ExerciseRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.GymWorkoutSelectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
@SpringBootTest
@ActiveProfiles({"test", "generateData"})
public class GymWorkoutSelectorTest extends BaseTest {

    private static final String USEREMAIL = "GymTest@gym.at";
    @Autowired
    private GymWorkoutSelectorService gymWorkoutSelectorService;
    @Autowired
    private UserRepository userRepository;
    private ApplicationUser user;

    @Autowired
    private ExerciseDataGenerator exerciseDataGenerator;

    @Autowired
    private ExerciseRepository exerciseRepository;


    @BeforeEach
    public void setUp() {
        user = new ApplicationUser();
        user.setFirstname("GymTest");
        user.setLastname("Test");
        user.setPassword("password");
        user.setSex(Sex.OTHER);
        user.setEmail(USEREMAIL);

        userRepository.save(user);
        if (exerciseRepository.findAll().isEmpty()) {
            exerciseDataGenerator.importExercises();
        }
    }

    @Test
    public void test_WhenPerfectReadinessScore_ThenRepsAreSmallAndSetsAreHigh() {
        GymWorkout result = gymWorkoutSelectorService.getGymWorkout(new HashMap<>(), 100);

        assertAll(
                () -> assertEquals(5, result.getReps()),
                () -> assertEquals(5, result.getSets())
        );
    }

    @Test
    public void test_WhenFlawedReadinessScore_ThenRepsAreMidAndSetsAreMid() {
        GymWorkout result = gymWorkoutSelectorService.getGymWorkout(new HashMap<>(), 50);

        assertAll(
                () -> assertEquals(11, result.getReps()),
                () -> assertEquals(4, result.getSets())
        );
    }

    @Test
    public void test_WhenZeroReadinessScore_ThenRepsAreHighAndSetsAreSmall() {
        GymWorkout result = gymWorkoutSelectorService.getGymWorkout(new HashMap<>(), 0);

        assertAll(
                () -> assertEquals(16, result.getReps()),
                () -> assertEquals(2, result.getSets())
        );
    }

    @Test
    public void test_WhenNoInjuries_ThenNumberOfExercisesDoesNotChange() {
        GymWorkout result = gymWorkoutSelectorService.getGymWorkout(new HashMap<>(), 100);

        assertEquals(11, result.getExercises().size());
    }

    @Test
    public void test_WhenOneInjuryPresent_ThenNoRecommendationForIt() {
        HashMap<BodyPart, Double> injuries = new HashMap<>();
        injuries.put(BodyPart.SPINAL_INJURY, 0.0);

        GymWorkout result = gymWorkoutSelectorService.getGymWorkout(injuries, 100);

        assertAll(
                () -> assertEquals(10, result.getExercises().size()),
                () -> assertEquals(0, result.getExercises().stream().filter(e -> e.getTargetMuscles().contains("spine")).count()),
                () -> assertEquals(0, result.getExercises().stream().filter(e -> e.getSecondaryMuscles().contains("spine")).count()),
                () -> assertEquals(1, result.getExercises().stream().filter(e -> e.getTargetMuscles().contains("adductors")).count())
        );

    }

    @Test
    public void test_WhenTwoInjuryPresent_ThenNoRecommendationForThem() {
        HashMap<BodyPart, Double> injuries = new HashMap<>();
        injuries.put(BodyPart.SPINAL_INJURY, 0.0);
        injuries.put(BodyPart.LOWER_LEG_REGION, 0.0);

        GymWorkout result = gymWorkoutSelectorService.getGymWorkout(injuries, 100);

        assertAll(
                () -> assertEquals(9, result.getExercises().size()),
                () -> assertEquals(0, result.getExercises().stream().filter(e -> e.getTargetMuscles().contains("spine")).count()),
                () -> assertEquals(0, result.getExercises().stream().filter(e -> e.getSecondaryMuscles().contains("spine")).count()),
                () -> assertEquals(0, result.getExercises().stream().filter(e -> e.getTargetMuscles().contains("calves")).count()),
                () -> assertEquals(0, result.getExercises().stream().filter(e -> e.getSecondaryMuscles().contains("calves")).count()),
                () -> assertEquals(1, result.getExercises().stream().filter(e -> e.getTargetMuscles().contains("adductors")).count())
        );

    }

    @Test
    public void test_WhenAllRelevantBodyPartsInjured_ReturnsEmptyList() {
        HashMap<BodyPart, Double> injuries = new HashMap<>();
        for (BodyPart bodyPart : BodyPart.values()) {
            injuries.put(bodyPart, 0.0);
        }

        GymWorkout result = gymWorkoutSelectorService.getGymWorkout(injuries, 100);

        assertEquals(0, result.getExercises().size());
    }

    @Test
    public void test_GivenEmail_WhenGetAllGymWorkouts_ThenFindsAllForUser() {

        gymWorkoutSelectorService.getGymWorkout(user, new HashMap<>(), 100);
        gymWorkoutSelectorService.getGymWorkout(user, new HashMap<>(), 100);


        List<GymWorkoutDto> result = gymWorkoutSelectorService.getAllGymWorkouts(USEREMAIL);

        assertEquals(2, result.size());
    }

    @Test
    public void test_GetGymWorkoutById_success() {
        GymWorkoutDto created = gymWorkoutSelectorService.getGymWorkout(user, new HashMap<>(), 100);

        GymWorkoutDto fetched = gymWorkoutSelectorService.getGymWorkoutById(created.getId(), USEREMAIL);

        assertNotNull(fetched);
        assertEquals(created.getId(), fetched.getId());
        assertEquals(created.getReps(), fetched.getReps());
        assertEquals(created.getSets(), fetched.getSets());
        assertEquals(created.getExercises().size(), fetched.getExercises().size());
    }

    @Test
    public void test_GetGymWorkoutById_workoutNotFound() {
        Long missingId = -100L;
        assertThrows(
                NotFoundException.class,
                () -> gymWorkoutSelectorService.getGymWorkoutById(missingId, USEREMAIL)
        );
    }

    @Test
    public void test_GetGymWorkoutById_forbidden() {
        // create another user and a workout for that other user
        ApplicationUser other = new ApplicationUser();
        other.setFirstname("Other");
        other.setLastname("User");
        other.setPassword("pw");
        other.setSex(Sex.OTHER);
        other.setEmail("other@gym.at");
        userRepository.save(other);

        GymWorkoutDto createdForOther = gymWorkoutSelectorService.getGymWorkout(other, new HashMap<>(), 100);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> gymWorkoutSelectorService.getGymWorkoutById(createdForOther.getId(), USEREMAIL)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

}
