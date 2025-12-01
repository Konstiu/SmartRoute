package com.smartroute.smartroute1.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class StravaZoneDataDto {

    @JsonProperty("heart_rate")
    private HeartRate heartRate;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HeartRate {

        @JsonProperty("custom_zones")
        private Boolean customZones;

        @JsonProperty("zones")
        private List<Zone> zones;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Zone {
        private Integer min;
        private Integer max;
    }
}