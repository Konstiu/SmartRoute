package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.PreKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
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

    /**
     * Find the first pre-key by user ID ordered by ID ascending.
     *
     * @param userId the user ID
     * @return an Optional containing the first PreKey if found, or empty if not found
     */
    Optional<PreKey> findFirstByUserIdOrderByIdAsc(Long userId);

}
