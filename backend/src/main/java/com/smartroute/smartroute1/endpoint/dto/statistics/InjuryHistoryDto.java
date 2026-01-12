package com.smartroute.smartroute1.endpoint.dto.statistics;

import com.smartroute.smartroute1.endpoint.dto.UpdateInjuryDto;
import com.smartroute.smartroute1.endpoint.dto.ViewInjuryDto;
import com.smartroute.smartroute1.entity.Injuries;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.hibernate.sql.Update;

import java.util.List;

@Data
@AllArgsConstructor
public class InjuryHistoryDto {
    int noOfInjuries;
    List<ViewInjuryDto> injuriesList;
}
