package com.smartroute.smartroute1.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class StravaActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long stravaId;
    private String name;
    private float distance;
    private int movingTime;
    private int elapsedTime;
    private float totalElevationGain;
    private String type;
    private String sportType;
    private String startDate;
    private String startDateLocal;
    private float averageSpeed;
    private float maxSpeed;
    private Float averageWatts;
    private Float averageHeartrate;
    private Float maxHeartrate;
    private Float kilojoules;
    private Integer sufferScore;
    @Column(columnDefinition = "TEXT")
    private String summaryPolyline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private StravaAccount stravaAccount;

    @ManyToOne
    private ApplicationUser user;
}
