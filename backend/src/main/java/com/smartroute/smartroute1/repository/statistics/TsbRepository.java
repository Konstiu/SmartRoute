package com.smartroute.smartroute1.repository.statistics;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Tsb;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface TsbRepository extends JpaRepository<Tsb, Integer> {

    List<Tsb> getTsbByUserAndDateBetween(ApplicationUser user, Instant from, Instant to);

    void deleteAllByUser(ApplicationUser user);
}
