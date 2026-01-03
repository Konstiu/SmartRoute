package com.smartroute.smartroute1.entity;

import jakarta.persistence.Embeddable;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor
public class ClassificationCorrectionMap {

    //easy
    Double easyToTempo = 0.0;
    Double easyToInterval = 0.0;
    Double easyToLong = 0.0;

    //tempo
    Double tempoToEasy = 0.0;
    Double tempoToInterval = 0.0;
    Double tempoToLong = 0.0;

    //interval
    Double intervalToEasy = 0.0;
    Double intervalToTempo = 0.0;
    Double intervalToLong = 0.0;

    //long
    Double longToEasy = 0.0;
    Double longToInterval = 0.0;
    Double longToTempo = 0.0;


    public Double getEasyToInterval() {
        return clamp(easyToInterval);
    }

    public Double getEasyToTempo() {
        return clamp(easyToTempo);
    }

    public Double getEasyToLong() {
        return clamp(easyToLong);
    }

    public Double getTempoToEasy() {
        return clamp(tempoToEasy);
    }

    public Double getTempoToLong() {
        return clamp(tempoToLong);
    }

    public Double getTempoToInterval() {
        return clamp(tempoToInterval);
    }

    public Double getIntervalToEasy() {
        return clamp(intervalToEasy);
    }

    public Double getIntervalToTempo() {
        return clamp(intervalToTempo);
    }

    public Double getIntervalToLong() {
        return clamp(intervalToLong);
    }

    public Double getLongToEasy() {
        return clamp(longToEasy);
    }

    public Double getLongToInterval() {
        return clamp(longToInterval);
    }

    public Double getLongToTempo() {
        return clamp(longToTempo);
    }

    private Double clamp(Double value) {
        return Math.min(0.1, value * 0.01);
    }
}
