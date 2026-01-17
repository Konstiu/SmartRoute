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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class TrainingPlanFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private ApplicationUser user;

    @Column(nullable = false)
    private LocalDate plannedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkoutType recommendedWorkoutType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private WorkoutType userPreferredWorkoutType; // “I would rather do X”

    @Column(nullable = true)
    private Boolean didFollow; // true/false/null (unknown)

    @Column(length = 1000)
    private String comment; // free text, optional

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private FeedbackReason reason; // optional enum

    // optional metadata to make future learning easier
    private Double weatherScore;
    private Integer readinessScore;
    private Double injuryIndex;

    // used later for “aging out” feedback
    private Instant createdAt = Instant.now();
}
