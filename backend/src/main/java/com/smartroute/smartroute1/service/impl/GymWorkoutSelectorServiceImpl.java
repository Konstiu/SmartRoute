package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.entity.Exercise;
import com.smartroute.smartroute1.entity.GymWorkout;
import com.smartroute.smartroute1.entity.enums.BodyPart;
import com.smartroute.smartroute1.entity.enums.Muscle;
import com.smartroute.smartroute1.repository.ExerciseRepository;
import com.smartroute.smartroute1.service.GymWorkoutSelectorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@Service
public class GymWorkoutSelectorServiceImpl implements GymWorkoutSelectorService {

    private static final Integer SET_MAX = 5;
    private static final Integer SET_MIN = 2;

    private static final Integer REP_MAX = 16;
    private static final Integer REP_MIN = 5;
    private static final HashMap<Muscle, Muscle> counterparts = new HashMap<>();
    private final ExerciseRepository exerciseRepository;
    private final List<Muscle> toTrain;

    public GymWorkoutSelectorServiceImpl(ExerciseRepository exerciseRepository) {
        this.exerciseRepository = exerciseRepository;
        this.toTrain = getTrainingMuscles();


    }

    /**
     * A simple helper function to get the affected muscles, of a specific body part.
     *
     * @param bodyPart the body part to search for
     * @return the list of muscles that are in the body part
     */
    private static List<Muscle> musclesOf(BodyPart bodyPart) {
        return Arrays.stream(Muscle.values())
                .filter(m -> m.getBodyPart() == bodyPart)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GymWorkout getGymWorkout(Map<BodyPart, Double> injuriesMap, Integer readinessScore) {
        if (readinessScore > 100 || readinessScore < 0) {
            throw new IllegalArgumentException("Readiness Score must be between 0 and 100, but was" + readinessScore);
        }
        if (injuriesMap == null) {
            injuriesMap = new HashMap<>();
        }

        Set<Muscle> removedMuscles = new HashSet<>();
        List<Muscle> musclesToTrain = new ArrayList<>(this.toTrain);

        for (BodyPart bodyPart : injuriesMap.keySet()) {
            if (injuriesMap.get(bodyPart) < 0.5) {
                List<Muscle> toRemove = musclesOf(bodyPart);
                removedMuscles.addAll(toRemove);
                musclesToTrain.removeAll(toRemove);
            }
        }

        List<Exercise> selected = new ArrayList<>();

        for (Muscle muscle : musclesToTrain) {
            List<Exercise> safeExercises = findSafeExercises(muscle, removedMuscles);
            Exercise selectedExercise = chooseRandom(safeExercises);

            if (selectedExercise != null) {
                selected.add(selectedExercise);
            }
        }

        double sets = SET_MIN + (SET_MAX - SET_MIN) * (readinessScore / 100.0);
        double reps = REP_MAX - (REP_MAX - REP_MIN) * (readinessScore / 100.0);


        GymWorkout gymWorkout = new GymWorkout();
        gymWorkout.setExercises(selected);
        gymWorkout.setReps((int) Math.round(reps));
        gymWorkout.setSets((int) Math.round(sets));

        return gymWorkout;
    }

    /**
     * Statically fills a list with the necessary muscles for complementary running training.
     *
     * @return a filled list of muscles that need training
     */
    private List<Muscle> getTrainingMuscles() {
        List<Muscle> toTrain = new ArrayList<>();
        toTrain.add(Muscle.glutes);
        toTrain.add(Muscle.hamstrings);
        toTrain.add(Muscle.calves);
        toTrain.add(Muscle.abs);
        toTrain.add(Muscle.spine);
        toTrain.add(Muscle.upper_back);
        toTrain.add(Muscle.adductors);
        toTrain.add(Muscle.delts);
        toTrain.add(Muscle.biceps);
        toTrain.add(Muscle.triceps);
        toTrain.add(Muscle.pectorals);

        return toTrain;
    }

    /**
     * Gets all exercises from the database for a specific muscle.
     * and removes those that have primary or secondary muscle training
     * on the ones in the removedMuscles set.
     *
     * @param target         which muscle to train
     * @param removedMuscles the muscles that should not be affected
     * @return the list of valid exercises
     */
    private List<Exercise> findSafeExercises(Muscle target, Set<Muscle> removedMuscles) {
        List<Exercise> all = exerciseRepository.findAll();

        return all.stream()
                .filter(e -> {
                    Set<Muscle> primary = toMuscleSet(e.getTargetMuscles());
                    Set<Muscle> secondary = toMuscleSet(e.getSecondaryMuscles());

                    return primary.contains(target)
                            && Collections.disjoint(primary, removedMuscles)
                            && Collections.disjoint(secondary, removedMuscles);
                })
                .toList();
    }

    /**
     * Parse strings into the muscle enum.
     *
     * @param raw the list of strings to parse
     * @return the list of corresponding muscles
     */
    private Set<Muscle> toMuscleSet(List<String> raw) {
        Set<Muscle> result = new HashSet<>();
        if (raw == null) {
            return result;
        }

        for (String s : raw) {
            s = s.replace(' ', '_');
            result.add(Muscle.valueOf(s));
        }
        return result;
    }

    /**
     * Choose a random exercise from the list, if it isn't empty.
     *
     * @param list the list of possible exercises
     * @return one random exercise
     */
    private Exercise chooseRandom(List<Exercise> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }

        Random rnd = new Random();
        return list.get(rnd.nextInt(list.size()));
    }


}
