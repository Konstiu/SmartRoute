package com.smartroute.smartroute1.endpoint.dto;

import com.smartroute.smartroute1.entity.enums.WorkoutType;
import lombok.Data;

@Data
public class DetailedActivityDto {
    private Long id;
    private String name;
    private Float distance;
    private Integer movingTime;
    private Integer elapsedTime;
    private Float totalElevationGain;
    private String type;
    private String sportType;
    private String startDate;
    private String startDateLocal;
    private Float averageSpeed;
    private Float maxSpeed;
    private Float averageHeartrate;
    private Float maxHeartrate;
    private Float averageWatts;
    private Float kilojoules;
    private String summaryPolyline;
    private Integer satisfactionScore;
    private WorkoutType workoutType;
    private RunClassificationDecisionDto runClassification;
}
