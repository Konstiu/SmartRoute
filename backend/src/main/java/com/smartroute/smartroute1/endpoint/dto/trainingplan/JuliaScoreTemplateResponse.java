package com.smartroute.smartroute1.endpoint.dto.trainingplan;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JuliaScoreTemplateResponse {
    double avgUtility;
    JuliaDist utilDist;
    List<JuliaDist> tsbDists;  // size 7
}

