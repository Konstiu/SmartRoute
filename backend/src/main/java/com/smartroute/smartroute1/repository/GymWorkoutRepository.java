package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.GymWorkout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface GymWorkoutRepository extends JpaRepository<GymWorkout, Long> {

    List<GymWorkout> findAllByUserOrderByIdDesc(ApplicationUser user);

    GymWorkout findGymWorkoutById(Long id);

    @Query("SELECT DISTINCT gw FROM GymWorkout gw " +
            "LEFT JOIN FETCH gw.exercises " +
            "WHERE gw.user = :user AND gw.creationDate = :creationDate")
    Optional<GymWorkout> findFirstByUserAndCreationDate(@Param("user") ApplicationUser user, @Param("creationDate") LocalDate creationDate);
}
