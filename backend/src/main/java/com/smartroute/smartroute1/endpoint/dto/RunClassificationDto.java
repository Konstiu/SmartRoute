package com.smartroute.smartroute1.endpoint.dto;

import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.Sex;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RunClassificationDto {
    /**
     * Duration of the run in seconds.
     */
    private Integer duration;

    /**
     * Duration as a percentage of the user’s personal best over the last 20 runs.
     */
    private Double durationPb20;

    /**
     * Distance of the run in meters.
     */
    private Double distance;

    /**
     * Distance as a percentage of the user’s personal best over the last 20 runs.
     */
    private Double distancePb20;

    /**
     * Average pace in meters per second.
     */
    private Double pace;

    /**
     * Pace as a percentage of the user’s personal best over the last 20 runs.
     */
    private Double pacePb20;

    /**
     * Total elevation gain during the run in meters.
     */
    private Double elevationGain;

    /**
     * Computed session load value (must be greater than or equal to zero).
     */
    private Double sessionLoad;

    /**
     * Number of significant pace spikes detected during the run.
     */
    private Integer numPaceSpikes;

    /**
     * Readiness score of the user prior to the run (range: 0–100).
     */
    private Integer readinessScore;

    /**
     * Consistency score derived from the user’s training history (range: 0–1).
     */
    private Double consistencyScore;

    /**
     * Training Stress Balance (TSB) of the user at the time of the run.
     */
    private Double tsb;

    /**
     * Age of the user in years.
     */
    private Integer age;

    /**
     * Weight of the user in kilograms.
     */
    private Double weight;

    /**
     * Height of the user in centimeters.
     */
    private Integer height;

    /**
     * Sex of the user.
     */
    private Sex sex;

    /**
     * Experience level of the user.
     */
    private ExperienceLevel experienceLevel;

    /**
     * Numeric injury index indicating injury risk or history.
     */
    private Double injuryIndex;

    /**
     * Average heart rate during the run as a percentage of the user’s maximum HR.
     */
    private Double hrAvg;

    /**
     * Indicates whether the average heart rate value is missing.
     */
    private Boolean hrAvgMissing;

    /**
     * Maximum heart rate during the run as a percentage of the user’s maximum HR.
     */
    private Double hrMax;

    /**
     * Indicates whether the maximum heart rate value is missing.
     */
    private Boolean hrMaxMissing;

    /**
     * Time spent in heart rate zone 1 in seconds.
     */
    private Integer zone1;

    /**
     * Indicates whether HR zone 1 data is missing.
     */
    private Boolean zone1Missing;

    /**
     * Time spent in heart rate zone 2 in seconds.
     */
    private Integer zone2;

    /**
     * Indicates whether HR zone 2 data is missing.
     */
    private Boolean zone2Missing;

    /**
     * Time spent in heart rate zone 3 in seconds.
     */
    private Integer zone3;

    /**
     * Indicates whether HR zone 3 data is missing.
     */
    private Boolean zone3Missing;

    /**
     * Time spent in heart rate zone 4 in seconds.
     */
    private Integer zone4;

    /**
     * Indicates whether HR zone 4 data is missing.
     */
    private Boolean zone4Missing;

    /**
     * Time spent in heart rate zone 5 in seconds.
     */
    private Integer zone5;

    /**
     * Indicates whether HR zone 5 data is missing.
     */
    private Boolean zone5Missing;

    /**
     * Number of significant heart rate spikes detected during the run.
     */
    private Integer numHrSpikes;

    /**
     * Indicates whether the number of heart rate spikes is missing.
     */
    private Boolean numHrSpikesMissing;

    /**
     * Wind speed at 10 meters above ground in kilometers per hour during the run.
     */
    private Double windSpeed10m;

    /**
     * Ambient temperature at 2 meters above ground in degrees Celsius.
     */
    private Double temperature2m;

    /**
     * UV index during the run (range: 0–13).
     */
    private Integer uvIndex;

    /**
     * Precipitation intensity during the run in millimeters per hour.
     */
    private Double precipitation;

    /**
     * Snow depth during the run in centimeters.
     */
    private Double snowDepth;
}
