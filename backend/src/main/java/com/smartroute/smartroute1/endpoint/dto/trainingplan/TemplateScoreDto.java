package com.smartroute.smartroute1.endpoint.dto.trainingplan;

import com.smartroute.smartroute1.entity.enums.WorkoutType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TemplateScoreDto {
    private int index;
    private double avgUtility;
    private List<WorkoutType> plannedTemplate;
    private List<WorkoutType> effectiveTemplate;
}
