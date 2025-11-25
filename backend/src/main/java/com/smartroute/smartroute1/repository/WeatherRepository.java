package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.weather.WeatherResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WeatherRepository extends JpaRepository<WeatherResponse, Long> {
    List<WeatherResponse> findAll();

}
