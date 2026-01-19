package com.smartroute.smartroute1.endpoint.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HistoryDto {
    ConsistencyHistoryDto consistencyHistory;
    GymHistoryDto gymHistory;
    InjuryHistoryDto injuryHistory;
    RunHistoryDto runHistory;
}
