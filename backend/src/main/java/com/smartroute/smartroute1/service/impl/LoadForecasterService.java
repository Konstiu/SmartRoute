package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.LoadDistributionDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.service.LoadForecaster;
import com.smartroute.smartroute1.entity.enums.ForecastState;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class LoadForecasterService implements LoadForecaster {

    private static final int WINDOW_DAYS = 42;
    private static final int COLD_START_MIN_NONZERO = 5;

    // z-scores for normal quantiles
    private static final double Z10 = -1.2815515655446004;
    private static final double Z90 =  1.2815515655446004;

    @Override
    public LoadDistributionDto forecastLoad(
            ApplicationUser user,
            LocalDate date,
            WorkoutType plannedWorkout,
            ForecastState state,
            List<Integer> recentDailyLoads
    ) {
        if (plannedWorkout == null || plannedWorkout == WorkoutType.REST_DAY) {
            return new LoadDistributionDto(0, 0, 0, 0, 0);
        }

        ExperienceLevel exp = (user == null || user.getExperienceLevel() == null)
                ? ExperienceLevel.INTERMEDIATE
                : user.getExperienceLevel();

        // --- recent stats (non-zero days only) ---
        RecentStats stats = recentStatsNonZero(recentDailyLoads, WINDOW_DAYS);
        boolean coldStart = stats.nonZero < COLD_START_MIN_NONZERO;

        // --- baseline ---
        double baseline = coldStart
                ? coldStartBaseline(exp)
                : blendedBaseline(stats);

        // For harder sessions, lean a bit toward the athlete's upper-typical day load.
        double baselineHard = coldStart
                ? baseline
                : clamp(22.0, 260.0, 0.55 * baseline + 0.45 * stats.p75);

        double multiplier = workoutMultiplier(plannedWorkout);

        double baseForWorkout = isHard(plannedWorkout) ? baselineHard : baseline;

        // --- fatigue / ramp adjustment ---
        double tsb = (state == null) ? 0.0 : safeTsb(state);
        double fatigueFactor = fatigueFactorFromTsb(tsb);

        // Ramp factor (if ctl/atl available): reduce load a bit when ATL/CTL is very high.
        fatigueFactor *= rampFactor(state);

        // Cold-start mild experience factor
        double expFactor = coldStart ? coldStartExperienceFactor(exp) : 1.0;

        double mean = baseForWorkout * multiplier * fatigueFactor * expFactor;

        // --- guardrails by workout + experience ---
        mean = clampMean(plannedWorkout, exp, mean);

        // --- uncertainty ---
        double cv = stats.cv;
        double baseStdFrac = coldStart ? 0.38 : 0.22;

        // If we have enough non-zero points, let history drive stdFrac; else default.
        double histStdFrac = (stats.nonZero >= 8)
                ? clamp(0.16, 0.45, cv)
                : baseStdFrac;

        // Harder sessions vary more in execution.
        double intensityBoost = switch (plannedWorkout) {
            case INTERVAL_RUN -> 0.10;
            case TEMPO_RUN -> 0.06;
            case LONG_RUN -> 0.08;
            default -> 0.00;
        };

        double stdFrac = clamp(0.14, 0.60, histStdFrac + intensityBoost);

        // Slightly lower absolute floor for mobility/gym; keep runs at >= 3.
        double stdFloor = (plannedWorkout == WorkoutType.MOBILITY) ? 1.0 : 3.0;
        double std = Math.max(stdFloor, mean * stdFrac);

        // --- quantiles consistent with mean/std (normal-ish, clamped non-negative) ---
        Quantiles q = normalNonNegativeQuantiles(mean, std);

        return new LoadDistributionDto(q.p10, q.p50, q.p90, mean, std);
    }

    // ============================================================
    // Baseline + multipliers
    // ============================================================

    private static double workoutMultiplier(WorkoutType plannedWorkout) {
        return switch (plannedWorkout) {
            case MOBILITY -> 0.18;
            case GYM_PREHAB -> 0.40;
            case EASY_RUN -> 1.00;
            case TEMPO_RUN -> 1.18;
            case INTERVAL_RUN -> 1.28;
            case LONG_RUN -> 1.35;
            default -> 1.00;
        };
    }

    private static double blendedBaseline(RecentStats s) {
        double b = 0.65 * s.median + 0.35 * s.mean;
        return clamp(20.0, 220.0, b);
    }

    private static double coldStartBaseline(ExperienceLevel exp) {
        return switch (exp == null ? ExperienceLevel.INTERMEDIATE : exp) {
            case BEGINNER -> 24.0;
            case CASUAL -> 30.0;
            case INTERMEDIATE -> 38.0;
            case ADVANCED -> 48.0;
            case COMPETITIVE_ATHLETE -> 58.0;
        };
    }

    private static double coldStartExperienceFactor(ExperienceLevel exp) {
        return switch (exp == null ? ExperienceLevel.INTERMEDIATE : exp) {
            case BEGINNER -> 0.90;
            case CASUAL -> 0.95;
            case INTERMEDIATE -> 1.00;
            case ADVANCED -> 1.03;
            case COMPETITIVE_ATHLETE -> 1.05;
        };
    }

    // ============================================================
    // Fatigue + ramp
    // ============================================================

    private static double fatigueFactorFromTsb(double tsb) {
        // Smooth-ish curve:
        // very negative TSB -> scale down; slightly positive -> tiny boost
        if (tsb <= -25) {
            return 0.68;
        }
        if (tsb <= -10) {
            double t = (tsb + 25.0) / 15.0;
            return 0.68 + t * (0.90 - 0.68);
        }
        if (tsb <= 5) {
            return 1.00;
        }
        if (tsb >= 20) {
            return 1.08;
        }
        double t = (tsb - 5.0) / 15.0;
        return 1.00 + t * 0.08;
    }

    private static double rampFactor(ForecastState state) {
        if (state == null) {
            return 1.0;
        }

        try {
            double ctl = state.ctl();
            double atl = state.atl();
            if (ctl <= 1e-6 || atl <= 1e-6) {
                return 1.0;
            }

            double ramp = atl / ctl;
            if (ramp > 1.8) {
                return 0.78;
            }
            if (ramp > 1.4) {
                return 0.88;
            }
            return 1.0;
        } catch (Exception ignored) {
            return 1.0;
        }
    }

    private static double safeTsb(ForecastState state) {
        try {
            return state.tsb();
        } catch (Exception e) {
            return 0.0;
        }
    }

    // ============================================================
    // Guardrails
    // ============================================================

    private static double clampMean(WorkoutType wt, ExperienceLevel exp, double mean) {
        exp = (exp == null) ? ExperienceLevel.INTERMEDIATE : exp;

        double cap;
        double floor;

        switch (wt) {
            case MOBILITY -> {
                floor = 2;
                cap = 18;
            }
            case GYM_PREHAB -> {
                floor = 6;
                cap = (exp == ExperienceLevel.BEGINNER ? 28 : 45);
            }
            case EASY_RUN -> {
                floor = 10;
                cap = switch (exp) {
                    case BEGINNER -> 55;
                    case CASUAL -> 70;
                    case INTERMEDIATE -> 90;
                    case ADVANCED -> 110;
                    case COMPETITIVE_ATHLETE -> 130;
                };
            }
            case TEMPO_RUN -> {
                floor = 15;
                cap = switch (exp) {
                    case BEGINNER -> 0;
                    case CASUAL -> 70;
                    case INTERMEDIATE -> 95;
                    case ADVANCED -> 115;
                    case COMPETITIVE_ATHLETE -> 135;
                };
            }
            case INTERVAL_RUN -> {
                floor = 18;
                cap = switch (exp) {
                    case BEGINNER, CASUAL -> 0;
                    case INTERMEDIATE -> 100;
                    case ADVANCED -> 125;
                    case COMPETITIVE_ATHLETE -> 145;
                };
            }
            case LONG_RUN -> {
                floor = 22;
                cap = switch (exp) {
                    case BEGINNER -> 70;
                    case CASUAL -> 95;
                    case INTERMEDIATE -> 125;
                    case ADVANCED -> 155;
                    case COMPETITIVE_ATHLETE -> 175;
                };
            }
            default -> {
                floor = 0;
                cap = 220;
            }
        }

        if (cap <= 0) {
            double easyCap = (exp == ExperienceLevel.BEGINNER) ? 55 : 90;
            return clamp(10, easyCap, mean * 0.80);
        }

        return clamp(floor, cap, mean);
    }

    private static boolean isHard(WorkoutType wt) {
        return wt == WorkoutType.INTERVAL_RUN
                || wt == WorkoutType.TEMPO_RUN
                || wt == WorkoutType.LONG_RUN;
    }

    // ============================================================
    // Quantiles consistent with mean/std (normal-ish + non-negativity)
    // ============================================================

    private static Quantiles normalNonNegativeQuantiles(double mean, double std) {
        if (mean <= 1e-9) {
            return new Quantiles(0, 0, 0);
        }
        if (std <= 1e-9) {
            return new Quantiles(mean, mean, mean);
        }

        double p50 = Math.max(0.0, mean);
        double p10 = Math.max(0.0, mean + Z10 * std);
        double p90 = Math.max(0.0, mean + Z90 * std);

        // enforce ordering (clamping can break it when mean is small)
        if (p10 > p50) {
            p10 = p50;
        }
        if (p50 > p90) {
            p90 = p50;
        }

        return new Quantiles(p10, p50, p90);
    }

    // ============================================================
    // Recent stats on non-zero loads only
    // ============================================================

    private static RecentStats recentStatsNonZero(List<Integer> loads, int window) {
        if (loads == null || loads.isEmpty()) {
            return RecentStats.empty();
        }

        int from = Math.max(0, loads.size() - window);
        List<Integer> slice = loads.subList(from, loads.size());

        List<Double> nz = new ArrayList<>();
        for (Integer x : slice) {
            if (x != null && x > 0) {
                nz.add(x.doubleValue());
            }
        }

        int n = nz.size();
        if (n == 0) {
            return RecentStats.empty();
        }

        Collections.sort(nz);

        double mean = nz.stream().mapToDouble(v -> v).average().orElse(0.0);
        double median = (n % 2 == 1)
                ? nz.get(n / 2)
                : (nz.get(n / 2 - 1) + nz.get(n / 2)) / 2.0;

        double p75 = quantileSorted(nz, 0.75);

        double std = 0.0;
        if (n >= 2) {
            double var = 0.0;
            for (double v : nz) {
                double d = v - mean;
                var += d * d;
            }
            var /= (n - 1);
            std = Math.sqrt(var);
        }

        double cv = std / Math.max(1.0, mean);

        return new RecentStats(mean, median, std, cv, p75, n);
    }

    private static double quantileSorted(List<Double> sorted, double q) {
        int n = sorted.size();
        if (n == 1) {
            return sorted.get(0);
        }

        double pos = q * (n - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);

        if (lo == hi) {
            return sorted.get(lo);
        }

        double w = pos - lo;
        return sorted.get(lo) * (1 - w) + sorted.get(hi) * w;
    }

    // ============================================================
    // tiny utils
    // ============================================================

    private static double clamp(double lo, double hi, double x) {
        return Math.max(lo, Math.min(hi, x));
    }

    private record RecentStats(double mean, double median, double std, double cv, double p75, int nonZero) {
        static RecentStats empty() {
            return new RecentStats(0, 0, 0, 0, 0, 0);
        }
    }

    private record Quantiles(double p10, double p50, double p90) {}
}
