package com.smartroute.smartroute1.endpoint.dto;

import com.smartroute.smartroute1.util.Coordinate;
import lombok.Data;

// ClosestPointResult.java
@Data
public class ClosestPointResultDto {
    public int segmentIndex;       // index of first point of the segment [i, i+1]
    public Coordinate closestPoint;
    public double distanceMeters;
}