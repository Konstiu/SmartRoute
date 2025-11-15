package com.smartroute.smartroute1.endpoint.mapper;

import com.smartroute.smartroute1.endpoint.dto.StravaActivityViewDto;
import com.smartroute.smartroute1.entity.StravaActivity;
import org.mapstruct.Mapper;

@Mapper
public interface StravaActivityMapper {
    default StravaActivityViewDto toViewDto(StravaActivity entity) {
        StravaActivityViewDto dto = new StravaActivityViewDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDistance(entity.getDistance());
        dto.setMovingTime(entity.getMovingTime());
        dto.setElapsedTime(entity.getElapsedTime());
        dto.setTotalElevationGain(entity.getTotalElevationGain());
        dto.setType(entity.getType());
        dto.setSportType(entity.getSportType());
        dto.setStartDate(entity.getStartDate());
        dto.setStartDateLocal(entity.getStartDateLocal());
        dto.setAverageSpeed(entity.getAverageSpeed());
        dto.setMaxSpeed(entity.getMaxSpeed());
        dto.setAverageHeartrate(entity.getAverageHeartrate());
        dto.setMaxHeartrate(entity.getMaxHeartrate());
        dto.setAverageWatts(entity.getAverageWatts());
        dto.setKilojoules(entity.getKilojoules());
        dto.setKudosCount(entity.getKudosCount());
        dto.setSummaryPolyline(entity.getMap());
        return dto;
    }
}
