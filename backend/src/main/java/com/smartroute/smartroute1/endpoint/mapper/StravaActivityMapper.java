package com.smartroute.smartroute1.endpoint.mapper;

import com.smartroute.smartroute1.endpoint.dto.StravaActivityDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Activity;
import org.mapstruct.Mapper;

import java.time.Instant;

@Mapper
public interface StravaActivityMapper {
    default Activity dtoToEntity(StravaActivityDto dto, Activity entity, ApplicationUser user) {
        if (dto == null || dto.getStravaId() == null) {
            return null;
        }

        if (entity == null) {
            entity = new Activity();
        }

        entity.setStravaId(dto.getStravaId());
        entity.setName(dto.getName());
        entity.setDistance(dto.getDistance());
        entity.setMovingTime(dto.getMovingTime());
        entity.setElapsedTime(dto.getElapsedTime());
        entity.setTotalElevationGain(dto.getTotalElevationGain());
        entity.setType(dto.getType());
        entity.setSportType(dto.getSportType());
        entity.setStartDate(Instant.parse(dto.getStartDate()));
        entity.setStartDateLocal(Instant.parse(dto.getStartDateLocal()));
        entity.setAverageSpeed(dto.getAverageSpeed());
        entity.setMaxSpeed(dto.getMaxSpeed());
        entity.setAverageHeartrate(dto.getAverageHeartrate());
        entity.setMaxHeartrate(dto.getMaxHeartrate());
        entity.setAverageWatts(dto.getAverageWatts());
        entity.setKilojoules(dto.getKilojoules());
        entity.setSufferScore(dto.getSufferScore());
        entity.setSummaryPolyline(dto.getMap() != null ? dto.getMap().getSummaryPolyline() : null);
        entity.setUser(user);

        return entity;
    }

    default StravaActivityDto entityToDto(Activity entity) {
        StravaActivityDto dto = new StravaActivityDto();

        if (entity != null) {
            dto.setStravaId(entity.getStravaId());
            dto.setName(entity.getName());
            dto.setDistance(entity.getDistance());
            dto.setMovingTime(entity.getMovingTime());
            dto.setElapsedTime(entity.getElapsedTime());
            dto.setTotalElevationGain(entity.getTotalElevationGain());
            dto.setType(entity.getType());
            dto.setSportType(entity.getSportType());
            dto.setStartDate(String.valueOf(entity.getStartDate()));
            dto.setStartDateLocal(String.valueOf(entity.getStartDateLocal()));
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
