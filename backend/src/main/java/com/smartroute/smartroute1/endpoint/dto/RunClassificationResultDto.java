package com.smartroute.smartroute1.endpoint.dto;

import com.smartroute.smartroute1.entity.enums.RunType;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RunClassificationResultDto {
    private RunClassificationDto run;
    private RunType classification;
}
