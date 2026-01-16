package com.smartroute.smartroute1.endpoint.dto.trainingplan;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TrainingPlan7dDto {
    List<PlannedDayDto> days;
}
