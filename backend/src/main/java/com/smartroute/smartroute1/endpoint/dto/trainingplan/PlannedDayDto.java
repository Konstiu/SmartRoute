package com.smartroute.smartroute1.endpoint.dto.trainingplan;

import com.smartroute.smartroute1.entity.enums.WorkoutType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
public class PlannedDayDto {
    LocalDate date;
    WorkoutType workoutType;
    LoadDistributionDto load;
    LoadDistributionDto tsb;
    String confidence;
    List<String> explanation;
}