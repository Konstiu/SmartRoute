package com.smartroute.smartroute1.endpoint.mapper;

import com.smartroute.smartroute1.endpoint.dto.WeatherDto;
import com.smartroute.smartroute1.entity.WeatherResponse;
import org.mapstruct.Mapper;

@Mapper
public interface WeatherMapper {
    default WeatherResponse toEntity(WeatherDto dto, WeatherResponse entity, Double longitude, Double latitude) {
        if (entity == null) {
            entity = new WeatherResponse();
        }

        if (dto != null) {
            entity.setLongitude(longitude);
            entity.setLatitude(latitude);
            entity.setTime(dto.getTime());
            entity.setTemperature2m(dto.getTemperature2m());
            entity.setPrecipitation(dto.getPrecipitation());
            entity.setRelativeHumidity(dto.getRelativeHumidity());
            entity.setWindSpeed10m(dto.getWindSpeed10m());
            entity.setShortWaveRadiation(dto.getShortWaveRadiation());
            entity.setDirectRadiation(dto.getDirectRadiation());
            entity.setDiffuseRadiation(dto.getDiffuseRadiation());
            entity.setSurfacePressure(dto.getSurfacePressure());
            entity.setDewPoint(dto.getDewPoint());
        }

        return entity;
    }
}

