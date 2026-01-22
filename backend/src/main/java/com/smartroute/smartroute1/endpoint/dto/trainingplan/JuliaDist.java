package com.smartroute.smartroute1.endpoint.dto.trainingplan;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JuliaDist {
    double p10;
    double p50;
    double p90;
    double mean;
    double std;
}
