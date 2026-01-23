package com.smartroute.smartroute1.repository.statistics;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Ctl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface CtlRepository extends JpaRepository<Ctl, Double> {

    List<Ctl> getCtlByUserAndDateBetween(ApplicationUser user, Instant from, Instant to);

    void deleteCtlsByUser(ApplicationUser user);
}
