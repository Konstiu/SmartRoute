package com.smartroute.smartroute1.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class StravaStreamDto {
    private String type;
    private List<Float> data; //Float for distance streams
    @JsonProperty("series_type")
    private String seriesType;
    @JsonProperty("original_size")
    private int originalSize;
    private String resolution;
}
