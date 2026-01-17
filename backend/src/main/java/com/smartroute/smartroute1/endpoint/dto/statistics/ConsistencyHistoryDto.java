package com.smartroute.smartroute1.endpoint.dto.statistics;

import com.smartroute.smartroute1.endpoint.dto.ConsistencyScoreResultDto;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.HashMap;

@Data
@AllArgsConstructor
public class ConsistencyHistoryDto {
    HashMap<Instant, ConsistencyScoreResultDto> consistencyHistory;
    HashMap<Instant, Double> ctlHistory;
    HashMap<Instant, Double> atlHistory;
    HashMap<Instant, Double> tsbHistory;
}
