package com.smartroute.smartroute1.endpoint.dto;

import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AddStopsDto {
    List<GeoJsonPosition> originalRoute;
    List<GeoJsonPosition> newPoint;
}
