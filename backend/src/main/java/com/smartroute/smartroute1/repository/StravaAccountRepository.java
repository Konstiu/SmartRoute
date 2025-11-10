package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.StravaAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StravaAccountRepository extends JpaRepository<StravaAccount, Long> {
    Optional<StravaAccount> findByUser(ApplicationUser user);
    Optional<StravaAccount> findByAthleteId(Long athleteId);
}