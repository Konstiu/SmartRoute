package com.smartroute.smartroute1.endpoint.dto.statistics;

import com.smartroute.smartroute1.endpoint.dto.ActivityDto;
import com.smartroute.smartroute1.endpoint.dto.DetailedActivityDto;
import com.smartroute.smartroute1.entity.Activity;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RunHistoryDto {
    int numberOfRuns;

    double totalRunTime;
    double totalDistance;

    List<DetailedActivityDto> runHistory;
}
