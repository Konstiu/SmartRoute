package com.smartroute.smartroute1.endpoint.dto.geojson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
@Getter
@Setter
@JsonIgnoreProperties(value = {"metadata"})
public class GeoJsonDto {
    private String type;
    private List<Double> bbox;
    private List<GeoJsonFeature> features;
    // private GeoJsonMetadata metadata;
}
