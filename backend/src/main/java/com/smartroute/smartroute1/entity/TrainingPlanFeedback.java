package com.smartroute.smartroute1.entity;

import com.smartroute.smartroute1.entity.enums.FeedbackReason;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingPlanFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ApplicationUser user;

    @Column(nullable = false)
    private LocalDate date; // which planned day

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkoutType plannedWorkout; // what planner recommended (effective)

    @Enumerated(EnumType.STRING)
    private WorkoutType userChosenWorkout; // what user says they did / preferred

    @Column(nullable = false)
    private boolean completed; // did they do the session?

    private Integer satisfactionScore;
    private Integer perceivedEffort;

    @Column(nullable = false)
    private Instant createdAt;
}

