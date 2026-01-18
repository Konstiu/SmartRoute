package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.TrainingPlanFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TrainingPlanFeedbackRepository extends JpaRepository<TrainingPlanFeedback, Long> {

    List<TrainingPlanFeedback> findTop50ByUserOrderByCreatedAtDesc(ApplicationUser user);

    List<TrainingPlanFeedback> findByUserAndDateBetween(
            ApplicationUser user,
            LocalDate start,
            LocalDate end
    );

    Optional<TrainingPlanFeedback> findByUserAndDate(ApplicationUser user, LocalDate plannedDate);
}
