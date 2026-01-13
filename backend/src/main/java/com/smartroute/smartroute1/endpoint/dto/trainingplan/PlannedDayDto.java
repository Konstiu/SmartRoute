package com.smartroute.smartroute1.endpoint.dto.trainingplan;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PlannedDayDto {
    LocalDate date;
    String sessionType;
    LoadDistributionDto sufferScore;
    LoadDistributionDto predictedTsb;
    String confidence;
    List<String> explanation;
}