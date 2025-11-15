package com.smartroute.smartroute1.entity;

import com.smartroute.smartroute1.endpoint.dto.StravaActivityDto;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class StravaActivity {
    @Id
    private Long id;

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
    private Float averageHeartrate;
    private Float maxHeartrate;
    private Float averageWatts;
    private Float kilojoules;
    private Integer kudosCount;
    private String map;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private StravaAccount stravaAccount;

}
