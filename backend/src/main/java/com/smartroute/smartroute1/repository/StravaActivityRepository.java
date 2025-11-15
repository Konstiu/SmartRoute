package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.StravaAccount;
import com.smartroute.smartroute1.entity.StravaActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StravaActivityRepository extends JpaRepository<StravaActivity, Long> {
    List<StravaActivity> findByStravaAccount(StravaAccount account);
}
