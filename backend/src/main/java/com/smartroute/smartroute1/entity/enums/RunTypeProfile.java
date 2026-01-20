package com.smartroute.smartroute1.entity.enums;

public record RunTypeProfile(double paceMultiplier,
                             double distanceMultiplier,
                             double elevationMultiplier,
                             int zone3to5Bias,
                             int paceSpikes,
                             double zone5p,
                             double zone4p,
                             double zone3p,
                             double zone2p,
                             double zone1p) {
}
