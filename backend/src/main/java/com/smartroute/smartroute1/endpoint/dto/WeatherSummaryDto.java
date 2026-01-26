package com.smartroute.smartroute1.endpoint.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WeatherSummaryDto {
    String temperatureText;
    String windText;
    String precipitationText;
    String uvText;
}
