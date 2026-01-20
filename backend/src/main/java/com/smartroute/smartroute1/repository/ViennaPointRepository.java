package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.ViennaPoint;
import com.smartroute.smartroute1.entity.enums.Sanitary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ViennaPointRepository extends JpaRepository<ViennaPoint, String> {
    List<ViennaPoint> findAllByType(Sanitary type);
}
