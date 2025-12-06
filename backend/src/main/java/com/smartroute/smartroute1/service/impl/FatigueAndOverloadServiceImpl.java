package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.exception.InsufficientTrainingDataException;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.service.FatigueAndOverloadService;
import com.smartroute.smartroute1.service.FitnessScoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class FatigueAndOverloadServiceImpl implements FatigueAndOverloadService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final double CTL_TAU = 42.0;
    private static final double ATL_TAU = 7.0;

    private static final double CTL_ALPHA = 1.0 / CTL_TAU;
    private static final double ATL_ALPHA = 1.0 / ATL_TAU;

    private final ActivityRepository activityRepository;

    private final FitnessScoreService fitnessScoreService;

    public FatigueAndOverloadServiceImpl(ActivityRepository activityRepository, FitnessScoreService fitnessScoreService) {
        this.activityRepository = activityRepository;
        this.fitnessScoreService = fitnessScoreService;
    }

    /**
     * Single day's input: date + FitnessScore (daily load L_t).
     */
    private record DailyLoad(LocalDate date, double fitnessScore) {
    }

    /**
     * Single day's state: date + CTL + ATL.
     * TSB is derived as CTL - ATL.
     */
    private record LoadState(LocalDate date, double ctl, double atl) {
        double tsb() {
            return ctl - atl;
        }
    }


    @Override
    public double currentCtl(ApplicationUser user) {
        LoadState latest = latestState(user);
        return latest.ctl();
    }

    @Override
    public double currentAtl(ApplicationUser user) {
        LoadState latest = latestState(user);
        return latest.atl();
    }

    @Override
    public double currentTsb(ApplicationUser user) {
        LoadState latest = latestState(user);
        return latest.tsb();
    }

    @Override
    public double ctlOn(ApplicationUser user, LocalDate date) {
        return stateOn(user, date).ctl();
    }

    @Override
    public double atlOn(ApplicationUser user, LocalDate date) {
        return stateOn(user, date).atl();
    }

    @Override
    public double tsbOn(ApplicationUser user, LocalDate date) {
        return stateOn(user, date).tsb();
    }

    @Override
    public List<Double> getCtlForLastDays(ApplicationUser user, int days) {
        return tailStates(user, days).stream()
                .map(LoadState::ctl)
                .toList();
    }

    @Override
    public List<Double> getAtlForLastDays(ApplicationUser user, int days) {
        return tailStates(user, days).stream()
                .map(LoadState::atl)
                .toList();
    }

    @Override
    public List<Double> getTsbForLastDays(ApplicationUser user, int days) {
        return tailStates(user, days).stream()
                .map(LoadState::tsb)
                .toList();
    }

    @Override
    public List<Double> getCtlHistory(ApplicationUser user) {
        return computeCurve(user).stream()
                .map(LoadState::ctl)
                .toList();
    }

    @Override
    public List<Double> getAtlHistory(ApplicationUser user) {
        return computeCurve(user).stream()
                .map(LoadState::atl)
                .toList();
    }

    @Override
    public List<Double> getTsbHistory(ApplicationUser user) {
        return computeCurve(user).stream()
                .map(LoadState::tsb)
                .toList();
    }


    /**
     * Returns the latest state (CTL/ATL/TSB) for the user, i.e. for the most
     * recent day for which a FitnessScore is available.
     */
    private LoadState latestState(ApplicationUser user) {
        List<LoadState> curve = computeCurve(user);
        if (curve.isEmpty()) {
            throw new InsufficientTrainingDataException("No training history available for user " + user.getId());
        }
        return curve.get(curve.size() - 1);
    }

    /**
     * Computes the full CTL/ATL curve (and implicit TSB) for the given user.
     *
     * <p>Implementation details:
     * <ul>
     *     <li>Daily load L_t is taken from the FitnessScore for that date.</li>
     *     <li>Days are processed in chronological order.</li>
     *     <li>Initialization: CTL_1 = ATL_1 = L_1 (first available FitnessScore).</li>
     *     <li>Updates follow the EMA formulas:
     *         CTL_t = CTL_{t-1} + α_c (L_t - CTL_{t-1}),
     *         ATL_t = ATL_{t-1} + α_a (L_t - ATL_{t-1}).</li>
     *     <li>Only days with imported FitnessScore are used; missing days are
     *         not implicitly treated as zero-load rest days.</li>
     * </ul>
     */
    private List<LoadState> computeCurve(ApplicationUser user) {
        List<DailyLoad> loads = loadDailyFitnessScores(user);
        if (loads.isEmpty()) {
            LOGGER.debug("No daily loads for user {}", user.getId());
            return List.of();
        }

        loads = loads.stream()
                .sorted(Comparator.comparing(DailyLoad::date))
                .toList();

        List<LoadState> states = new ArrayList<>(loads.size());

        DailyLoad first = loads.get(0);
        double ctl = first.fitnessScore();
        double atl = first.fitnessScore();
        states.add(new LoadState(first.date(), ctl, atl));

        for (int i = 1; i < loads.size(); i++) {
            DailyLoad day = loads.get(i);
            double load = day.fitnessScore();

            ctl = ctl + CTL_ALPHA * (load - ctl);
            atl = atl + ATL_ALPHA * (load - atl);

            states.add(new LoadState(day.date(), ctl, atl));
        }

        return List.copyOf(states);
    }

    /**
     * Returns the state (CTL/ATL/TSB) on the last day &le; {@code date}.
     */
    private LoadState stateOn(ApplicationUser user, LocalDate date) {
        return computeCurve(user).stream()
                .filter(s -> !s.date().isAfter(date))
                .reduce((a, b) -> b) // take last state up to that date
                .orElseThrow(() -> new InsufficientTrainingDataException(
                        "No training history available on or before " + date + " for user " + user.getId()));
    }

    /**
     * Returns the last {@code days} states (oldest→newest), or all states if
     * fewer days are available.
     */
    private List<LoadState> tailStates(ApplicationUser user, int days) {
        if (days <= 0) {
            throw new IllegalArgumentException("days must be > 0");
        }
        List<LoadState> curve = computeCurve(user);
        if (curve.isEmpty()) {
            return List.of();
        }
        int size = curve.size();
        int fromIndex = Math.max(0, size - days);
        return curve.subList(fromIndex, size);
    }


    /**
     * Loads all daily FitnessScore values for the given user.
     *
     * @param user the athlete
     * @return list of daily loads (maybe unsorted; will be sorted upstream)
     */
    private List<DailyLoad> loadDailyFitnessScores(ApplicationUser user) {
        Activity first = !activityRepository.findAllByUserOrderByStartDateAsc(user).isEmpty() ? activityRepository.findAllByUserOrderByStartDateAsc(user).get(0) : null;
        Activity last = !activityRepository.findAllByUserOrderByStartDateDesc(user).isEmpty() ? activityRepository.findAllByUserOrderByStartDateDesc(user).get(0) : null;

        if (first == null || last == null) {
            return List.of();
        }

        LocalDate firstDay = first.getStartDate().atZone(ZoneOffset.systemDefault()).toLocalDate();
        LocalDate lastDay = last.getStartDate().atZone(ZoneOffset.systemDefault()).toLocalDate();

        List<DailyLoad> result = new ArrayList<>();

        for (LocalDate d = firstDay; !d.isAfter(lastDay); d = d.plusDays(1)) {
            int score = fitnessScoreService.calculateFitnessScore(
                    d.atStartOfDay(ZoneOffset.systemDefault()).toInstant(),
                    user
            );

            if (score > 0) {
                result.add(new DailyLoad(d, score));
            }
        }
        return result;
    }
}
