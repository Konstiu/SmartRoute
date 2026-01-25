package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
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

    List<Activity> findTop10ByUserAndTypeIsAndWorkoutTypeIsOrderByStartDateDesc(ApplicationUser user, String type, WorkoutType workoutType, Pageable pageable);

    @Query(value = """
        SELECT moving_time
        FROM (
            SELECT a.moving_time
            FROM activity a
            WHERE a.user_id = :#{#user.id}
                AND a.type = :type
                AND a.start_date < CAST(:activityDate AS TIMESTAMP)
                AND a.start_date >= DATEADD('WEEK', -8, CAST(:activityDate AS TIMESTAMP))
            ORDER BY a.start_date DESC
            LIMIT 20
        ) last_20
        ORDER BY moving_time ASC
        """, nativeQuery = true)
    List<Integer> getDurationsInLast20ActivitiesBeforeActivityByUserAndTypeAsc(@Param("user") ApplicationUser user, @Param("type") String type, @Param("activityDate") Instant activityDate);

    @Query(value = """
        SELECT distance
        FROM (
            SELECT a.distance
            FROM activity a
            WHERE a.user_id = :#{#user.id}
                AND a.type = :type
                AND a.start_date < CAST(:activityDate AS TIMESTAMP)
                AND a.start_date >= DATEADD('WEEK', -8, CAST(:activityDate AS TIMESTAMP))
            ORDER BY a.start_date DESC
            LIMIT 20
        ) last_20
        ORDER BY distance ASC
        """, nativeQuery = true)
    List<Integer> getDistancesInLast20ActivitiesBeforeActivityByUserAndTypeAsc(@Param("user") ApplicationUser user, @Param("type") String type, @Param("activityDate") Instant activityDate);

    @Query(value = """
        SELECT average_speed
        FROM (
            SELECT a.average_speed
            FROM activity a
            WHERE a.user_id = :#{#user.id}
                AND a.type = :type
                AND a.start_date < CAST(:activityDate AS TIMESTAMP)
                AND a.start_date >= DATEADD('WEEK', -8, CAST(:activityDate AS TIMESTAMP))
            ORDER BY a.start_date DESC
            LIMIT 20
        ) last_20
        ORDER BY average_speed ASC
        """, nativeQuery = true)
    List<Double> getPacesInLast20ActivitiesBeforeActivityByUserAndTypeAsc(@Param("user") ApplicationUser user, @Param("type") String type, @Param("activityDate") Instant activityDate);

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

    @Query("""
            select a
            from Activity a
            where a.user = :user
              and a.type = 'Run'
              and a.startDate between :start and :end
            order by a.startDate asc
        """)
    List<Activity> findRunsInPeriod(
            @Param("user") ApplicationUser user,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

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
