package com.smartroute.smartroute1.endpoint.mapper;

import com.smartroute.smartroute1.endpoint.dto.ExerciseDto;
import com.smartroute.smartroute1.endpoint.dto.GymWorkoutDto;
import com.smartroute.smartroute1.entity.Exercise;
import com.smartroute.smartroute1.entity.GymWorkout;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface GymWorkOutMapper {
    default GymWorkoutDto toDto(GymWorkout entity) {
        if (entity == null) {
            return null;
        }

        GymWorkoutDto dto = new GymWorkoutDto();
        dto.setId(entity.getId());
        dto.setSets(entity.getSets());
        dto.setReps(entity.getReps());

        // map exercises -> ExerciseDto
        dto.setExercises(mapExercises(entity.getExercises()));

        return dto;
    }

    default List<GymWorkoutDto> toDtos(List<GymWorkout> entities) {
        return entities == null ? List.of() : entities.stream().map(this::toDto).toList();
    }

    private List<ExerciseDto> mapExercises(List<Exercise> exercises) {
        if (exercises == null) {
            return List.of();
        }

        return exercises.stream().map(this::toExerciseDto).toList();
    }

    private ExerciseDto toExerciseDto(Exercise entity) {
        if (entity == null) {
            return null;
        }

        ExerciseDto dto = new ExerciseDto();
        dto.setName(entity.getName());
        dto.setGifUrl(entity.getGifUrl());

        dto.setTargetMuscles(safeList(entity.getTargetMuscles()));
        dto.setBodyParts(safeList(entity.getBodyParts()));
        dto.setEquipments(safeList(entity.getEquipments()));
        dto.setSecondaryMuscles(safeList(entity.getSecondaryMuscles()));
        dto.setInstructions(safeList(entity.getInstructions()));
        dto.setExerciseId(entity.getId());

        return dto;
    }


    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

}
