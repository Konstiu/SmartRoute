package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("""
        SELECT m
        FROM Message m
        WHERE
          (
            (m.sender = :user1 AND m.recipient = :user2)
            OR
            (m.sender = :user2 AND m.recipient = :user1)
          )
          AND m.timestamp > :since
        ORDER BY m.timestamp ASC
    """)
    List<Message> findConversationSince(@Param("user1") ApplicationUser user1, @Param("user2") ApplicationUser user2, @Param("since") Instant since);

}
