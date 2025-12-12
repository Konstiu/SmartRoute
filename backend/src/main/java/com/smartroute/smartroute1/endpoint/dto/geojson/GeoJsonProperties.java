package com.smartroute.smartroute1.endpoint.dto.geojson;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeoJsonProperties {
    private double ascent;
    private double descent;
    @JsonIgnore()
    private double distance;

    @JsonProperty("summary")
    private void unpackNameFromNestedObject(Map<String, String> summary) {
        distance = Double.parseDouble(summary.get("distance"));
    }
}
