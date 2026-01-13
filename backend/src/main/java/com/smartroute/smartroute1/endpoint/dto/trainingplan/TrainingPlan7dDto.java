package com.smartroute.smartroute1.endpoint.dto.trainingplan;

import lombok.Data;

import java.util.List;

@Data
public class TrainingPlan7dDto {
    List<PlannedDayDto> days;
}
