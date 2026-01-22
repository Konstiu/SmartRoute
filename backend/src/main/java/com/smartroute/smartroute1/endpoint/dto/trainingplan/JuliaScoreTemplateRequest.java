package com.smartroute.smartroute1.endpoint.dto.trainingplan;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class JuliaScoreTemplateRequest {
    String startDate;              // YYYY-MM-DD
    List<String> template;         // 7 workout type names (your enum .name())
    double ctl;
    double atl;
    List<Integer> recentLoads;
    String experienceLevel;
    double injuryIndex;
    int readiness;
    List<Double> weatherScores;    // size 7, nullable entries allowed
    int sims;
    long seed;
    double baseUncertaintyMult;
}

