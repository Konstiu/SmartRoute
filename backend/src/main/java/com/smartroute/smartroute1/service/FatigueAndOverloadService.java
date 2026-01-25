package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.entity.ApplicationUser;

import java.time.LocalDate;
import java.util.List;

/**
 * Service to compute Chronic Training Load (CTL), Acute Training Load (ATL) and
 * Training Stress Balance (TSB) from daily {@code FitnessScore} values.
 *
 * <p>
 * The implementation follows the Training Stress Balance (TSB) model, which
 * is a simplified fitness–fatigue impulse-response model. On each day {@code t},
 * the daily training load {@code L_t} is given by the {@code FitnessScore} for that
 * day. CTL and ATL are modeled as exponential moving averages of this load:
 * </p>
 *
 * <pre>
 * CTL_t = CTL_{t-1} + (1 / τ_c) * (L_t - CTL_{t-1})
 * ATL_t = ATL_{t-1} + (1 / τ_a) * (L_t - ATL_{t-1})
 *
 * with τ_c = 42 days (chronic / "fitness")
 *      τ_a = 7  days (acute / "fatigue")
 *
 * TSB_t = CTL_t - ATL_t
 * </pre>
 *
 * <p>
 * Intuitively:
 * </p>
 * <ul>
 *   <li>CTL represents long-term training load (a proxy for "fitness").</li>
 *   <li>ATL represents short-term training load (a proxy for "fatigue").</li>
 *   <li>TSB is the difference CTL − ATL and is often interpreted as
 *       "form" or "freshness": positive values suggest the athlete is
 *       relatively fresh, while strongly negative values indicate
 *       accumulated fatigue and possible overload.</li>
 * </ul>
 *
 * <p>
 * This service operates on per-day aggregates; it assumes that daily
 * {@code FitnessScore} values are already computed elsewhere.
 * </p>
 *
 * <p>
 * Conceptually, this follows the Training Stress Balance model as described in:
 * </p>
 *  <li>
 *     Fellrnr, “Modeling Human Performance”
 *     <a href="https://fellrnr.com/wiki/Modeling_Human_Performance">
 *       https://fellrnr.com/wiki/Modeling_Human_Performance
 *     </a>
 *   </li>
 */
public interface FatigueAndOverloadService {
    /**
     * Returns the athlete's current Chronic Training Load (CTL), i.e. the
     * long-term exponentially weighted average of daily training load
     * (FitnessScore).
     *
     * <p>
     * Semantically, this is the CTL value for the most recent day for which
     * the user has data (not necessarily "today" if there was no recent
     * training).
     * </p>
     *
     * @param user the athlete
     * @return the current CTL value for the user
     */
    double currentCtl(ApplicationUser user);

    /**
     * Returns the athlete's current Acute Training Load (ATL), i.e. the
     * short-term exponentially weighted average of daily training load.
     *
     * <p>
     * Semantically, this is the ATL value for the most recent day for which
     * the user has data.
     * </p>
     *
     * @param user the athlete
     * @return the current ATL value for the user
     */
    double currentAtl(ApplicationUser user);

    /**
     * Returns the athlete's current Training Stress Balance (TSB), defined as
     * {@code TSB = CTL - ATL}.
     *
     * <p>
     * Positive TSB values typically indicate that the athlete is relatively
     * fresh or recovered, while negative values indicate accumulated fatigue
     * and can be a sign of overload, especially if strongly negative for
     * extended periods.
     * </p>
     *
     * @param user the athlete
     * @return the current TSB value for the user
     */
    double currentTsb(ApplicationUser user);

    /**
     * Returns the Chronic Training Load (CTL) for the given user on the
     * specified date.
     *
     * <p>
     * The CTL value on {@code date} is computed from all available daily
     * loads up to and including that date, using the exponential update
     * equation with time constant {@code τ_c = 42} days.
     * </p>
     *
     * @param user the athlete
     * @param date calendar date for which CTL should be returned
     * @return CTL on the given date
     * @throws IllegalArgumentException if no history exists on or before {@code date}
     */
    double ctlOn(ApplicationUser user, LocalDate date);

