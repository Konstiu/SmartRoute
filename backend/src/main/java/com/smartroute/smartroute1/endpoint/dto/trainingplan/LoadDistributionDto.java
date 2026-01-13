package com.smartroute.smartroute1.endpoint.dto.trainingplan;

import lombok.Data;

@Data
public class LoadDistributionDto {
    double p10;
    double p50;
    double p90;
    double mean;
    double std;
}
