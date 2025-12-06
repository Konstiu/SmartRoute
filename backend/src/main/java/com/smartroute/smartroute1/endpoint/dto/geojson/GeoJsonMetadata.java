package com.smartroute.smartroute1.endpoint.dto.geojson;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class GeoJsonMetadata {
    private String id;
    private String attribution;
    private String service;
    private int timestamp;
}
