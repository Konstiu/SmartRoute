package com.smartroute.smartroute1.endpoint.dto.trainingplan;

import com.smartroute.smartroute1.endpoint.dto.AthleteStatusDto;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
public class TrainingPlan7dDto {
    List<PlannedDayDto> days;
    TrainingPlanDebugDto debug;
    String planId;
    Instant generatedAt;
    AthleteStatusDto currentAthleteStatus;


    public TrainingPlan7dDto(List<PlannedDayDto> days) {
        this.days = days;
        this.debug = null;
        this.planId = null;
        this.generatedAt = null;
        this.currentAthleteStatus = null;
    }
}