    /**
     * Returns the Acute Training Load (ATL) for the given user on the
     * specified date.
     *
     * <p>
     * The ATL value on {@code date} is computed from all available daily
     * loads up to and including that date, using the exponential update
     * equation with time constant {@code τ_a = 7} days.
     * </p>
     *
     * @param user the athlete
     * @param date calendar date for which ATL should be returned
     * @return ATL on the given date
     * @throws IllegalArgumentException if no history exists on or before {@code date}
     */
    double atlOn(ApplicationUser user, LocalDate date);

    /**
     * Returns the Training Stress Balance (TSB) for the given user on the
     * specified date, defined as {@code TSB(date) = CTL(date) - ATL(date)}.
     *
     * @param user the athlete
     * @param date calendar date for which TSB should be returned
     * @return TSB on the given date
     * @throws IllegalArgumentException if no history exists on or before {@code date}
     */
    double tsbOn(ApplicationUser user, LocalDate date);

    /**
     * Returns the CTL values for the last {@code days} days for the given user.
     *
     * <p>The list is ordered chronologically (oldest first, newest last). If the
     * user has fewer than {@code days} days of history, all available days are
     * returned.
     * </p>
     *
     * @param user the athlete
     * @param days number of days to include, counting backwards from the most recent day
     * @return list of CTL values for the requested period, in chronological order
     * @throws IllegalArgumentException if {@code days <= 0}
     */
    List<Double> getCtlForLastDays(ApplicationUser user, int days);

    /**
     * Returns the ATL values for the last {@code days} days for the given user.
     *
     * <p>
     * The list is ordered chronologically (oldest first, newest last). If the
     * user has fewer than {@code days} days of history, all available days are
     * returned.
     * </p>
     *
     * @param user the athlete
     * @param days number of days to include, counting backwards from the most recent day
     * @return list of ATL values for the requested period, in chronological order
     * @throws IllegalArgumentException if {@code days <= 0}
     */
    List<Double> getAtlForLastDays(ApplicationUser user, int days);

    /**
     * Returns the TSB values for the last {@code days} days for the given user,
     * where each value is {@code TSB = CTL - ATL} on that day.
     *
     * <p>
     * The list is ordered chronologically (oldest first, newest last). If the
     * user has fewer than {@code days} days of history, all available days are
     * returned.
     * </p>
     *
     * @param user the athlete
     * @param days number of days to include, counting backwards from the most recent day
     * @return list of TSB values for the requested period, in chronological order
     * @throws IllegalArgumentException if {@code days <= 0}
     */
    List<Double> getTsbForLastDays(ApplicationUser user, int days);

    /**
     * Returns the full CTL history for the given user, from the first available
     * day up to the most recent day.
     *
     * <p>
     * The result is ordered chronologically (oldest first, newest last) and
     * uses the same exponential model and parameters as described in the class
     * documentation.
     * </p>
     *
     * @param user the athlete
     * @return list of CTL values for all available days, in chronological order
     */
    List<Double> getCtlHistory(ApplicationUser user);

    /**
     * Returns the full ATL history for the given user, from the first available
     * day up to the most recent day.
     *
     * <p>
     * The result is ordered chronologically (oldest first, newest last) and
     * uses the same exponential model and parameters as described in the class
     * documentation.
     * </p>
     *
     * @param user the athlete
     * @return list of ATL values for all available days, in chronological order
     */
    List<Double> getAtlHistory(ApplicationUser user);

    /**
     * Returns the full TSB history for the given user, from the first available
     * day up to the most recent day, where each value is
     * {@code TSB = CTL - ATL} for that day.
     *
     * <p>
     * The result is ordered chronologically (oldest first, newest last).
     * </p>
     *
     * @param user the athlete
     * @return list of TSB values for all available days, in chronological order
     */
    List<Double> getTsbHistory(ApplicationUser user);


}
