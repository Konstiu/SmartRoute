package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    @Query("SELECT f FROM Friendship f WHERE "
        + "(f.sender = :user1 AND f.receiver = :user2) OR "
        + "(f.sender = :user2 AND f.receiver = :user1)")
    Optional<Friendship> findByUsers(@Param("user1") ApplicationUser user1, @Param("user2") ApplicationUser user2);

}
