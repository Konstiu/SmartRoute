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
            dto.setPrecipitation(weatherResponse.getPrecipitation());
            dto.setRelativeHumidity(weatherResponse.getRelativeHumidity());
            dto.setWindSpeed10m(weatherResponse.getWindSpeed10m());
            dto.setShortWaveRadiation(weatherResponse.getShortWaveRadiation());
        }

        return dto;
    }
}
