package com.smartroute.smartroute1.endpoint.dto.trainingplan;

import com.smartroute.smartroute1.entity.enums.WorkoutType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class DayDebugDto {
    private LocalDate date;
    private WorkoutType planned;
    private WorkoutType effective;

    private double weatherScore;

    private double expectedLoadMean;
    private double expectedLoadStd;

    private double tsbP50;

    private double fatiguePenalty;
    private double injuryPenalty;
    private double readinessPenalty;
    private double weatherPenalty;
}
