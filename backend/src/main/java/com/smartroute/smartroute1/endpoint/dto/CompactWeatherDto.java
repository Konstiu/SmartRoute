package com.smartroute.smartroute1.endpoint.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CompactWeatherDto {
    Double weatherScore;
    Double temperature;
    Double windSpeed;
    String windDirection;
    Double precipitation;
    Double relativeHumidity;
    String description;
}
