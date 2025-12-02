package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.entity.GymWorkout;
import com.smartroute.smartroute1.entity.enums.BodyPart;

import java.util.Map;

public interface GymWorkoutSelectorService {
    /**
     * The gym workout selector aims to offer complementary workout plan for the gym,
     * if the previously mentioned workout type selector deems it necessary.
     * By selecting one exercise for every relevant muscle group, while ignoring muscles, that are
     * injured All exercises are taken from the Open source dataset
     * <a href="https://www.exercisedb.dev/"> ExerciseDB</a>
     *
     * @param injuriesMap    determines which muscles will be left out for the trainingplan.
     * @param readinessScore determines how many sets and reps the user should do
     * @return A Gymworkout entity with a list of exercises and sets & reps count
     */
    GymWorkout getGymWorkout(Map<BodyPart, Double> injuriesMap, Integer readinessScore);
}
