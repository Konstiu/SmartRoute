package com.smartroute.smartroute1.endpoint.dto;

import com.smartroute.smartroute1.entity.enums.WorkoutType;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RunClassificationResultDto {
    private RunClassificationDto run;
    private WorkoutType classification;
}
