package com.smartroute.smartroute1.entity;

import com.smartroute.smartroute1.endpoint.dto.RunClassificationDecisionDto;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Activity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true, unique = true)
    private Long stravaId;

    private String name;

    private float distance;

    private int movingTime;

    private int elapsedTime;

    private float totalElevationGain;

    private String type;

    private String sportType;

    private Instant startDate;

    private String externalId;

    private Instant startDateLocal;

    private float averageSpeed;

    private float maxSpeed;

    private Float averageWatts;

    private Float averageHeartrate;

    private Float maxHeartrate;

    private Integer timeZ1;

    private Integer timeZ2;

    private Integer timeZ3;

    private Integer timeZ4;

    private Integer timeZ5;

    private Float kilojoules;

    private Integer sufferScore;

    private Double garminActivityTrainingsLoad;

    @Column(columnDefinition = "TEXT")
    private String summaryPolyline;

    private Integer sessionLoad;

    private Integer satisfactionScore;

    @Enumerated(EnumType.STRING)
    private WorkoutType workoutType;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_type_classification")
    private RunClassificationDecision runTypeClassification;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_stream_id", unique = true)
    private ActivityStream activityStream;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private ApplicationUser user;
}
