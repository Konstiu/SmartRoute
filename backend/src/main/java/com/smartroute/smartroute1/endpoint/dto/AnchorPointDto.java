package com.smartroute.smartroute1.endpoint.dto;

import com.smartroute.smartroute1.util.Coordinate;
import lombok.Data;

// AnchorPoints.java
@Data
public class AnchorPointDto {
    public int startIndex;      // index in original polyline
    public int endIndex;        // index in original polyline
    public Coordinate startCoord;
    public Coordinate endCoord;
}