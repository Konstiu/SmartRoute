package com.smartroute.smartroute1.endpoint.mapper;

import com.smartroute.smartroute1.endpoint.dto.StravaActivityDto;
import com.smartroute.smartroute1.entity.StravaAccount;
import com.smartroute.smartroute1.entity.StravaActivity;
import org.mapstruct.Mapper;

@Mapper
public interface StravaActivityMapper {
    default StravaActivity dtoToEntity(StravaActivityDto dto, StravaActivity entity, StravaAccount account) {

        if (entity == null) {
            entity = new StravaActivity();
        }

        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setDistance(dto.getDistance());
        entity.setMovingTime(dto.getMovingTime());
        entity.setElapsedTime(dto.getElapsedTime());
        entity.setTotalElevationGain(dto.getTotalElevationGain());
        entity.setType(dto.getType());
        entity.setSportType(dto.getSportType());
        entity.setStartDate(dto.getStartDate());
        entity.setStartDateLocal(dto.getStartDateLocal());
        entity.setAverageSpeed(dto.getAverageSpeed());
        entity.setMaxSpeed(dto.getMaxSpeed());
        entity.setAverageHeartrate(dto.getAverageHeartrate());
        entity.setMaxHeartrate(dto.getMaxHeartrate());
        entity.setAverageWatts(dto.getAverageWatts());
        entity.setKilojoules(dto.getKilojoules());
        entity.setSufferScore(dto.getSufferScore());
        entity.setSummaryPolyline(dto.getMap() != null ? dto.getMap().getSummaryPolyline() : null);
        entity.setStravaAccount(account);

        return entity;
    }

    default StravaActivityDto entityToDto(StravaActivity entity) {
        StravaActivityDto dto = new StravaActivityDto();

        if (entity != null) {
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
            dto.setSufferScore(entity.getSufferScore());
            if (entity.getSummaryPolyline() != null) {
                StravaActivityDto.StravaMap mapDto = new StravaActivityDto.StravaMap();
                mapDto.setSummaryPolyline(entity.getSummaryPolyline());
                dto.setMap(mapDto);
            }
        }

        return dto;
    }
}
