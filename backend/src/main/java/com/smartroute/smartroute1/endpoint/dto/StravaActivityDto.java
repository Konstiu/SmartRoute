package com.smartroute.smartroute1.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StravaActivityDto {

    private Long id;

    @JsonProperty("external_id")
    private String externalId;

    @JsonProperty("upload_id")
    private Long uploadId;

    private String name;
    private Float distance;

    @JsonProperty("moving_time")
    private Integer movingTime;

    @JsonProperty("elapsed_time")
    private Integer elapsedTime;

    @JsonProperty("total_elevation_gain")
    private Float totalElevationGain;

    private String type;

    @JsonProperty("sport_type")
    private String sportType;

    @JsonProperty("workout_type")
    private Integer workoutType;

    @JsonProperty("start_date")
    private String startDate;

    @JsonProperty("start_date_local")
    private String startDateLocal;

    private String timezone;

    @JsonProperty("utc_offset")
    private Float utcOffset;

    @JsonProperty("location_city")
    private String locationCity;

    @JsonProperty("location_state")
    private String locationState;

    @JsonProperty("location_country")
    private String locationCountry;

    @JsonProperty("achievement_count")
    private Integer achievementCount;

    @JsonProperty("kudos_count")
    private Integer kudosCount;

    @JsonProperty("comment_count")
    private Integer commentCount;

    @JsonProperty("athlete_count")
    private Integer athleteCount;

    @JsonProperty("photo_count")
    private Integer photoCount;

    @JsonProperty("total_photo_count")
    private Integer totalPhotoCount;

    private Boolean trainer;
    private Boolean commute;
    private Boolean manual;

    @JsonProperty("private")
    private Boolean isPrivate;

    private Boolean flagged;

    @JsonProperty("gear_id")
    private String gearId;

    @JsonProperty("from_accepted_tag")
    private Boolean fromAcceptedTag;

    @JsonProperty("average_speed")
    private Float averageSpeed;

    @JsonProperty("max_speed")
    private Float maxSpeed;

    @JsonProperty("average_cadence")
    private Float averageCadence;

    @JsonProperty("average_watts")
    private Float averageWatts;

    @JsonProperty("weighted_average_watts")
    private Float weightedAverageWatts;

    private Float kilojoules;

    @JsonProperty("device_watts")
    private Boolean deviceWatts;

    @JsonProperty("has_heartrate")
    private Boolean hasHeartrate;

    @JsonProperty("average_heartrate")
    private Float averageHeartrate;

    @JsonProperty("max_heartrate")
    private Float maxHeartrate;

    @JsonProperty("max_watts")
    private Float maxWatts;

    @JsonProperty("pr_count")
    private Integer prCount;

    @JsonProperty("has_kudoed")
    private Boolean hasKudoed;

    @JsonProperty("suffer_score")
    private Integer sufferScore;

    @JsonProperty("device_name")
    private String deviceName;

    private StravaAthleteRef athlete;
    private StravaMap map;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StravaAthleteRef {
        private Long id;

        @JsonProperty("resource_state")
        private Integer resourceState;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StravaMap {
        private String id;

        @JsonProperty("summary_polyline")
        private String summaryPolyline;

        @JsonProperty("resource_state")
        private Integer resourceState;
    }
}
