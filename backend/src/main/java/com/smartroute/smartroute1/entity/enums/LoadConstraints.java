package com.smartroute.smartroute1.entity.enums;

public record LoadConstraints(
        double intensityScale,   // 0..1
        double volumeScale,      // 0..1
        double impactPenalty     // 0..1 (optional)
) {
    public static LoadConstraints none() {
        return new LoadConstraints(1.0, 1.0, 1.0);
    }

    public double combinedScale() {
        return 0.6 * volumeScale + 0.4 * intensityScale;
    }
}