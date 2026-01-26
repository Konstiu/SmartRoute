package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.GymWorkoutDto;
import com.smartroute.smartroute1.endpoint.mapper.ExerciseMapper;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Exercise;
import com.smartroute.smartroute1.entity.GymWorkout;
import com.smartroute.smartroute1.entity.enums.BodyPart;
import com.smartroute.smartroute1.entity.enums.Muscle;
import com.smartroute.smartroute1.exception.NotFoundException;
import com.smartroute.smartroute1.repository.ExerciseRepository;
import com.smartroute.smartroute1.repository.GymWorkoutRepository;
import com.smartroute.smartroute1.service.GymWorkoutSelectorService;
import com.smartroute.smartroute1.service.ReadinessScoreService;
import com.smartroute.smartroute1.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cglib.core.Local;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

@Service
public class GymWorkoutSelectorServiceImpl implements GymWorkoutSelectorService {

    private static final Integer SET_MAX = 5;
    private static final Integer SET_MIN = 2;

    private static final Integer REP_MAX = 16;
    private static final Integer REP_MIN = 5;
    //This map ensures that muscle counterparts are recommended right after a muscle, for ideal suggestions
    private static final Map<Muscle, Integer> MUSCLE_ORDER = Map.ofEntries(
            Map.entry(Muscle.glutes, 1),
            Map.entry(Muscle.hamstrings, 2),
            Map.entry(Muscle.calves, 3),
            Map.entry(Muscle.abs, 4),
            Map.entry(Muscle.spine, 5),
            Map.entry(Muscle.upper_back, 6),
            Map.entry(Muscle.delts, 7),
            Map.entry(Muscle.adductors, 8),
            Map.entry(Muscle.biceps, 9),
            Map.entry(Muscle.triceps, 10),
            Map.entry(Muscle.pectorals, 11)
    );
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final ExerciseRepository exerciseRepository;
    private final UserService userService;
    private final GymWorkoutRepository gymWorkoutRepository;
    private final ExerciseMapper exerciseMapper;
    private final List<Muscle> toTrain;
    private final ReadinessScoreService readinessScoreService;


    public GymWorkoutSelectorServiceImpl(ExerciseRepository exerciseRepository, UserService userService, GymWorkoutRepository gymWorkoutRepository, ExerciseMapper exerciseMapper, ReadinessScoreService readinessScoreService) {
        this.exerciseRepository = exerciseRepository;
        this.userService = userService;
        this.gymWorkoutRepository = gymWorkoutRepository;
        this.exerciseMapper = exerciseMapper;
        this.toTrain = getTrainingMuscles();
        this.readinessScoreService = readinessScoreService;
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
    @Transactional
    public GymWorkoutDto getGymWorkout(ApplicationUser user, LocalDate date, Map<BodyPart, Double> injuriesMap, Integer readinessScore) {
        Optional<GymWorkout> existing = gymWorkoutRepository.findFirstByUserAndCreationDate(user, date);
        if (existing.isPresent()) {
            GymWorkoutDto gymWorkoutDto = getGymWorkoutById(existing.get().getId(), user.getEmail());
            return gymWorkoutDto;
        } else {
            return getGymWorkout(user, injuriesMap, readinessScore);
        }
    }

    @Override
    @Transactional
    public GymWorkoutDto getGymWorkout(ApplicationUser user, Map<BodyPart, Double> injuriesMap, Integer readinessScore) {
        LocalDate today = LocalDate.now();

        GymWorkout gymWorkout = getGymWorkout(injuriesMap, readinessScore);

        gymWorkout.setUser(user);
        gymWorkout.setCreationDate(today);

        gymWorkout = gymWorkoutRepository.save(gymWorkout);
        gymWorkoutRepository.flush();

        GymWorkoutDto gymWorkoutDto = new GymWorkoutDto();
        gymWorkoutDto.setExercises(exerciseMapper.entityListToDtoList(gymWorkout.getExercises()));
        gymWorkoutDto.setId(gymWorkout.getId());
        gymWorkoutDto.setReps(gymWorkout.getReps());
        gymWorkoutDto.setSets(gymWorkout.getSets());
        LOGGER.info("GymWorkoutDto: {}", gymWorkoutDto); //this prevents an exception
        return gymWorkoutDto;

    }

    @Override
    @Transactional
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

        musclesToTrain.sort(Comparator.comparingInt(m -> MUSCLE_ORDER.getOrDefault(m, Integer.MAX_VALUE)));

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

    @Override
    public List<GymWorkoutDto> getAllGymWorkouts(String email) {
        ApplicationUser user = userService.findApplicationUserByEmail(email);

        List<GymWorkout> gymWorkouts = gymWorkoutRepository.findAllByUserOrderByIdDesc(user);


        List<GymWorkoutDto> gymWorkoutDtos = new ArrayList<>();
        GymWorkoutDto gymWorkoutDto;

        for (GymWorkout gymWorkout : gymWorkouts) {
            gymWorkoutDto = new GymWorkoutDto();
            gymWorkoutDto.setId(gymWorkout.getId());
            gymWorkoutDto.setExercises(exerciseMapper.entityListToDtoList(gymWorkout.getExercises()));
            gymWorkoutDto.setReps(gymWorkout.getReps());
            gymWorkoutDto.setSets(gymWorkout.getSets());
            gymWorkoutDtos.add(gymWorkoutDto);
        }
        LOGGER.info("Getting all GymWorkoutDtos for user: {}", gymWorkoutDtos);
        return gymWorkoutDtos;
    }

    @Override
    public GymWorkoutDto getGymWorkoutById(Long id, String email) {
        ApplicationUser user = userService.findApplicationUserByEmail(email);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        GymWorkout gymWorkout = gymWorkoutRepository.findGymWorkoutById(id);
        if (gymWorkout == null) {
            throw new NotFoundException("Could not find GymWorkout with id " + id);
        }
        if (!gymWorkout.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "GymWorkout does not belong to user");
        }
        GymWorkoutDto gymWorkoutDto = new GymWorkoutDto();
        gymWorkoutDto.setId(gymWorkout.getId());
        gymWorkoutDto.setExercises(exerciseMapper.entityListToDtoList(gymWorkout.getExercises()));
        gymWorkoutDto.setReps(gymWorkout.getReps());
        gymWorkoutDto.setSets(gymWorkout.getSets());
        LOGGER.info("Getting GymWorkoutDto by id: {}", gymWorkoutDto);
        return gymWorkoutDto;
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

        if (all.isEmpty()) {
            LOGGER.warn("No exercises found in DB. Workouts will be empty!");
        }

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
            if (s == null || s.isBlank()) {
                continue;
            }

            s = s.toLowerCase().replace(' ', '_');

            try {
                result.add(Muscle.valueOf(s));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Warning: Unknown muscle string in DB: '" + s + "'");
            }
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
