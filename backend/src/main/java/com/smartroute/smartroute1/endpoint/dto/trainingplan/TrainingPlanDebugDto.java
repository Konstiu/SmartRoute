package com.smartroute.smartroute1.endpoint.dto.trainingplan;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TrainingPlanDebugDto {
    private int sims;
    private long seed;

    private double chosenAvgUtility;
    private double chosenTemplateIndex;

    private List<TemplateScoreDto> templateScores;
    private List<DayDebugDto> dayDebug; // for the chosen plan
    String scoringEngine; // "JULIA" or "JAVA"
}
