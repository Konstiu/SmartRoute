package com.smartroute.smartroute1.entity.enums;

public record RunTypeProfile(double paceMultiplier,
                             double distanceMultiplier,
                             double elevationMultiplier,
                             int zone3to5Bias,
                             int paceSpikes) {
}
