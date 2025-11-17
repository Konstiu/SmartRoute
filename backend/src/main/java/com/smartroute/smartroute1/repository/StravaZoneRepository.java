package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.StravaAccount;
import com.smartroute.smartroute1.entity.StravaZone;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StravaZoneRepository extends JpaRepository<StravaZone, Long> {
    void deleteAllByStravaAccount(StravaAccount account);
}
