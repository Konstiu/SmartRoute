package com.smartroute.smartroute1.endpoint.mapper;

import com.smartroute.smartroute1.endpoint.dto.WeatherDto;
import com.smartroute.smartroute1.entity.WeatherResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface WeatherMapper {
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(WeatherDto dto, @MappingTarget WeatherResponse entity);

    default WeatherResponse toEntity(WeatherDto dto) {
        WeatherResponse entity = new WeatherResponse();

        if (dto != null) {
            entity.setTime(dto.getTime());
            entity.setTemperature2m(dto.getTemperature2m());
            entity.setPrecipitation(dto.getPrecipitation());
            entity.setRelativeHumidity(dto.getRelativeHumidity());
            entity.setWindSpeed10m(dto.getWindSpeed10m());
            entity.setShortWaveRadiation(dto.getShortWaveRadiation());
        }

        return entity;
    }
}

