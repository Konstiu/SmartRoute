package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.ConsistencyScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConsistencyRepository extends JpaRepository<ConsistencyScore, Long> {
}
