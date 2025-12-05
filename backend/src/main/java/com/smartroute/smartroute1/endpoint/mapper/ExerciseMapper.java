package com.smartroute.smartroute1.endpoint.mapper;

import com.smartroute.smartroute1.endpoint.dto.ExerciseDto;
import com.smartroute.smartroute1.entity.Exercise;
import org.mapstruct.Mapper;

import java.util.ArrayList;
import java.util.List;

@Mapper
public interface ExerciseMapper {

    default ExerciseDto entityToDto(Exercise exercise) {
        if (exercise == null) {
            return null;
        }
        ExerciseDto exerciseDto = new ExerciseDto();
        exerciseDto.setExerciseId(exercise.getId());
        exerciseDto.setName(exercise.getName());
        exerciseDto.setBodyParts(exercise.getBodyParts());
        exerciseDto.setEquipments(exercise.getEquipments());
        exerciseDto.setGifUrl(exercise.getGifUrl());
        exerciseDto.setInstructions(exercise.getInstructions());
        exerciseDto.setTargetMuscles(exercise.getTargetMuscles());
        exerciseDto.setSecondaryMuscles(exercise.getSecondaryMuscles());

        return exerciseDto;
    }

    default List<ExerciseDto> entityListToDtoList(List<Exercise> exercises) {
        List<ExerciseDto> exerciseDtos = new ArrayList<ExerciseDto>();
        ExerciseDto exerciseDto = null;

        for (Exercise exercise : exercises) {
            exerciseDto = entityToDto(exercise);
            exerciseDtos.add(exerciseDto);
        }
        return exerciseDtos;
    }
}
