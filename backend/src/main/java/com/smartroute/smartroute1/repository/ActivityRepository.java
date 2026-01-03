package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import org.springframework.data.domain.Pageable;
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
        AND a.startDate >= :start AND a.startDate < :end
        """)
    Integer sumSessionLoadForDay(
        @Param("user") ApplicationUser user,
        @Param("type") String type,
        @Param("start") Instant start,
        @Param("end") Instant end
    );

    @Query(value = """
        SELECT COALESCE(AVG(moving_time), -1)
        FROM (
            SELECT moving_time
            FROM (
                SELECT a.moving_time
                FROM activity a
                WHERE a.user_id = :#{#user.id}
                    AND a.type = :type
                ORDER BY a.start_date DESC
                LIMIT 20
            ) last_20
            ORDER BY moving_time DESC
            LIMIT 3
        ) top_3
        """, nativeQuery = true)
    Integer findTop3AvgDurationInLast20ActivitiesByUserAndType(@Param("user") ApplicationUser user, @Param("type") String type);

    @Query(value = """
        SELECT COALESCE(AVG(distance), -1)
        FROM (
            SELECT distance
            FROM (
                SELECT a.distance
                FROM activity a
                WHERE a.user_id = :#{#user.id}
                    AND a.type = :type
                ORDER BY a.start_date DESC
                LIMIT 20
            ) last_20
            ORDER BY distance DESC
            LIMIT 3
        ) top_3
        """, nativeQuery = true)
    Integer findTop3AvgDistanceInLast20ActivitiesByUserAndType(@Param("user") ApplicationUser user, @Param("type") String type);

    @Query(value = """
        SELECT COALESCE(AVG(average_speed), -1)
        FROM (
            SELECT average_speed
            FROM (
                SELECT a.average_speed
                FROM activity a
                WHERE a.user_id = :#{#user.id}
                    AND a.type = :type
                ORDER BY a.start_date DESC
                LIMIT 20
            ) last_20
            ORDER BY average_speed DESC
            LIMIT 3
        ) top_3
        """, nativeQuery = true)
    Double findTop3AvgPaceInLast20ActivitiesByUserAndType(@Param("user") ApplicationUser user, @Param("type") String type);

    @Query("""
        SELECT COALESCE(MAX(a.maxHeartrate), -1)
        FROM Activity a
        WHERE a.user = :user
        AND a.type = :type
        """)
    Integer getMaxMaxHrInAllActivitiesByUserAndType(@Param("user") ApplicationUser user, @Param("type") String type);

    @Query("""
        SELECT COALESCE(MAX(a.averageHeartrate), -1)
        FROM Activity a
        WHERE a.user = :user
        AND a.type = :type
        """)
    Integer getMaxAverageHrInAllActivitiesByUserAndType(@Param("user") ApplicationUser user, @Param("type") String type);

    List<Activity> findTop10ByUserAndTypeIsOrderByStartDateDesc(ApplicationUser user, String type, Pageable pageable);

    List<Activity> findAllByUserAndStartDateBetweenOrderByStartDateAsc(ApplicationUser user, Instant start, Instant end);

    List<Activity> findAllByUserOrderByStartDateAsc(ApplicationUser user);

    List<Activity> findAllByUserOrderByStartDateDesc(ApplicationUser user);


    Activity findByIdAndUser(Long id, ApplicationUser user);

    Optional<Activity> getActivitiesByUserAndStartDate(ApplicationUser user, Instant startDate);

    Optional<Activity> getActivitiesByUserAndStartDateAndExternalId(ApplicationUser user, Instant startDate, String externalId);

    List<Activity> getActivitiesByUser(ApplicationUser user);

    List<Activity> findAllByUserAndStartDate(ApplicationUser user, Instant startDateLocal);

    Optional<Activity> findTopByUserAndStartDateBeforeOrderByStartDateDesc(ApplicationUser user, Instant date);

    List<Activity> findByUserOrderByStartDateDesc(ApplicationUser user, Pageable pageable);

    Optional<Activity> findTopByUserAndWorkoutTypeInAndStartDateBeforeOrderByStartDateDesc(
        ApplicationUser user,
        List<WorkoutType> workoutTypes,
        Instant startDate
    );

    void deleteAllByUser(ApplicationUser user);
}
