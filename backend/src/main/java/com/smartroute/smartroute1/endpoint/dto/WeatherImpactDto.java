package com.smartroute.smartroute1.endpoint.dto;

import com.smartroute.smartroute1.entity.enums.HeatRiskCategory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeatherImpactDto {
    private double penaltyPercent;
    private double weatherScore;
    private HeatRiskCategory riskCategory;
}
