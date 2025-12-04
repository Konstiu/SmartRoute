package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.WeatherResponse;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WeatherRepository extends JpaRepository<WeatherResponse, Long> {
    List<WeatherResponse> findAll();

    WeatherResponse getByTimeAndLatitudeAndLongitude(String time, Double latitude, Double longitude);

    @Modifying
    @Transactional
    @Query("""
                DELETE FROM WeatherResponse w
                WHERE w.latitude = :lat
                  AND w.longitude = :lon
            """)
    void deleteAllByCoordinates(@Param("lat") double lat, @Param("lon") double lon);
}
