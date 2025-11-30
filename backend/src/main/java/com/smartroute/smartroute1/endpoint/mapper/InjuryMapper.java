package com.smartroute.smartroute1.endpoint.mapper;

import com.smartroute.smartroute1.endpoint.dto.ViewInjuryDto;
import com.smartroute.smartroute1.entity.Injuries;
import org.mapstruct.Mapper;

@Mapper
public interface InjuryMapper {

    default ViewInjuryDto entitytoDto(Injuries injuries) {
        ViewInjuryDto dto = new ViewInjuryDto();
        if (injuries == null) {
            return null;
        }
        dto.setInjuryId(injuries.getId());
        dto.setAffectedArea(injuries.getAffectedArea());
        dto.setLastInjuryDate(injuries.getLastInjuryDate());
        dto.setLastHealthyDate(injuries.getLastHealthyDate());
        dto.setInjuryIndex(injuries.getInjuryIndex());
        return dto;
    }
}
