package com.smartroute.smartroute1.endpoint.dto.trainingplan;

import com.smartroute.smartroute1.entity.enums.FeedbackReason;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SubmitTrainingPlanFeedbackDto {
    private LocalDate plannedDate;
    private WorkoutType recommendedWorkoutType;
    private WorkoutType userPreferredWorkoutType; // nullable
    private Boolean didFollow;                    // nullable
    private FeedbackReason reason;                // nullable
    private String comment;                       // nullable
}
