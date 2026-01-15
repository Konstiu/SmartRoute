package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.service.LoadForecaster;
import com.smartroute.smartroute1.util.ForecastState;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.LoadDistributionDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
@SpringBootTest
@ActiveProfiles({"test", "generateData"})
public class LoadForecasterTest {

    @Autowired
    private LoadForecaster forecaster;

    @Test
    void restDay_returnsZeroEverywhere() {
        ApplicationUser user = user(ExperienceLevel.INTERMEDIATE);
        ForecastState state = new ForecastState(50, 50); // tsb=0
        List<Integer> hist = history(28, 50);

        LoadDistributionDto dist = forecaster.forecastLoad(user, LocalDate.now(), WorkoutType.REST_DAY, state, hist);

        assertAll(
                () -> assertEquals(0.0, dist.getMean(), 1e-9),
                () -> assertEquals(0.0, dist.getP10(), 1e-9),
                () -> assertEquals(0.0, dist.getP50(), 1e-9),
                () -> assertEquals(0.0, dist.getP90(), 1e-9),
                () -> assertTrue(dist.getStd() >= 0.0)
        );
    }

    @Test
    void quantiles_areOrdered_andNonNegative() {
        ApplicationUser user = user(ExperienceLevel.INTERMEDIATE);
        ForecastState state = new ForecastState(60, 55); // tsb=5
        List<Integer> hist = history(28, 45);

        LoadDistributionDto dist = forecaster.forecastLoad(user, LocalDate.now(), WorkoutType.TEMPO_RUN, state, hist);

        assertAll(
                () -> assertTrue(dist.getP10() <= dist.getP50(), "p10 must be <= p50"),
                () -> assertTrue(dist.getP50() <= dist.getP90(), "p50 must be <= p90"),
                () -> assertTrue(dist.getP10() >= 0, "p10 must be >= 0"),
                () -> assertTrue(dist.getP50() >= 0, "p50 must be >= 0"),
                () -> assertTrue(dist.getP90() >= 0, "p90 must be >= 0"),
                () -> assertTrue(dist.getStd() > 0, "std must be > 0")
        );
    }

    @Test
    void intensityOrdering_sameHistoryAndState() {
        ApplicationUser user = user(ExperienceLevel.INTERMEDIATE);
        ForecastState state = new ForecastState(60, 60); // tsb=0
        List<Integer> hist = history(28, 50);

        double easy = mean(user, WorkoutType.EASY_RUN, state, hist);
        double tempo = mean(user, WorkoutType.TEMPO_RUN, state, hist);
        double intervals = mean(user, WorkoutType.INTERVAL_RUN, state, hist);
        double longRun = mean(user, WorkoutType.LONG_RUN, state, hist);

        double mobility = mean(user, WorkoutType.MOBILITY, state, hist);
        double gym = mean(user, WorkoutType.GYM_PREHAB, state, hist);
        double rest = mean(user, WorkoutType.REST_DAY, state, hist);

        assertAll(
                () -> assertTrue(intervals > tempo, "intervals should be > tempo"),
                () -> assertTrue(tempo > easy, "tempo should be > easy"),
                () -> assertTrue(longRun > easy, "long run should be > easy"),
                () -> assertTrue(easy > gym, "easy should be > gym"),
                () -> assertTrue(gym > mobility, "gym should be > mobility"),
                () -> assertTrue(mobility > rest, "mobility should be > rest"),
                () -> assertEquals(0.0, rest, 1e-9)
        );
    }

    @Test
    void fatiguePenalty_reducesMean_whenTsbVeryNegative() {
        ApplicationUser user = user(ExperienceLevel.INTERMEDIATE);
        List<Integer> hist = history(28, 60);

        ForecastState fresh = new ForecastState(60, 60);       // tsb=0
        ForecastState fatigued = new ForecastState(40, 70);    // tsb=-30 (very negative)

        double meanFresh = mean(user, WorkoutType.TEMPO_RUN, fresh, hist);
        double meanFatigued = mean(user, WorkoutType.TEMPO_RUN, fatigued, hist);

        assertTrue(meanFatigued < meanFresh, "mean should be reduced when TSB is very negative");
    }

