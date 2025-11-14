package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.entity.Exercise;
import com.smartroute.smartroute1.repository.ExerciseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest()
@ActiveProfiles({"test", "generateData"})
public class ExerciseImportTest {

    @Autowired
    private ExerciseRepository repository;

    @Test
    public void testExerciseImportReturnsNonEmptyRepository() {
        assertNotEquals(0, repository.count());
    }

    @Test
    public void testExerciseImportHasAllArgumentsOfExercise() {
        List<Exercise> exercises = repository.findAll();
        Exercise exercise = exercises.getFirst();


        assertAll(
                () -> assertNotNull(exercise.getId()),
                () -> assertNotNull(exercise.getBodyParts()),
                () -> assertNotNull(exercise.getName()),
                () -> assertNotNull(exercise.getGifUrl()),
                () -> assertNotNull(exercise.getInstructions()),
                () -> assertNotNull(exercise.getTargetMuscles()),
                () -> assertNotNull(exercise.getSecondaryMuscles())
        );

    }
}
