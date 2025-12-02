package com.smartroute.smartroute1.endpoint.dto;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Exercise;
import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Data
public class GymWorkoutDto {
    private Long id;


    private List<ExerciseDto> exercises;
    private Integer sets;
    private Integer reps;
}
