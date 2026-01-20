package com.smartroute.smartroute1.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StravaStreamDto {
    private String type;
    private List<Float> data; //Float to support distance streams
    @JsonProperty("series_type")
    private String seriesType;
    @JsonProperty("original_size")
    private int originalSize;
    private String resolution;
}
