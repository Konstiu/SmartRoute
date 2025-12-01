package com.smartroute.smartroute1.endpoint.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExerciseDto {
    private String exerciseId;
    private String name;
    private String gifUrl;
    private List<String> targetMuscles;
    private List<String> bodyParts;
    private List<String> equipments;
    private List<String> secondaryMuscles;
    private List<String> instructions;
}