    @Test
    void coldStart_hasHigherRelativeUncertaintyThanWarmStart() {
        ApplicationUser user = user(ExperienceLevel.CASUAL);
        ForecastState state = new ForecastState(50, 50);

        // cold start: < 5 non-zero days
        List<Integer> cold = List.of(0, 0, 20, 0, 0, 0, 0);

        // warm: many non-zero days
        List<Integer> warm = history(28, 40);

        LoadDistributionDto coldDist = forecaster.forecastLoad(user, LocalDate.now(), WorkoutType.EASY_RUN, state, cold);
        LoadDistributionDto warmDist = forecaster.forecastLoad(user, LocalDate.now(), WorkoutType.EASY_RUN, state, warm);

        double coldCv = coldDist.getStd() / coldDist.getMean();
        double warmCv = warmDist.getStd() / warmDist.getMean();

        assertAll(
                () -> assertTrue(coldDist.getMean() > 0),
                () -> assertTrue(warmDist.getMean() > 0),
                () -> assertTrue(coldCv > warmCv, () -> "Expected cold-start CV > warm-start CV but got cold=" + coldCv + " warm=" + warmCv)
        );
    }


    @Test
    void emptyHistory_usesSafeDefaultBaseline() {
        ApplicationUser user = user(ExperienceLevel.INTERMEDIATE);
        ForecastState state = new ForecastState(50, 50);

        LoadDistributionDto dist = forecaster.forecastLoad(user, LocalDate.now(), WorkoutType.EASY_RUN, state, List.of());

        assertAll(
                () -> assertTrue(dist.getMean() > 0, "with empty history, EASY_RUN mean should still be > 0"),
                () -> assertTrue(dist.getP90() >= dist.getP50())
        );
    }

    @Test
    void mostlyZeroHistory_fallsBackToNonZeroMean() {
        ApplicationUser user = user(ExperienceLevel.INTERMEDIATE);
        ForecastState state = new ForecastState(50, 50);

        // median would be 0, should fall back to mean of non-zero days (here: 30)
        List<Integer> hist = List.of(0, 0, 0, 0, 30, 0, 0, 0);

        LoadDistributionDto dist = forecaster.forecastLoad(user, LocalDate.now(), WorkoutType.EASY_RUN, state, hist);

        assertTrue(dist.getMean() >= 15, "mean should not collapse to ~0 when there is some non-zero history");
    }

    @Test
    void experienceFactor_onlyAffectsColdStart() {
        ForecastState state = new ForecastState(50, 50);

        List<Integer> cold = List.of(0, 0, 20, 0, 0, 0, 0); // cold start
        ApplicationUser beginner = user(ExperienceLevel.BEGINNER);
        ApplicationUser advanced = user(ExperienceLevel.ADVANCED);

        double beginnerCold = mean(beginner, WorkoutType.EASY_RUN, state, cold);
        double advancedCold = mean(advanced, WorkoutType.EASY_RUN, state, cold);


        // warm start: experience factor should be neutral (1.0)
        List<Integer> warm = history(28, 50);
        double beginnerWarm = mean(beginner, WorkoutType.EASY_RUN, state, warm);
        double advancedWarm = mean(advanced, WorkoutType.EASY_RUN, state, warm);

        assertAll(
                () -> assertTrue(beginnerCold < advancedCold, "in cold start, advanced should get higher mean than beginner"),
                () -> assertEquals(beginnerWarm, advancedWarm, 1e-6, "in warm start, experience should not change mean")
        );
    }

    // ---------- helpers ----------

    private double mean(ApplicationUser user, WorkoutType w, ForecastState state, List<Integer> hist) {
        return forecaster.forecastLoad(user, LocalDate.now(), w, state, hist).getMean();
    }

    private ApplicationUser user(ExperienceLevel exp) {
        ApplicationUser u = new ApplicationUser();
        u.setExperienceLevel(exp);
        return u;
    }

    private List<Integer> history(int days, int load) {
        List<Integer> out = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            out.add(load);
        }
        return out;
    }
}
