package com.smartroute.smartroute1.endpoint.dto.statistics;

import com.smartroute.smartroute1.entity.Injuries;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class InjuryHistoryDto {
    int noOfInjuries;
    List<Injuries> injuriesList;
}
