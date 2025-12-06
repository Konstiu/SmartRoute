package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.AthleteZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AthleteZoneRepository extends JpaRepository<AthleteZone, Long> {
    void deleteAllByUser(ApplicationUser user);

    List<AthleteZone> findAllByUser(ApplicationUser user);
}
