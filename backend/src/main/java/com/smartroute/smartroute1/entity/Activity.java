package com.smartroute.smartroute1.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.jdbc.Work;

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

    private Float kilojoules;

    private Integer sufferScore;

    private Double garminActivityTrainingsLoad;

    @Column(columnDefinition = "TEXT")
    private String summaryPolyline;

    private Integer sessionLoad;

    private Integer satisfactionScore;

    @Enumerated(EnumType.STRING)
    private WorkoutType workoutType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private ApplicationUser user;

}
