package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.LoadDistributionDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.service.LoadForecaster;
import com.smartroute.smartroute1.service.impl.LoadForecasterService;
import com.smartroute.smartroute1.entity.enums.ForecastState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@Transactional
@ActiveProfiles({"test"})
public class LoadForecasterServiceTest {

    private LoadForecaster forecaster;

    @BeforeEach
    void setup() {
        forecaster = new LoadForecasterService();
    }

    @Test
    void restDayAlwaysReturnsZeros() {
        ApplicationUser user = user(ExperienceLevel.INTERMEDIATE);

        LoadDistributionDto d1 = forecaster.forecastLoad(user, LocalDate.now(), WorkoutType.REST_DAY, state(0, 40, 40), history(60, 40));
        LoadDistributionDto d2 = forecaster.forecastLoad(user, LocalDate.now(), null, state(0, 40, 40), history(60, 40));

        assertAll("rest/null -> zeros",
                () -> assertAll("REST_DAY",
                        () -> assertEquals(0.0, d1.getMean(), 1e-9),
                        () -> assertEquals(0.0, d1.getStd(), 1e-9),
                        () -> assertEquals(0.0, d1.getP10(), 1e-9),
                        () -> assertEquals(0.0, d1.getP50(), 1e-9),
                        () -> assertEquals(0.0, d1.getP90(), 1e-9)
                ),
                () -> assertAll("null workout",
                        () -> assertEquals(0.0, d2.getMean(), 1e-9),
                        () -> assertEquals(0.0, d2.getStd(), 1e-9),
                        () -> assertEquals(0.0, d2.getP10(), 1e-9),
                        () -> assertEquals(0.0, d2.getP50(), 1e-9),
                        () -> assertEquals(0.0, d2.getP90(), 1e-9)
                )
        );
    }

    @Test
    void quantilesAreOrderedAndNonNegative() {
        ApplicationUser user = user(ExperienceLevel.CASUAL);
        LoadDistributionDto dist = forecaster.forecastLoad(
                user,
                LocalDate.of(2026, 1, 15),
                WorkoutType.EASY_RUN,
                state(-5, 45, 50),
                history(60, 35)
        );

        assertAll("quantiles ordering and non-negativity",
                () -> assertNotNull(dist),
                () -> assertTrue(dist.getP10() >= 0.0, "p10 must be >= 0"),
                () -> assertTrue(dist.getP50() >= 0.0, "p50 must be >= 0"),
                () -> assertTrue(dist.getP90() >= 0.0, "p90 must be >= 0"),
                () -> assertTrue(dist.getStd() >= 0.0, "std must be >= 0"),
                () -> assertTrue(dist.getP10() <= dist.getP50(), "p10 <= p50"),
                () -> assertTrue(dist.getP50() <= dist.getP90(), "p50 <= p90")
        );
    }

    @Test
    void coldStartBaselineDependsOnExperience() {
        List<Integer> cold = List.of(0, 0, 0, 0, 0, 0, 0);

        LoadDistributionDto b = forecaster.forecastLoad(user(ExperienceLevel.BEGINNER), LocalDate.now(), WorkoutType.EASY_RUN, state(0, 10, 10), cold);
        LoadDistributionDto i = forecaster.forecastLoad(user(ExperienceLevel.INTERMEDIATE), LocalDate.now(), WorkoutType.EASY_RUN, state(0, 10, 10), cold);
        LoadDistributionDto a = forecaster.forecastLoad(user(ExperienceLevel.ADVANCED), LocalDate.now(), WorkoutType.EASY_RUN, state(0, 10, 10), cold);

        assertAll("cold-start experience affects mean (BEGINNER < INTERMEDIATE < ADVANCED)",
                () -> assertTrue(b.getMean() < i.getMean(), "Beginner mean should be lower than intermediate"),
                () -> assertTrue(i.getMean() < a.getMean(), "Intermediate mean should be lower than advanced"),
                () -> assertTrue(b.getMean() > 0.0, "Beginner mean must be > 0 for EASY_RUN"),
                () -> assertTrue(a.getMean() > 0.0, "Advanced mean must be > 0 for EASY_RUN")
        );
    }

    @Test
    void fatigueFromTsbReducesMean() {
        ApplicationUser user = user(ExperienceLevel.INTERMEDIATE);
        List<Integer> hist = history(60, 45);

        LoadDistributionDto fresh = forecaster.forecastLoad(user, LocalDate.now(), WorkoutType.EASY_RUN, state(+10, 45, 45), hist);
        LoadDistributionDto tired = forecaster.forecastLoad(user, LocalDate.now(), WorkoutType.EASY_RUN, state(-25, 45, 70), hist);

        assertAll("more fatigue => lower mean (same history/workout)",
                () -> assertTrue(tired.getMean() < fresh.getMean(), "TSB -25 should reduce mean vs TSB +10"),
                () -> assertTrue(fresh.getMean() > 0.0, "fresh mean > 0"),
                () -> assertTrue(tired.getMean() > 0.0, "tired mean > 0")
        );
    }

