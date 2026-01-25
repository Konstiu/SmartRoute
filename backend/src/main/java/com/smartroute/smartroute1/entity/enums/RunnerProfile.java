package com.smartroute.smartroute1.entity.enums;

public record RunnerProfile(int minAge, int maxAge,
                            double minPace, double maxPace,
                            double maxDistance,
                            int maxHr,
                            double injuryRiskFactor) {
}
