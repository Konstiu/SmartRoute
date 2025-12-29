package com.smartroute.smartroute1.endpoint.dto;

import com.smartroute.smartroute1.entity.enums.RunType;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Data
@Getter
@Setter
public class RunClassificationDecisionDto {
    RunType runType;
    Map<RunType, Double> probabilities;
}
