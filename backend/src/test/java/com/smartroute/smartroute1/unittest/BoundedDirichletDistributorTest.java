package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.util.BoundedDirichletDistributor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles({"test", "generateData"})
public class BoundedDirichletDistributorTest {

    private static final double EPSILON = 1e-9;
    BoundedDirichletDistributor distributor;

    @BeforeEach
    void setup() {
        distributor = new BoundedDirichletDistributor();
    }

    @Test
    void test_WhenDirichletDistributorDistributesOver5Classes_ThenClassesStayWithinLimits() {
        double[] base = {0.20, 0.20, 0.20, 0.20, 0.20};
        double[] min = {0.10, 0.10, 0.10, 0.10, 0.10};
        double[] max = {0.30, 0.30, 0.30, 0.30, 0.30};

        double noise = 0.2;

        double[] result = distributor.distribute(base, min, max, noise);

        Assertions.assertEquals(5, result.length);

        double sum = 0;
        for (int i = 0; i < result.length; i++) {
            assertTrue(result[i] >= min[i] - EPSILON); //Value above max index
            assertTrue(result[i] <= max[i] + EPSILON); //Value below max index
            sum += result[i];
        }

        Assertions.assertEquals(1.0, sum, EPSILON);

    }

    @Test
    void test_WhenNoiseIsZero_ThenDistributionMatchesBase() {
        double[] base = {0.10, 0.15, 0.25, 0.20, 0.30};
        double[] min = {0.05, 0.05, 0.05, 0.05, 0.05};
        double[] max = {0.50, 0.50, 0.50, 0.50, 0.50};

        double noise = 0.0;

        double[] result = distributor.distribute(base, min, max, noise);

        for (int i = 0; i < base.length; i++) {
            Assertions.assertEquals(base[i], result[i], EPSILON);
        }
    }

    @Test
    void test_DistributionIsAlwaysNormalized() {
        double[] base = {0.5, 0.2, 0.15, 0.10, 0.05};
        double[] min = {0.01, 0.01, 0.01, 0.01, 0.01};
        double[] max = {0.90, 0.90, 0.90, 0.90, 0.90};

        double[] result = distributor.distribute(base, min, max, 0.5);

        double sum = 0;
        for (double v : result) {
            sum += v;
        }

        Assertions.assertEquals(1.0, sum, EPSILON);
    }

    @Test
    void test_DistributionRemainsStableAcrossMultipleRuns() {
        double[] base = {0.25, 0.25, 0.20, 0.15, 0.15};
        double[] min = {0.10, 0.10, 0.05, 0.05, 0.05};
        double[] max = {0.40, 0.40, 0.30, 0.30, 0.30};

        for (int run = 0; run < 100; run++) {
            double[] result = distributor.distribute(base, min, max, 0.3);

            double sum = 0;
            for (int i = 0; i < result.length; i++) {
                assertTrue(result[i] >= min[i] - EPSILON);
                assertTrue(result[i] <= max[i] + EPSILON);
                sum += result[i];
            }
            Assertions.assertEquals(1.0, sum, EPSILON);
        }
    }


}
