package com.smartroute.smartroute1.endpoint.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AthleteStatusDto {
    Double tsb;
    Integer readinessScore;
    Double injuryIndex;
    List<ViewInjuryDto> injuries;
}
