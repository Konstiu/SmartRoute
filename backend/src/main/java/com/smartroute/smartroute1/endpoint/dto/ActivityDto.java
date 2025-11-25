package com.smartroute.smartroute1.endpoint.dto;

import lombok.Data;

@Data
public class ActivityDto {
    private Long id;
    private String name;
    private Float distance;
    private Integer movingTime;
    private Float totalElevationGain;
    private String sportType;
    private String startDateLocal;
    private Float averageSpeed;
    private Float averageHeartrate;
    private Float averageWatts;
}
