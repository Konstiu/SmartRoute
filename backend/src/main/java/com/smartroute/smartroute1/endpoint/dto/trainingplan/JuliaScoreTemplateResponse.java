package com.smartroute.smartroute1.endpoint.dto.trainingplan;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class JuliaScoreTemplateResponse {
    double avgUtility;
    List<JuliaDist> tsbDists;  // size 7
}

