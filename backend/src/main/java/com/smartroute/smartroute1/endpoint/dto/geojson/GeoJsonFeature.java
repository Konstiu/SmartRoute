package com.smartroute.smartroute1.endpoint.dto.geojson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
@Getter
@Setter
public class GeoJsonFeature {
    private String type;
    private List<Double> bbox;
    private GeoJsonGeometryLineString geometry;
    private GeoJsonProperties properties;
}
