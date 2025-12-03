package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.GymWorkout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GymWorkoutRepository extends JpaRepository<GymWorkout, Long> {

    List<GymWorkout> findAllByUserOrderByIdDesc(ApplicationUser user);
}
