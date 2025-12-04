package com.smartroute.smartroute1.endpoint.dto;

import lombok.Data;

import java.util.List;

@Data
public class GymWorkoutDto {
    private Long id;


    private List<ExerciseDto> exercises;
    private Integer sets;
    private Integer reps;
}
