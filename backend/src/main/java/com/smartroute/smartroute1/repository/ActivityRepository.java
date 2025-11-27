package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    List<Activity> findByUser(ApplicationUser user);

    Optional<Activity> findByStravaId(Long stravaId);

    @Query("""
            SELECT COALESCE(SUM(a.sessionLoad), 0)
            FROM Activity a
            WHERE a.user = :user
            AND a.type = :type
            AND a.startDateLocal BETWEEN :start AND :end
            """)
    Integer sumSessionLoadForDay(
            @Param("user") ApplicationUser user,
            @Param("type") String type,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    List<Activity> findAllByUserAndStartDateBetweenOrderByStartDateAsc(ApplicationUser user, Instant start, Instant end);

    Activity findAllByUserOrderByStartDateAsc(ApplicationUser user);

    Activity findAllByUserOrderByStartDateDesc(ApplicationUser user);


    Activity findByIdAndUser(Long id, ApplicationUser user);
}
