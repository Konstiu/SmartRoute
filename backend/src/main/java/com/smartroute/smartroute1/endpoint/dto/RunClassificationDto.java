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
     * duration.
     */
    private Integer duration;

    /**
     * duration_pct_pb_20.
     */
    private Double durationPb20;

    /**
     * distance.
     */
    private Double distance;

    /**
     * distance_pct_pb_20.
     */
    private Double distancePb20;

    /**
     * pace.
     */
    private Double pace;

    /**
     * pace_pct_pb_20.
     */
    private Double pacePb20;

    /**
     * elevation_gain.
     */
    private Double elevationGain;

    /**
     * session_load.
     */
    private Double sessionLoad;

    /**
     * num_pace_spikes.
     */
    private Integer numPaceSpikes;

    /**
     * num_pace_spikes_missing.
     */
    private Boolean numPaceSpikesMissing;

    /**
     * readiness_score.
     */
    private Integer readinessScore;


    /**
     * consistency_score.
     */
    private Double consistencyScore;

    /**
     * tsb.
     */
    private Double tsb;

    /**
     * age.
     */
    private Integer age;

    /**
     * weight.
     */
    private Double weight;

    /**
     * height.
     */
    private Integer height;

    /**
     * sex.
     */
    private Sex sex;

    /**
     * experience_level.
     */
    private ExperienceLevel experienceLevel;

    /**
     * injury_index.
     */
    private Double injuryIndex;

    /**
     * hr_avg.
     */
    private Double hrAvg;

    /**
     * hr_avg_missing.
     */
    private Boolean hrAvgMissing;

    /**
     * hr_max.
     */
    private Double hrMax;

    /**
     * hr_max_missing.
     */
    private Boolean hrMaxMissing;

    /**
     * zone1.
     */
    private Integer zone1;

    /**
     * zone1 percent.
     */
    private Float zone1pct;

    /**
     * zone1_missing.
     */
    private Boolean zone1Missing;

    /**
     * zone2.
     */
    private Integer zone2;

    /**
     * zone2 percent.
     */
    private Float zone2pct;

    /**
     * zone2_missing.
     */
    private Boolean zone2Missing;

    /**
     * zone3.
     */
    private Integer zone3;

    /**
     * zone3 percent.
     */
    private Float zone3pct;

    /**
     * zone3_missing.
     */
    private Boolean zone3Missing;

    /**
     * zone4.
     */
    private Integer zone4;

    /**
     * zone4 percent.
     */
    private Float zone4pct;

    /**
     * zone4_missing.
     */
    private Boolean zone4Missing;

    /**
     * zone5.
     */
    private Integer zone5;

    /**
     * zone5 percent.
     */
    private Float zone5pct;

    /**
     * zone5_missing.
     */
    private Boolean zone5Missing;

    /**
     * num_hr_spikes.
     */
    private Integer numHrSpikes;

    /**
     * num_hr_spikes_missing.
     */
    private Boolean numHrSpikesMissing;

    /**
     * windSpeed10m.
     */
    private Double windSpeed10m;

    /**
     * temperature2m.
     */
    private Double temperature2m;

    /**
     * uv_index.
     */
    private Integer uvIndex;

    /**
     * precipitation.
     */
    private Double precipitation;

    /**
     * snowDepth.
     */
    private Double snowDepth;
}
