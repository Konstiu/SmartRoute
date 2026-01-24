package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.LoadDistributionDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.service.LoadForecaster;
import com.smartroute.smartroute1.util.ForecastState;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class LoadForecasterService implements LoadForecaster {

    // Tuneable knobs
    private static final int WINDOW_DAYS = 42;
    private static final int COLD_START_MIN_NONZERO = 5;

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

        RecentStats stats = recentStats(recentDailyLoads, WINDOW_DAYS);
        boolean coldStart = stats.nonZero < COLD_START_MIN_NONZERO;

        // ---------- Baseline ----------
        // Use only non-zero days. Blend median and mean (median is robust; mean captures "true volume").
        double baseline = coldStart
                ? coldStartBaseline(user == null ? null : user.getExperienceLevel())
                : blendedBaseline(stats);

        // ---------- Workout multipliers ----------
        // Less extreme than before; long/interval shouldn’t be 1.6/1.45 unless baseline is "easy day".
        double multiplier = switch (plannedWorkout) {
            case MOBILITY -> 0.18;
            case GYM_PREHAB -> 0.40;
            case EASY_RUN -> 1.00;
            case TEMPO_RUN -> 1.18;
            case INTERVAL_RUN -> 1.28;
            case LONG_RUN -> 1.35;
            default -> 1.00;
        };

        // ---------- Fatigue / ramp adjustment ----------
        double fatigueFactor = fatigueFactorFromTsb(state == null ? 0.0 : state.tsb());

        // Optional ramp check: if your ForecastState has ctl()/atl(), this helps avoid crazy weeks.
        // If not available in your ForecastState, delete this block.
        try {
            double ctl = state == null ? 0.0 : state.ctl();
            double atl = state == null ? 0.0 : state.atl();
            if (ctl > 1e-6 && atl > 1e-6) {
                double ramp = atl / ctl;
                if (ramp > 1.8) {
                    fatigueFactor *= 0.78;
                } else if (ramp > 1.4) {
                    fatigueFactor *= 0.88;
                }
            }
        } catch (Exception ignored) {
            // ignore if ctl()/atl() don't exist
        }

        // Cold-start experience scaling: keep it mild; most of the difference should be in templates anyway.
        double expFactor = coldStart ? coldStartExperienceFactor(user == null ? null : user.getExperienceLevel()) : 1.0;

        double mean = baseline * multiplier * fatigueFactor * expFactor;

        // ---------- Guardrails ----------
        mean = clampMean(plannedWorkout, user == null ? null : user.getExperienceLevel(), mean);

        // ---------- Uncertainty ----------
        // Use history CV when available; otherwise default.
        double cv = stats.cv;
        double baseStdFrac = coldStart ? 0.38 : 0.22;
        double histFrac = (stats.nonZero >= 8) ? clamp(0.16, 0.45, cv) : baseStdFrac;

        // intensity adds uncertainty (execution varies more)
        double intensityBoost = switch (plannedWorkout) {
            case INTERVAL_RUN -> 0.10;
            case TEMPO_RUN -> 0.06;
            case LONG_RUN -> 0.08;
            default -> 0.00;
        };

        double stdFrac = clamp(0.14, 0.60, histFrac + intensityBoost);
        double std = Math.max(3.0, mean * stdFrac);

        // ---------- Quantiles ----------
        // Lognormal is a better fit for non-negative load.
        Quantiles q = lognormalQuantiles(mean, std);

        return new LoadDistributionDto(q.p10, q.p50, q.p90, mean, std);
    }

    // ---------------- helpers ----------------

    private static double blendedBaseline(RecentStats s) {
        // Slightly bias toward median to be robust, but keep mean in play.
        double b = 0.65 * s.median + 0.35 * s.mean;
        return clamp(20.0, 220.0, b);
    }

    private static double coldStartBaseline(ExperienceLevel exp) {
        // “Reasonable daily load” defaults if we barely know anything.
        if (exp == null) {
            exp = ExperienceLevel.INTERMEDIATE;
        }
        return switch (exp) {
            case BEGINNER -> 24.0;
            case CASUAL -> 30.0;
            case INTERMEDIATE -> 38.0;
            case ADVANCED -> 48.0;
            case COMPETITIVE_ATHLETE -> 58.0;
        };
    }

    private static double coldStartExperienceFactor(ExperienceLevel exp) {
        if (exp == null) {
            exp = ExperienceLevel.INTERMEDIATE;
        }
        return switch (exp) {
            case BEGINNER -> 0.90;
            case CASUAL -> 0.95;
            case INTERMEDIATE -> 1.00;
            case ADVANCED -> 1.03;
            case COMPETITIVE_ATHLETE -> 1.05;
        };
    }

    private static double fatigueFactorFromTsb(double tsb) {
        // Smooth-ish curve:
        // very negative TSB -> scale down; slightly positive -> tiny boost
        if (tsb <= -25) {
            return 0.68;
        }
        if (tsb <= -10) {
            // -25..-10 -> 0.68..0.90
            double t = (tsb + 25.0) / 15.0;
            return 0.68 + t * (0.90 - 0.68);
        }
        if (tsb <= 5) {
            return 1.00;
        }
        if (tsb >= 20) {
            return 1.08;
        }
        // 5..20 -> 1.00..1.08
        double t = (tsb - 5.0) / 15.0;
        return 1.00 + t * 0.08;
    }

    private static double clampMean(WorkoutType wt, ExperienceLevel exp, double mean) {
        if (exp == null) {
            exp = ExperienceLevel.INTERMEDIATE;
        }

        // caps keep things sane for beginners even if something slips through gating
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
                    case BEGINNER -> 0;   // effectively disallow
                    case CASUAL -> 70;
                    case INTERMEDIATE -> 95;
                    case ADVANCED -> 115;
                    case COMPETITIVE_ATHLETE -> 135;
                };
            }
            case INTERVAL_RUN -> {
                floor = 18;
                cap = switch (exp) {
                    case BEGINNER, CASUAL -> 0; // effectively disallow
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
            // if disallowed by experience, fall back to EASY_RUN-ish behavior
            return clamp(10, (exp == ExperienceLevel.BEGINNER ? 55 : 90), mean * 0.80);
        }

        return clamp(floor, cap, mean);
    }

    private static RecentStats recentStats(List<Integer> loads, int window) {
        if (loads == null || loads.isEmpty()) {
            return new RecentStats(0, 0, 0, 0, 0);
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
            return new RecentStats(0, 0, 0, 0, 0);
        }

        Collections.sort(nz);

        double mean = nz.stream().mapToDouble(x -> x).average().orElse(0.0);
        double median = (n % 2 == 1) ? nz.get(n / 2) : (nz.get(n / 2 - 1) + nz.get(n / 2)) / 2.0;

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

        return new RecentStats(mean, median, std, cv, n);
    }

    private static Quantiles lognormalQuantiles(double mean, double std) {
        // if mean is tiny or std invalid, fallback to "almost deterministic"
        if (mean <= 1e-6) {
            return new Quantiles(0, 0, 0);
        }
        if (std <= 1e-6) {
            return new Quantiles(mean, mean, mean);
        }

        // lognormal params from mean/std
        double variance = std * std;
        double phi = Math.sqrt(variance + mean * mean);
        double sigma2 = Math.log((phi * phi) / (mean * mean));
        double sigma = Math.sqrt(Math.max(0.0, sigma2));
        double mu = Math.log(mean) - 0.5 * sigma2;

        // z for 10/50/90
        double z10 = -1.2815515655446004;
        double z50 = 0.0;
        double z90 = 1.2815515655446004;

        double p10 = Math.exp(mu + sigma * z10);
        double p50 = Math.exp(mu + sigma * z50);
        double p90 = Math.exp(mu + sigma * z90);

        // safety: ensure ordering
        if (p10 > p50) {
            p10 = p50;
        }
        if (p50 > p90) {
            p90 = p50;
        }

        return new Quantiles(p10, p50, p90);
    }

    private static double clamp(double lo, double hi, double x) {
        return Math.max(lo, Math.min(hi, x));
    }

    private record RecentStats(double mean, double median, double std, double cv, int nonZero) {}

    private record Quantiles(double p10, double p50, double p90) {}
}
