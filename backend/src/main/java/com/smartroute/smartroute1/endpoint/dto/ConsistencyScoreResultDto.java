package com.smartroute.smartroute1.endpoint.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
@AllArgsConstructor
public class ConsistencyScoreResultDto {
    @Min(0)
    @Max(1)
    double finalScore;
    @Min(0)
    @Max(1)
    double frequencyConsistency;
    @Min(0)
    @Max(1)
    double regularityConsistency;
}