    @Test
    void rampFactorReducesMeanWhenAtlCtlVeryHigh() {
        ApplicationUser user = user(ExperienceLevel.INTERMEDIATE);
        List<Integer> hist = history(60, 45);

        LoadDistributionDto okRamp = forecaster.forecastLoad(user, LocalDate.now(), WorkoutType.EASY_RUN, state(0, 50, 55), hist);  // ramp=1.1
        LoadDistributionDto highRamp = forecaster.forecastLoad(user, LocalDate.now(), WorkoutType.EASY_RUN, state(0, 40, 80), hist); // ramp=2.0 -> factor 0.78

        assertAll("high ATL/CTL ramp should reduce mean",
                () -> assertTrue(highRamp.getMean() < okRamp.getMean(), "ramp=2.0 should reduce mean vs ramp=1.1")
        );
    }

    @Test
    void workoutTypeOrderingHoldsForSameContext() {
        ApplicationUser user = user(ExperienceLevel.ADVANCED);
        List<Integer> hist = history(60, 50);
        ForecastState st = state(0, 60, 60);

        LoadDistributionDto easy = forecaster.forecastLoad(user, LocalDate.now(), WorkoutType.EASY_RUN, st, hist);
        LoadDistributionDto tempo = forecaster.forecastLoad(user, LocalDate.now(), WorkoutType.TEMPO_RUN, st, hist);
        LoadDistributionDto interval = forecaster.forecastLoad(user, LocalDate.now(), WorkoutType.INTERVAL_RUN, st, hist);
        LoadDistributionDto lng = forecaster.forecastLoad(user, LocalDate.now(), WorkoutType.LONG_RUN, st, hist);
        LoadDistributionDto gym = forecaster.forecastLoad(user, LocalDate.now(), WorkoutType.GYM_PREHAB, st, hist);
        LoadDistributionDto mob = forecaster.forecastLoad(user, LocalDate.now(), WorkoutType.MOBILITY, st, hist);

        assertAll("mean ordering by workout type",
                () -> assertTrue(mob.getMean() < gym.getMean(), "mob < gym"),
                () -> assertTrue(gym.getMean() < easy.getMean(), "gym < easy"),
                () -> assertTrue(easy.getMean() < tempo.getMean(), "easy < tempo"),
                () -> assertTrue(tempo.getMean() < interval.getMean(), "tempo < interval"),
                () -> assertTrue(interval.getMean() < lng.getMean(), "interval < long")
        );
    }

    @Test
    void disallowedWorkoutsForBeginnerAreDegradedNotExploding() {
        ApplicationUser beginner = user(ExperienceLevel.BEGINNER);

        List<Integer> hist = history(60, 35);
        ForecastState st = state(0, 30, 30);

        LoadDistributionDto tempo = forecaster.forecastLoad(beginner, LocalDate.now(), WorkoutType.TEMPO_RUN, st, hist);
        LoadDistributionDto interval = forecaster.forecastLoad(beginner, LocalDate.now(), WorkoutType.INTERVAL_RUN, st, hist);

        LoadDistributionDto easy = forecaster.forecastLoad(beginner, LocalDate.now(), WorkoutType.EASY_RUN, st, hist);

        assertAll("beginner tempo/interval should not exceed easy (degraded behavior)",
                () -> assertTrue(tempo.getMean() <= easy.getMean() + 1e-6, "beginner tempo mean should be <= easy mean"),
                () -> assertTrue(tempo.getMean() > 0.0, "tempo mean should remain > 0"),
                () -> assertTrue(interval.getMean() > 0.0, "interval mean should remain > 0")
        );
    }

    @Test
    void uncertaintyStdIncreasesForHardWorkouts() {
        ApplicationUser user = user(ExperienceLevel.INTERMEDIATE);
        List<Integer> hist = historyWithNoise(60, 45, 10); // some CV
        ForecastState st = state(0, 50, 50);

        LoadDistributionDto easy = forecaster.forecastLoad(user, LocalDate.now(), WorkoutType.EASY_RUN, st, hist);
        LoadDistributionDto interval = forecaster.forecastLoad(user, LocalDate.now(), WorkoutType.INTERVAL_RUN, st, hist);

        assertAll("hard sessions should have higher std fraction than easy",
                () -> assertTrue(interval.getStd() >= easy.getStd(), "interval std should be >= easy std"),
                () -> assertTrue(interval.getMean() >= easy.getMean(), "interval mean should be >= easy mean")
        );
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private static ApplicationUser user(ExperienceLevel level) {
        ApplicationUser u = new ApplicationUser();
        u.setId(1L);
        u.setEmail("u@test.com");
        u.setExperienceLevel(level);
        return u;
    }

    /**
     * Creates a ForecastState. Assumes your ForecastState has (tsb, ctl, atl) or similar.
     * If your ForecastState constructor differs, adapt this method only.
     */
    private static ForecastState state(double tsb, double ctl, double atl) {
        ForecastState s = new ForecastState(ctl, atl);

        return s;
    }

    private static List<Integer> history(int days, int load) {
        List<Integer> out = new ArrayList<>(days);
        for (int i = 0; i < days; i++) out.add(load);
        return out;
    }

    private static List<Integer> historyWithNoise(int days, int mean, int spread) {
        List<Integer> out = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            int v = mean + ((i % 4 == 0) ? spread : (i % 4 == 1) ? -spread : (i % 4 == 2) ? spread / 2 : -spread / 2);
            out.add(Math.max(0, v));
        }
        return out;
    }
}
