package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.GymWorkoutDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.GymWorkout;
import com.smartroute.smartroute1.entity.enums.BodyPart;

import java.util.List;
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

    /**
     * The gym workout selector aims to offer complementary workout plan for the gym,
     * if the previously mentioned workout type selector deems it necessary.
     * By selecting one exercise for every relevant muscle group, while ignoring muscles, that are
     * injured All exercises are taken from the Open source dataset
     * <a href="https://www.exercisedb.dev/"> ExerciseDB</a>
     * This call saves the gym workout for the corresponding user
     *
     * @param user           the user to save the Gymworkout to
     * @param injuriesMap    determines which muscles will be left out for the trainingplan.
     * @param readinessScore determines how many sets and reps the user should do
     * @return A Gymworkout entity with a list of exercises and sets & reps count
     */
    GymWorkoutDto getGymWorkout(ApplicationUser user, Map<BodyPart, Double> injuriesMap, Integer readinessScore);

    /**
     * Get all gymworkouts for a user specified by their email.
     *
     * @param email the email of the user to search for.
     * @return the list of all gym workouts created for that user.
     */
    List<GymWorkoutDto> getAllGymWorkouts(String email);

    /**
     * Get a gym workout by id.
     *
     * @param id the id of the gym workout
     * @return the gym workout from the id
     */
    GymWorkoutDto getGymWorkoutById(Long id);
}
