package com.smartroute.smartroute1.repository.statistics;


import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Atl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AtlRepository extends JpaRepository<Atl, Double> {

    List<Atl> getAtlByUserAndDateBetween(ApplicationUser user, Instant from, Instant to);

    void deleteAtlsByUser(ApplicationUser user);
}