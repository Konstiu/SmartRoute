package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.PreKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PreKeyRepository extends JpaRepository<PreKey, Long> {

    /**
     * Count pre-keys by user ID.
     *
     * @param userId the user ID
     * @return the count of pre-keys associated with the user
     */
    long countByUserId(Long userId);

    /**
     * Check if a pre-key exists by its UUID.
     *
     * @param uuid the UUID of the pre-key
     * @return true if a pre-key with the given UUID exists, false otherwise
     */
    boolean existsByUuid(UUID uuid);

}
