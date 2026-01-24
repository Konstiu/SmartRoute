package com.smartroute.smartroute1.datagenerator;

import com.smartroute.smartroute1.endpoint.dto.WeatherDto;
import com.smartroute.smartroute1.endpoint.mapper.WeatherMapper;
import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.repository.WeatherRepository;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Profile("generateData")
@Component
@AllArgsConstructor
public class WeatherDataGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final WeatherRepository repository;
    private final Random random = new Random();
    private final WeatherMapper mapper;


    @PostConstruct
    public void generateWeather() {
        if (!repository.findAll().isEmpty()) {
            LOGGER.info("Weather already generated");
        } else {
            try {
                LOGGER.info("Generate Weather Data");
                final double latitude = -90 + 180 * random.nextDouble();
                final double longitude = -180 + 360 * random.nextDouble();

                LocalDateTime now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
                double baseTemp = 12.0;
                double amplitude = 6.0;

                List<WeatherResponse> list = new ArrayList<>();
                WeatherResponse entity;

                for (int hour = 0; hour < 24; hour++) {

                    double humidity = 50 + Math.sin((hour / 24.0) * 2 * Math.PI + Math.PI) * 20;
                    humidity += random.nextDouble() * 5;
                    humidity = Math.max(20, Math.min(100, humidity));

                    double wind = 1 + random.nextDouble() * 6;

                    double precipitation = random.nextDouble() > 0.85 ? random.nextDouble() * 5 : 0;

                    double daylight = Math.sin((hour - 6) / 12.0 * Math.PI);
                    double solar = daylight > 0 ? daylight * 800 : 0;
                    double directRadiation = daylight > 0 ? daylight * 800 : 0;
                    double diffuseRadiation = daylight > 0 ? daylight * 800 : 0;

                    double uvIndex = solar / 80.0;
                    uvIndex += (random.nextDouble() - 0.5) * 1.2;
                    uvIndex = Math.max(0.0, Math.min(11.0, uvIndex));

                    LocalDateTime t = now.plusHours(hour);

                    double temp = baseTemp + Math.sin((hour / 24.0) * 2 * Math.PI) * amplitude;

                    double surfacePressure = 1000;
                    double dewPoint = 4;
                    double snowDepth = temp - 1;

                    WeatherDto dto = new WeatherDto(
                            t.toString(),
                            temp,
                            wind,
                            precipitation,
                            humidity,
                            solar,
                            directRadiation,
                            diffuseRadiation,
                            surfacePressure,
                            dewPoint,
                            snowDepth,
                            uvIndex
                    );

                    entity = mapper.toEntity(dto, null, latitude, longitude);
                    list.add(entity);
                }

                repository.saveAll(list);
            } catch (ArithmeticException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
