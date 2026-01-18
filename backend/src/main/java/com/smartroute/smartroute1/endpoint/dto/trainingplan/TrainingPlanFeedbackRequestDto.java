package com.smartroute.smartroute1.endpoint.dto.trainingplan;

import com.smartroute.smartroute1.entity.enums.WorkoutType;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TrainingPlanFeedbackRequestDto {
    private LocalDate date;
    private WorkoutType plannedWorkout;
    private WorkoutType userChosenWorkout; // nullable if they just say “skipped”
    private boolean completed;
    private Integer satisfactionScore; // optional
    private Integer perceivedEffort;   // optional
}
