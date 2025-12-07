package com.smartroute.smartroute1.endpoint.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CompactWeatherDto {
    Double weatherScore;
    Double temperature;
    Double windSpeed;
    Double precipitation;
    Double relativeHumidity;
    Double weatherPerformancePenalty;
    String weatherScoreDescription;
    WeatherSummaryDto weatherSummary;
}
