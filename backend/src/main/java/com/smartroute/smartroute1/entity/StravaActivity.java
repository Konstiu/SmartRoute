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

@Entity
@Getter
@Setter
@NoArgsConstructor
public class StravaActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // done
    private float distance; // done
    private int movingTime; // done
    private int elapsedTime; // done
    private float totalElevationGain; // done
    private String type;    // unknown
    private String sportType;   // unknown
    private String startDate;   // done
    private String startDateLocal;  // unknown
    private float averageSpeed; // done
    private float maxSpeed; // done
    private Float averageWatts; // unknown
    private Float averageHeartrate; // done
    private Float maxHeartrate; // done
    private Float kilojoules; // unknown
    private Integer sufferScore; // unknown
    @Column(columnDefinition = "TEXT")
    private String summaryPolyline; // done

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private StravaAccount stravaAccount;

    @ManyToOne
    private ApplicationUser user;
}
