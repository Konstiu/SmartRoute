package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.PreKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PreKeyRepository extends JpaRepository<PreKey, Long> {

    long countByDevice_Id(Long deviceId);

    PreKey findFirstByDevice_IdOrderByIdAsc(Long deviceId);

    boolean existsByDevice_IdAndUuid(Long deviceId, UUID uuid);
}
