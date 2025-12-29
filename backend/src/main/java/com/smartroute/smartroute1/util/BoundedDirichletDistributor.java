package com.smartroute.smartroute1.util;

import java.util.concurrent.ThreadLocalRandom;

public class BoundedDirichletDistributor {


    private static double rand(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * Distribute 100% to 5 different percentage with random variance for effective test data generation.
     *
     * @param base  target ratios (must sum 1)
     * @param min   minimum allowed per bucket
     * @param max   maximum allowed per bucket
     * @param noise ±noise (0.2 = ±20%)
     */
    public double[] distribute(double[] base,
                               double[] min,
                               double[] max,
                               double noise) {
        int n = base.length;
        double[] out = new double[n];
        double sum = 0;

        for (int i = 0; i < n; i++) {
            double factor = 1 + rand(-noise, noise);
            out[i] = clamp(base[i] * factor, min[i], max[i]);
            sum += out[i];
        }

        // Normalize
        for (int i = 0; i < n; i++) {
            out[i] /= sum;
        }

        return out;
    }
}
