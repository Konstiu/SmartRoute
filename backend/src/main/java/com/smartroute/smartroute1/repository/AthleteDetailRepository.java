package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.AthleteDetail;
import com.smartroute.smartroute1.entity.StravaAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AthleteDetailRepository extends JpaRepository<AthleteDetail, Long> {
    Optional<AthleteDetail> findByStravaAccount(StravaAccount account);
}
