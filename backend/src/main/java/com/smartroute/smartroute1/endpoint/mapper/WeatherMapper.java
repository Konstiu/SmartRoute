package com.smartroute.smartroute1.endpoint.mapper;

import com.smartroute.smartroute1.endpoint.dto.WeatherDto;
import com.smartroute.smartroute1.entity.weather.WeatherResponse;
import org.mapstruct.Mapper;
import org.springframework.stereotype.Component;

@Component
@Mapper
public class WeatherMapper {
    public WeatherDto toDto(WeatherResponse weatherResponse) {
        WeatherDto dto = new WeatherDto();

        if (weatherResponse != null) {
            dto.setTime(weatherResponse.getTime());
            dto.setTemperature2m(weatherResponse.getTemperature2m());
            dto.setPrecipitation(weatherResponse.getPrecipitation());
            dto.setRelativeHumidity(weatherResponse.getRelativeHumidity());
            dto.setWindSpeed10m(weatherResponse.getWindSpeed10m());
            dto.setShortWaveRadiation(weatherResponse.getShortWaveRadiation());
        }

        return dto;
    }


    public WeatherResponse toEntity(WeatherDto dto) {
        WeatherResponse entity = new WeatherResponse();

        if (dto == null) {
            return null;
        }

        if (entity == null) {
            entity = new WeatherResponse();
        }

        entity.setTime(dto.getTime());
        entity.setTemperature2m(dto.getTemperature2m());
        entity.setPrecipitation(dto.getPrecipitation());
        entity.setRelativeHumidity(dto.getRelativeHumidity());
        entity.setWindSpeed10m(dto.getWindSpeed10m());
        entity.setShortWaveRadiation(dto.getShortWaveRadiation());

        return entity;
    }
}
