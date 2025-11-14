package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.ViennaPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ViennaPointRepository extends JpaRepository<ViennaPoint, String> {
}
