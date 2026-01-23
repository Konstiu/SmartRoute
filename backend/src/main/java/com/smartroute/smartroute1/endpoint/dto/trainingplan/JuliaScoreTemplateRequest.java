package com.smartroute.smartroute1.endpoint.dto.trainingplan;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class JuliaScoreTemplateRequest {
    String userId;
    String startDate;
    List<String> effectiveTemplate;
    double ctl;
    double atl;
    List<Integer> recentLoads;
    String experienceLevel;
    double injuryIndex;
    int readiness;
    List<Double> weatherScores;
    int sims;
    long seed;
    double baseUncertaintyMult;
    // renamed to satisfy checkstyle, but JSON stays "b" and "m"
    @JsonProperty("b")
    private Double baselineLoadScale;
    @JsonProperty("m")
    private List<Double> workoutMultipliers;
    Double sigma0;
    List<Double> sigmaK;
    Double betaFat;
}


