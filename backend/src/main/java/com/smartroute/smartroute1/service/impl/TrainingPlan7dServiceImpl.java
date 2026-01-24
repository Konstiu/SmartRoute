package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.CompactWeatherDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.LoadDistributionDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.PlannedDayDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlan7dDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.DailySummary;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlanDebugDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.TemplateScoreDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.DayDebugDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.PplDailyObs;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.FitUserModelRequest;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.FitUserModelResponse;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.JuliaDist;
import com.smartroute.smartroute1.endpoint.dto.GymWorkoutDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Injuries;
import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.entity.enums.BodyPart;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.DailyAggregationService;
import com.smartroute.smartroute1.service.FatigueAndOverloadService;
import com.smartroute.smartroute1.service.InjuryAwareTrainingService;
import com.smartroute.smartroute1.service.TrainingPlan7dService;
import com.smartroute.smartroute1.service.LoadForecaster;
import com.smartroute.smartroute1.service.ReadinessScoreService;
import com.smartroute.smartroute1.service.WeatherService;
import com.smartroute.smartroute1.service.DaySelectorService;
import com.smartroute.smartroute1.service.GymWorkoutSelectorService;
import com.smartroute.smartroute1.service.TrainingPlanStore;
import com.smartroute.smartroute1.service.RouteGenerationService;
import com.smartroute.smartroute1.service.UserModelStore;
import com.smartroute.smartroute1.util.ForecastState;
import com.smartroute.smartroute1.util.LoadConstraints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.smartroute.smartroute1.endpoint.dto.RouteDto;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Map;
import java.util.Optional;

@Service
public class TrainingPlan7dServiceImpl implements TrainingPlan7dService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Vienna");
    private static final Logger log = LoggerFactory.getLogger(TrainingPlan7dServiceImpl.class);

    private final UserRepository userRepository;
    private final DailyAggregationService dailyAggregationService;
    private final LoadForecaster loadForecaster;
    private final FatigueAndOverloadService fatigueAndOverloadService;
    private final Clock clock;
    private final InjuryAwareTrainingService injuryAwareTrainingService;
    private final ReadinessScoreService readinessScoreService;
    private final WeatherService weatherService;
    private final DaySelectorService daySelectorService;
    private final GymWorkoutSelectorService gymWorkoutSelectorService;
    private final RouteGenerationService routeGenerationService;
    private final TrainingPlanStore trainingPlanStore;
    private final UserModelStore userModelStore;

    @Autowired
    public TrainingPlan7dServiceImpl(UserRepository userRepository,
                                     DailyAggregationService dailyAggregationService,
                                     LoadForecaster loadForecaster,
                                     FatigueAndOverloadService fatigueAndOverloadService,
                                     InjuryAwareTrainingService injuryAwareTrainingService,
                                     ReadinessScoreService readinessScoreService,
                                     WeatherService weatherService,
                                     DaySelectorService daySelectorService,
                                     GymWorkoutSelectorService gymWorkoutSelectorService,
                                     RouteGenerationService routeGenerationService,
                                     TrainingPlanStore trainingPlanStore,
                                     UserModelStore userModelStore
    ) {
        this(userRepository, dailyAggregationService, loadForecaster, fatigueAndOverloadService,
                Clock.system(ZoneId.of("Europe/Vienna")), injuryAwareTrainingService, readinessScoreService,
                weatherService, daySelectorService, gymWorkoutSelectorService, routeGenerationService, trainingPlanStore, userModelStore);
    }

    public TrainingPlan7dServiceImpl(UserRepository userRepository,
                                     DailyAggregationService dailyAggregationService,
                                     LoadForecaster loadForecaster,
                                     FatigueAndOverloadService fatigueAndOverloadService,
                                     Clock clock,
                                     InjuryAwareTrainingService injuryAwareTrainingService,
                                     ReadinessScoreService readinessScoreService,
                                     WeatherService weatherService,
                                     DaySelectorService daySelectorService,
                                     GymWorkoutSelectorService gymWorkoutSelectorService,
                                     RouteGenerationService routeGenerationService,
                                     TrainingPlanStore trainingPlanStore,
                                     UserModelStore userModelStore) {
        this.userRepository = userRepository;
        this.dailyAggregationService = dailyAggregationService;
        this.loadForecaster = loadForecaster;
        this.fatigueAndOverloadService = fatigueAndOverloadService;
        this.clock = clock;
        this.injuryAwareTrainingService = injuryAwareTrainingService;
        this.readinessScoreService = readinessScoreService;
        this.weatherService = weatherService;
        this.daySelectorService = daySelectorService;
        this.gymWorkoutSelectorService = gymWorkoutSelectorService;
        this.routeGenerationService = routeGenerationService;
        this.trainingPlanStore = trainingPlanStore;
        this.userModelStore = userModelStore;
    }

    @Override
    public TrainingPlan7dDto buildNext7Days(
            String email,
            double latitude,
            double longitude,
            boolean debug,
            Integer simsParam,
            Long seedParam,
            DevOverrides overrides,
            boolean regen
    ) {
        ApplicationUser user = userRepository.findUserByEmail(email);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        int sims = clampSims(simsParam);
        long seed = defaultSeed(seedParam);

        // Stable weekly plan id (Monday-based week)
        LocalDate today = LocalDate.now(clock);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        String planId = "week:" + weekStart;

        // Cache hit -> return stable plan (no recomputation)
        if (!regen) {
            Optional<TrainingPlan7dDto> cached = trainingPlanStore.get(email, planId);
            if (cached.isPresent()) {
                log.info("7d plan CACHE HIT planId={} debug={} overrides={}", planId, debug, overrides);
                return cached.get();
            }
        } else {
            log.info("7d plan RECOMPUTE planId={} debug={} regen={} overrides={}", planId, debug, regen, overrides);
            trainingPlanStore.remove(email, planId);
        }


        // ----------------------------
        // DEV overrides (optional)
        // ----------------------------
        Double injuryIndexOverride = (overrides == null) ? null : overrides.injuryIndex();
        Integer readinessOverride = (overrides == null) ? null : overrides.readiness();
        Double ctlOverride = (overrides == null) ? null : overrides.ctl();
        Double atlOverride = (overrides == null) ? null : overrides.atl();

        Integer historyDays = (overrides == null) ? null : overrides.historyDays();
        Double historyMean = (overrides == null) ? null : overrides.historyMean();
        Double historyStd = (overrides == null) ? null : overrides.historyStd();

        double injuryIndex = (injuryIndexOverride != null)
                ? injuryIndexOverride
                : safe(() -> injuryAwareTrainingService.getInjuryIndex(email), 0.0);

        int readiness = (readinessOverride != null)
                ? readinessOverride
                : safeInt(() -> readinessScoreService.calculateReadinessScore(user, today), 50);

        // Injuries (keep real data; feel free to override too if you want)
        final List<Injuries> injuries = safeList(() -> injuryAwareTrainingService.findInjuriesByEmail(email));
        final Map<BodyPart, Double> injuriesMap = injuryAwareTrainingService.calculateInjuriesMap(injuries);

        final LoadConstraints constraints = new LoadConstraints(
                injuryAwareTrainingService.calculateIntensityScaling(injuryIndex),
                injuryAwareTrainingService.calculateVolumeScaling(injuryIndex),
                injuryAwareTrainingService.calculateHighImpactPenalty(injuryIndex)
        );

        // Weather
        final List<CompactWeatherDto> weatherPerDay = precomputeWeather(today, latitude, longitude, 18);

        // ----------------------------
        // RECENT LOADS: real OR fake
        // ----------------------------
        final List<Integer> recentLoads;
        if (historyDays != null && historyDays > 0) {
            double mean = (historyMean != null) ? historyMean : 35.0;
            double std = (historyStd != null) ? historyStd : 10.0;
            recentLoads = fakeRecentLoads(historyDays, mean, std, seed);
        } else {
            final List<DailySummary> history = dailyAggregationService.getDailySummaries(user, 60);
            recentLoads = history.stream().map(DailySummary::getTotalLoad).toList();
        }

        final PlannerProfile profile = buildPlannerProfile(user, recentLoads);

        // ----------------------------
        // INITIAL CTL/ATL: real OR override
        // ----------------------------
        double ctl = (ctlOverride != null) ? ctlOverride : safe(() -> fatigueAndOverloadService.currentCtl(user), 0.0);
        double atl = (atlOverride != null) ? atlOverride : safe(() -> fatigueAndOverloadService.currentAtl(user), 0.0);
        final ForecastState initialState = new ForecastState(ctl, atl);

        boolean coldStart = isColdStart(ctl, atl, recentLoads);

        log.info(
                "7d inputs USED planId={} seed={} sims={} injuryIndex={} readiness={} ctl={} atl={} recentLoads(n={}, head={})",
                planId, seed, sims,
                injuryIndex, readiness, ctl, atl,
                recentLoads.size(),
                recentLoads.stream().limit(7).toList()
        );

        List<PplDailyObs> pastDays = dailyAggregationService.getPplDailyObs(user, 90);

        FitUserModelRequest fitReq = new FitUserModelRequest(
                String.valueOf(user.getId()),
                user.getExperienceLevel() == null ? "INTERMEDIATE" : user.getExperienceLevel().name(),
                pastDays,
                initialState.ctl(),
                initialState.atl(),
                seed
        );

        String modelKey = "fit:" + weekStart;

        Optional<FitUserModelResponse> fitModelOpt = Optional.empty();
        if (!regen) {
            fitModelOpt = userModelStore.get(email, modelKey);
        }

        // Candidate weekly templates including gym/mobility
        List<List<WorkoutType>> templates = generateTemplates(user, today, coldStart);

        // Choose best via Monte Carlo utility
        PlanChoice choice = chooseBestPlan(
                user, today, templates, initialState, recentLoads,
                injuryIndex, readiness, weatherPerDay, constraints,
                sims, seed, debug, profile,
                fitModelOpt
        );


        List<PlannedDayDto> days = materializePlanWithTsbDists(
                user,
                today,
                choice.bestTemplate(),
                initialState,
                recentLoads,
                choice.bestTsbDists(),
                injuryIndex,
                injuriesMap,
                readiness,
                weatherPerDay,
                constraints,
                planId
        );

        TrainingPlan7dDto dto = new TrainingPlan7dDto(days);
        dto.setPlanId(planId);
        dto.setDebug(choice.debug());

        trainingPlanStore.put(email, planId, dto);
        return dto;
    }

    /**
     * Generates deterministic fake recent loads for dev/manual testing.
     * Uses the same sampling method as your load forecaster path (non-negative normal).
     */
    private List<Integer> fakeRecentLoads(int days, double mean, double std, long seed) {
        Random rng = new Random(seed ^ 0x9E3779B97F4A7C15L);
        List<Integer> out = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            double sample = sampleNonNegativeNormal(rng, mean, std);
            out.add((int) Math.round(sample));
        }
        return out;
    }


    private List<List<WorkoutType>> generateTemplates(ApplicationUser user, LocalDate startDate, boolean coldStart) {
        TemplateGenCfg cfg = cfgFor(user.getExperienceLevel());

        // 1) AVAILABLE-day mask (NOT "must run")
        List<Integer> availableIdx = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = startDate.plusDays(i);
            if (daySelectorService.isTrainingDay(d, user)) { // treat as "available"
                availableIdx.add(i);
            }
        }

        // Fallback if user has no available days
        if (availableIdx.isEmpty()) {
            return List.of(List.of(
                    WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY,
                    WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY,
                    WorkoutType.REST_DAY
            ));
        }

        // 2) Choose run-day subsets from availability (these create REST/GYM slots!)
        List<List<Integer>> runIdxVariants = runDaySubsets(availableIdx, cfg);

        List<List<WorkoutType>> out = new ArrayList<>();

        for (List<Integer> runIdx : runIdxVariants) {
            int runN = runIdx.size();

            // If too few run days, keep it super simple (still uses availability!)
            if (runN < cfg.minRunDays()) {
                out.add(buildSimpleEasyWeekFromAvailability(availableIdx, runIdx, cfg));
                continue;
            }

            // Decide how many quality sessions this week based on *run days*
            int numIntensity = switch (runN) {
                case 0, 1, 2 -> 0;
                case 3, 4 -> 1;
                default -> 2;
            };
            numIntensity = Math.min(numIntensity, cfg.maxQuality());

            // Long run only if allowed and enough run days
            boolean includeLong = cfg.allowLong() && (runN >= 3);

            // Long placement choices are within RUN list positions
            List<Integer> longPosChoices = includeLong
                    ? List.of(runN - 1, Math.max(0, runN - 2))
                    : List.of();

            // Intensity positions (within runIdx positions)
            List<int[]> intensityPosChoices = new ArrayList<>();
            if (numIntensity == 0) {
                intensityPosChoices.add(new int[]{}); // none
            } else if (numIntensity == 1) {
                intensityPosChoices.add(new int[]{0});
                intensityPosChoices.add(new int[]{Math.min(1, runN - 1)});
                intensityPosChoices.add(new int[]{Math.max(0, runN / 2)});
            } else {
                intensityPosChoices.add(new int[]{0, Math.max(1, runN / 2)});
                intensityPosChoices.add(new int[]{0, Math.max(1, runN - 3)});
                intensityPosChoices.add(new int[]{1, Math.max(2, runN - 3)});
            }

            // Intensity type variants
            List<WorkoutType[]> intensityTypeVariants;
            if (numIntensity == 0) {
                intensityTypeVariants = java.util.Collections.singletonList(new WorkoutType[]{});
            } else if (!cfg.allowIntervals() && cfg.allowTempo()) {
                intensityTypeVariants = java.util.Collections.singletonList(
                        (numIntensity == 1)
                                ? new WorkoutType[]{WorkoutType.TEMPO_RUN}
                                : new WorkoutType[]{WorkoutType.TEMPO_RUN, WorkoutType.TEMPO_RUN}
                );
            } else {
                intensityTypeVariants = (numIntensity == 1)
                        ? List.of(
                        new WorkoutType[]{WorkoutType.TEMPO_RUN},
                        new WorkoutType[]{WorkoutType.INTERVAL_RUN}
                )
                        : List.of(
                        new WorkoutType[]{WorkoutType.TEMPO_RUN, WorkoutType.INTERVAL_RUN},
                        new WorkoutType[]{WorkoutType.INTERVAL_RUN, WorkoutType.TEMPO_RUN},
                        new WorkoutType[]{WorkoutType.TEMPO_RUN, WorkoutType.TEMPO_RUN}
                );
            }

            // Gym/mobility strategies (keep yours)
            List<GymMobStrategy> strategies = switch (user.getExperienceLevel()) {
                case BEGINNER -> List.of(GymMobStrategy.MOBILITY_ON_NONRUN, GymMobStrategy.GYM_ON_EARLIEST_NONRUN);
                case CASUAL -> List.of(GymMobStrategy.MOBILITY_ON_NONRUN, GymMobStrategy.GYM_AFTER_INTENSITY);
                case INTERMEDIATE, ADVANCED -> List.of(
                        GymMobStrategy.GYM_BEFORE_LONG, GymMobStrategy.GYM_AFTER_INTENSITY,
                        GymMobStrategy.MOBILITY_ON_NONRUN, GymMobStrategy.GYM_ON_EARLIEST_NONRUN
                );
                case COMPETITIVE_ATHLETE -> List.of(GymMobStrategy.GYM_AFTER_INTENSITY);
                default -> List.of(GymMobStrategy.MOBILITY_ON_NONRUN);
            };

            List<Integer> longPosLoop = includeLong
                    ? longPosChoices
                    : java.util.Collections.singletonList(null);

            for (Integer longPos : longPosLoop) {
                for (int[] intenPos : intensityPosChoices) {
                    for (WorkoutType[] intenTypes : intensityTypeVariants) {
                        for (GymMobStrategy strat : strategies) {
                            List<WorkoutType> t = buildTemplate(
                                    availableIdx,
                                    runIdx,
                                    runN,
                                    longPos,
                                    intenPos,
                                    intenTypes,
                                    strat,
                                    cfg
                            );
                            if (t != null) {
                                out.add(t);
                            }
                        }
                    }
                }
            }
        }


        // de-duplicate + cap
        // post-process (hard safety + week shape) especially for cold start
        out = postProcessTemplates(out, user.getExperienceLevel(), coldStart);

        // de-duplicate + cap
        return out.stream().distinct().limit(24).toList();

    }

    private List<List<Integer>> runDaySubsets(List<Integer> availableIdx, TemplateGenCfg cfg) {
        int n = availableIdx.size();

        // force at least one rest day for some levels (or just to avoid "run 7/7")
        int desiredRunDays = n;

        if (cfg.requireRestDay()) {
            desiredRunDays = Math.max(1, n - 1);
        }

        // Never exceed availability; never drop below 1
        desiredRunDays = Math.max(1, Math.min(n, desiredRunDays));

        // Also respect minRunDays somewhat (but allow fewer -> simple week path handles it)
        // desiredRunDays = Math.max(1, Math.min(n, Math.max(cfg.minRunDays(), desiredRunDays)));

        List<List<Integer>> variants = new ArrayList<>();

        // Variant A: take first desiredRunDays
        variants.add(new ArrayList<>(availableIdx.subList(0, desiredRunDays)));

        if (n <= 3) {
            return variants;
        }

        // Variant B: take last desiredRunDays
        variants.add(new ArrayList<>(availableIdx.subList(n - desiredRunDays, n)));

        // Variant C: drop one from the middle (creates a rest in the middle of week)
        if (desiredRunDays <= n - 1) {
            List<Integer> midDrop = new ArrayList<>(availableIdx);
            midDrop.remove(n / 2);
            variants.add(midDrop.subList(0, desiredRunDays));
        }

        // Variant D: alternate pattern (every other available day)
        List<Integer> alt = new ArrayList<>();
        for (int i = 0; i < n; i += 2) {
            alt.add(availableIdx.get(i));
        }
        if (!alt.isEmpty()) {
            while (alt.size() > desiredRunDays) {
                alt.remove(alt.size() - 1);
            }
            variants.add(alt);
        }

        // If we still have too many duplicates, distinct() later will handle it.
        return variants;
    }

    private enum GymMobStrategy {
        GYM_BEFORE_LONG,
        GYM_AFTER_INTENSITY,
        MOBILITY_ON_NONRUN,
        GYM_ON_EARLIEST_NONRUN
    }

    private List<WorkoutType> buildSimpleEasyWeekFromAvailability(
            List<Integer> availableIdx,
            List<Integer> runIdx,
            TemplateGenCfg cfg
    ) {
        WorkoutType[] week
                = new WorkoutType[]{ WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY };

        // Only run on runIdx, not all available days
        for (int idx : runIdx) {
            week[idx] = WorkoutType.EASY_RUN;
        }

        // add mobility/gym ONLY on available non-run REST slots
        boolean[] isAvailable = new boolean[7];
        boolean[] isRun = new boolean[7];
        for (int i : availableIdx) {
            isAvailable[i] = true;
        }
        for (int i : runIdx) {
            isRun[i] = true;
        }

        int addedMob = 0;
        int addedGym = 0;

        for (int i = 0; i < 7 && addedMob < cfg.maxMobility(); i++) {
            if (isAvailable[i] && !isRun[i] && week[i] == WorkoutType.REST_DAY) {
                week[i] = WorkoutType.MOBILITY;
                addedMob++;
            }
        }

        for (int i = 0; i < 7 && addedGym < cfg.maxGym(); i++) {
            if (isAvailable[i] && !isRun[i] && week[i] == WorkoutType.REST_DAY) {
                week[i] = WorkoutType.GYM_PREHAB;
                addedGym++;
            }
        }

        // enforce at least one rest day if required
        if (cfg.requireRestDay() && java.util.Arrays.stream(week).noneMatch(w -> w == WorkoutType.REST_DAY)) {
            int idx = lastIndexOf(week, WorkoutType.EASY_RUN);
            if (idx >= 0) {
                week[idx] = WorkoutType.REST_DAY;
            }
        }

        return java.util.Arrays.asList(week);
    }


    private int lastIndexOf(WorkoutType[] week, WorkoutType candidate) {
        for (int i = 6; i >= 0; i--) {
            if (week[i] == candidate) {
                return i;
            }
        }
        return -1;
    }


    private void capGymMobility(WorkoutType[] week, TemplateGenCfg cfg) {
        int gym = 0;
        int mob = 0;
        for (int i = 0; i < 7; i++) {
            if (week[i] == WorkoutType.GYM_PREHAB) {
                gym++;
            }
            if (week[i] == WorkoutType.MOBILITY) {
                mob++;
            }
        }
        // if too many, turn extras back to REST
        for (int i = 0; i < 7 && gym > cfg.maxGym(); i++) {
            if (week[i] == WorkoutType.GYM_PREHAB) {
                week[i] = WorkoutType.REST_DAY;
                gym--;
            }
        }
        for (int i = 0; i < 7 && mob > cfg.maxMobility(); i++) {
            if (week[i] == WorkoutType.MOBILITY) {
                week[i] = WorkoutType.REST_DAY;
                mob--;
            }
        }
    }

    private List<WorkoutType> buildTemplate(
            List<Integer> availableIdx,
            List<Integer> runIdx,
            int runN,
            Integer longPosInRunIdx,
            int[] intensityPosInRunIdx,
            WorkoutType[] intensityTypes,
            GymMobStrategy strat,
            TemplateGenCfg cfg
    ) {
        // Start REST everywhere
        WorkoutType[] week
                = new WorkoutType[]{ WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY};

        // Mark run days as EASY (only chosen runIdx!)
        for (int absDay : runIdx) {
            week[absDay] = WorkoutType.EASY_RUN;
        }

        // Place LONG on a run day (by position within runIdx)
        Integer longAbsDay = null;
        if (longPosInRunIdx != null && runN > 0) {
            int lp = Math.max(0, Math.min(runN - 1, longPosInRunIdx));
            longAbsDay = runIdx.get(lp);
            week[longAbsDay] = WorkoutType.LONG_RUN;
        }

        // Place intensity sessions on run days
        for (int k = 0; k < intensityPosInRunIdx.length; k++) {
            int pos = intensityPosInRunIdx[k];
            if (pos < 0 || pos >= runN) {
                continue;
            }

            int absDay = runIdx.get(pos);
            if (longAbsDay != null && absDay == longAbsDay) {
                continue;
            }

            WorkoutType it = intensityTypes[Math.min(k, intensityTypes.length - 1)];
            week[absDay] = it;
        }

        // Guardrail: avoid consecutive hard days
        if (!passesHardDaySpacing(week)) {
            return null;
        }

        // Apply gym/mob strategy ONLY on available non-run REST slots
        applyGymMobility(week, availableIdx, runIdx, longAbsDay, strat);

        capGymMobility(week, cfg);

        if (cfg.requireRestDay() && java.util.Arrays.stream(week).noneMatch(w -> w == WorkoutType.REST_DAY)) {
            // force one rest by downgrading an EASY day
            int idx = firstIndexOf(week, WorkoutType.EASY_RUN);
            if (idx >= 0) {
                week[idx] = WorkoutType.REST_DAY;
            }
        }

        if (!passesHardDaySpacing(week)) {
            return null;
        }

        return java.util.Arrays.asList(week);
    }


    private boolean passesHardDaySpacing(WorkoutType[] week) {
        for (int i = 1; i < 7; i++) {
            if (isHard(week[i - 1]) && isHard(week[i])) {
                return false;
            }
        }
        return true;
    }

    private boolean isHard(WorkoutType wt) {
        return wt == WorkoutType.INTERVAL_RUN
                || wt == WorkoutType.TEMPO_RUN
                || wt == WorkoutType.LONG_RUN;
    }

    private void applyGymMobility(
            WorkoutType[] week,
            List<Integer> availableIdx,
            List<Integer> runIdx,
            Integer longAbsDay,
            GymMobStrategy strat
    ) {
        boolean[] isAvailable = new boolean[7];
        boolean[] isRunDay = new boolean[7];
        for (int i : availableIdx) {
            isAvailable[i] = true;
        }
        for (int i : runIdx) {
            isRunDay[i] = true;
        }

        // eligible slots = available AND currently REST AND not a run day
        List<Integer> eligibleNonRun = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            if (isAvailable[i] && !isRunDay[i] && week[i] == WorkoutType.REST_DAY) {
                eligibleNonRun.add(i);
            }
        }

        switch (strat) {
            case GYM_BEFORE_LONG -> {
                if (longAbsDay == null) {
                    return;
                }
                int gymDay = Math.max(0, longAbsDay - 1);

                // only if eligible (available, non-run, rest)
                if (gymDay >= 0 && gymDay < 7 && isAvailable[gymDay] && !isRunDay[gymDay] && week[gymDay] == WorkoutType.REST_DAY) {
                    week[gymDay] = WorkoutType.GYM_PREHAB;
                } else if (!eligibleNonRun.isEmpty()) {
                    week[eligibleNonRun.get(0)] = WorkoutType.GYM_PREHAB;
                }
            }
            case GYM_AFTER_INTENSITY -> {
                int intenDay = firstIndexOf(week, WorkoutType.INTERVAL_RUN, WorkoutType.TEMPO_RUN);
                if (intenDay < 0) {
                    return;
                }

                int gymDay = Math.min(6, intenDay + 1);
                if (isAvailable[gymDay] && !isRunDay[gymDay] && week[gymDay] == WorkoutType.REST_DAY) {
                    week[gymDay] = WorkoutType.GYM_PREHAB;
                } else if (!eligibleNonRun.isEmpty()) {
                    week[eligibleNonRun.get(eligibleNonRun.size() - 1)] = WorkoutType.GYM_PREHAB;
                }
            }
            case MOBILITY_ON_NONRUN -> {
                if (!eligibleNonRun.isEmpty()) {
                    week[eligibleNonRun.get(0)] = WorkoutType.MOBILITY;
                }
            }
            case GYM_ON_EARLIEST_NONRUN -> {
                if (!eligibleNonRun.isEmpty()) {
                    week[eligibleNonRun.get(0)] = WorkoutType.GYM_PREHAB;
                }
            }
            default -> {
                if (!eligibleNonRun.isEmpty()) {
                    week[eligibleNonRun.get(0)] = WorkoutType.MOBILITY;
                }
            }
        }
    }


    private int firstIndexOf(WorkoutType[] week, WorkoutType... candidates) {
        for (int i = 0; i < 7; i++) {
            for (WorkoutType c : candidates) {
                if (week[i] == c) {
                    return i;
                }
            }
        }
        return -1;
    }


    private PlanChoice chooseBestPlan(ApplicationUser user,
                                      LocalDate startDate,
                                      List<List<WorkoutType>> templates,
                                      ForecastState initialState,
                                      List<Integer> recentLoads,
                                      double injuryIndex,
                                      int readiness,
                                      List<CompactWeatherDto> weatherPerDay,
                                      LoadConstraints constraints,
                                      int sims,
                                      long seed,
                                      boolean debug,
                                      PlannerProfile profile,
                                      Optional<FitUserModelResponse> fitUserModelOpt) {

        final double wsim = 1.0;
        final double wtemplateprior = 0.25;
        final double wmissing = 0.35;
        final double wexp = 0.30;
        final double wshape = 0.40;


        List<TemplateScoreDto> templateScores = debug ? new ArrayList<>() : null;

        double bestScore = Double.NEGATIVE_INFINITY;
        int bestTemplateIndex = -1;
        List<WorkoutType> bestTemplate = null;
        List<LoadDistributionDto> bestTsbDists = null;
        final ExperienceConfig cfg = expCfg(user);

        for (int templateIndex = 0; templateIndex < templates.size(); templateIndex++) {
            List<WorkoutType> template = templates.get(templateIndex);
            EffectiveWeek effectiveWeek = computeEffectiveWeek(template, injuryIndex, readiness, weatherPerDay, cfg);
            List<WorkoutType> effectiveTemplate = effectiveWeek.effective();

            long ehard = effectiveTemplate.stream().filter(this::isHard).count();
            long elong = effectiveTemplate.stream().filter(w -> w == WorkoutType.LONG_RUN).count();
            long equal = effectiveTemplate.stream().filter(w -> w == WorkoutType.TEMPO_RUN || w == WorkoutType.INTERVAL_RUN).count();
            log.info("cand {} effective hard={} long={} qual={} template={}", templateIndex, ehard, elong, equal, effectiveTemplate);
            long templateSeed = seed ^ (templateIndex * 1315423911L);


            // fallback to your existing Java simulation
            SimResult sim = simulateTemplateJava(
                    user,
                    startDate,
                    template,
                    effectiveWeek,
                    initialState,
                    recentLoads,
                    injuryIndex,
                    readiness,
                    weatherPerDay,
                    sims,
                    templateSeed,
                    profile,
                    constraints
            );

            double prior = templatePrior(profile, effectiveTemplate);
            prior += missingKeySessionsPenalty(effectiveTemplate, profile, injuryIndex, readiness, cfg);
            prior += experienceTemplatePrior(cfg, effectiveTemplate);
            double avgUtility = sim.avgUtility();
            double shape = weekShapeScore(effectiveTemplate, profile);

            double simScore = sim.riskAdjustedScore();

            double tp = templatePrior(profile, effectiveTemplate);
            double mk = missingKeySessionsPenalty(effectiveTemplate, profile, injuryIndex, readiness, cfg);
            double ep = experienceTemplatePrior(cfg, effectiveTemplate);
            double sh = weekShapeScore(effectiveTemplate, profile);

            // IMPORTANT: total score
            double scored =
                    wsim * simScore
                            + wtemplateprior * tp
                            + wmissing * mk
                            + wexp * ep
                            + wshape * sh;


            if (debug) {
                logBreakdown(templateIndex, effectiveTemplate,
                        new ScoreBreakdown(simScore, tp, mk, ep, sh, scored));
            }


            List<LoadDistributionDto> tsbDists = sim.tsbDists();

            log.info("template {} avgUtility={} utilStd={} riskAdj={} prior={} scored={}",
                    templateIndex,
                    sim.avgUtility(),
                    sim.utilDist() == null ? null : sim.utilDist().getStd(),
                    sim.riskAdjustedScore(),
                    prior,
                    scored);


            // Debug: show planned vs effective template for this candidate
            if (debug) {
                templateScores.add(new TemplateScoreDto(templateIndex, avgUtility, new ArrayList<>(template), new ArrayList<>(effectiveTemplate)));
            }

            if (scored > bestScore) {
                bestScore = scored;
                bestTemplateIndex = templateIndex;
                bestTemplate = template;
                bestTsbDists = tsbDists;
            }
        }

        // Fallback if nothing chosen (shouldn't happen, but keep it)
        if (bestTemplate == null) {
            bestTemplateIndex = -1;
            bestScore = 0.0;
            bestTemplate = List.of(
                    WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY,
                    WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY,
                    WorkoutType.REST_DAY
            );
            bestTsbDists = List.of(
                    new LoadDistributionDto(0, 0, 0, 0, 0),
                    new LoadDistributionDto(0, 0, 0, 0, 0),
                    new LoadDistributionDto(0, 0, 0, 0, 0),
                    new LoadDistributionDto(0, 0, 0, 0, 0),
                    new LoadDistributionDto(0, 0, 0, 0, 0),
                    new LoadDistributionDto(0, 0, 0, 0, 0),
                    new LoadDistributionDto(0, 0, 0, 0, 0)
            );
            if (debug && templateScores != null && templateScores.isEmpty()) {
                templateScores = new ArrayList<>();
            }
        }

        TrainingPlanDebugDto debugDto = null;
        if (debug) {
            List<DayDebugDto> dayDebug = buildDayDebug(
                    user,
                    startDate,
                    bestTemplate,
                    initialState,
                    recentLoads,
                    bestTsbDists,
                    injuryIndex,
                    readiness,
                    weatherPerDay,
                    constraints
            );

            debugDto = new TrainingPlanDebugDto(
                    sims,
                    seed,
                    bestTemplateIndex,
                    bestScore,
                    templateScores == null ? List.of() : templateScores,
                    dayDebug
            );
        }

        return new PlanChoice(bestTemplateIndex, bestScore, bestTemplate, bestTsbDists, debugDto);
    }

    private double weekShapeScore(List<WorkoutType> template, PlannerProfile profile) {
        if (template == null || template.size() != 7) {
            return 0.0;
        }

        double s = 0.0;

        int hardCount = (int) template.stream().filter(this::isHard).count();
        int runCount = (int) template.stream().filter(this::isRun).count();
        int restCount = (int) template.stream().filter(w -> w == WorkoutType.REST_DAY).count();

        if (restCount >= 1 && hardCount >= 1) {
            s += 5.0;        // “rest is good” only if week has quality
        }
        if (restCount >= 2) {
            s -= 6.0 * (restCount - 1);        // too many rest days becomes bad
        }
        if (runCount <= 3) {
            s -= 20.0;                          // “week barely trains” should lose
        }

        return s;
    }

    private boolean isRun(WorkoutType wt) {
        return wt == WorkoutType.EASY_RUN || wt == WorkoutType.TEMPO_RUN
                || wt == WorkoutType.INTERVAL_RUN || wt == WorkoutType.LONG_RUN;
    }


    private SimResult simulateTemplateJava(
            ApplicationUser user,
            LocalDate startDate,
            List<WorkoutType> plannedTemplate,
            ForecastState initialState,
            List<Integer> recentLoads,
            double injuryIndex,
            int readiness,
            List<CompactWeatherDto> weatherPerDay,
            int sims,
            long seed,
            PlannerProfile profile,
            LoadConstraints constraints
    ) {
        Random rng = new Random(seed);

        ExperienceConfig cfg = expCfg(user);
        // (1) precompute weatherScore + effective workout type once (NOT per sim)
        EffectiveWeek effectiveWeek = computeEffectiveWeek(plannedTemplate, injuryIndex, readiness, weatherPerDay, cfg);

        // collect TSB samples
        List<List<Double>> tsbSamplesPerDay = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            tsbSamplesPerDay.add(new ArrayList<>(sims));
        }

        double totalUtility = 0.0;
        List<Double> utilSamples = new ArrayList<>(sims);

        for (int s = 0; s < sims; s++) {
            ForecastState st = initialState;
            double utility = 0.0;
            double weeklyLoad = 0.0;

            for (int i = 0; i < 7; i++) {
                LocalDate d = startDate.plusDays(i);
                WorkoutType effective = effectiveWeek.effective().get(i);
                Double ws = effectiveWeek.weatherScores().get(i);

                // forecast load based on current state
                LoadDistributionDto loadDist =
                        loadForecaster.forecastLoad(user, d, effective, st, recentLoads, constraints);

                double stdAdj = loadDist.getStd() * uncertaintyMultiplier(profile, injuryIndex, readiness, ws, effective);

                double loadSample = sampleNonNegativeNormal(rng, loadDist.getMean(), stdAdj);
                weeklyLoad += loadSample;

                // advance
                st = st.next(loadSample);

                // record
                tsbSamplesPerDay.get(i).add(st.tsb());

                // utility (same as your original)
                utility += trainingReward(effective, loadSample);
                utility -= fatiguePenalty(st.tsb(), effective);
                utility -= injuryPenalty(injuryIndex, effective);
                utility -= readinessPenalty(readiness, effective);
                utility -= weatherPenalty(ws, effective);
            }

            // weekly undertraining penalty (ONCE)
            double targetWeek = profile.targetWeeklyLoad() * weekTargetMultiplier(injuryIndex, readiness);
            double deficit = Math.max(0.0, targetWeek - weeklyLoad);
            double k = 0.0035 / Math.max(0.4, profile.riskAversion());
            utility -= k * deficit * deficit;

            totalUtility += utility;
            utilSamples.add(utility);
        }

        double avgUtility = totalUtility / sims;
        JuliaDist utilDist = toDist(utilSamples);
        double lambda = 0.35 + 0.9 * profile.riskAversion(); // e.g. ~0.6..1.6
        double riskAdjusted = avgUtility - lambda * utilDist.getStd();

        // samples -> distributions
        List<LoadDistributionDto> tsbDists = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            tsbDists.add(toDistribution(tsbSamplesPerDay.get(i)));
        }

        return new SimResult(avgUtility, utilDist, riskAdjusted, tsbDists);
    }

    // overloaded
    private SimResult simulateTemplateJava(
            ApplicationUser user,
            LocalDate startDate,
            List<WorkoutType> plannedTemplate,
            EffectiveWeek effectiveWeek,
            ForecastState initialState,
            List<Integer> recentLoads,
            double injuryIndex,
            int readiness,
            List<CompactWeatherDto> weatherPerDay,
            int sims,
            long seed,
            PlannerProfile profile,
            LoadConstraints constraints
    ) {
        Random rng = new Random(seed);
        ExperienceConfig cfg = expCfg(user);

        // if caller didn't precompute it, do it here
        EffectiveWeek eff = (effectiveWeek != null)
                ? effectiveWeek
                : computeEffectiveWeek(plannedTemplate, injuryIndex, readiness, weatherPerDay, cfg);

        // collect TSB samples
        List<List<Double>> tsbSamplesPerDay = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            tsbSamplesPerDay.add(new ArrayList<>(sims));
        }

        double totalUtility = 0.0;
        List<Double> utilSamples = new ArrayList<>(sims);

        for (int s = 0; s < sims; s++) {
            ForecastState st = initialState;
            double utility = 0.0;
            double weeklyLoad = 0.0;

            for (int i = 0; i < 7; i++) {
                LocalDate d = startDate.plusDays(i);
                WorkoutType effective = eff.effective().get(i);
                Double ws = eff.weatherScores().get(i);

                // forecast load based on current state
                LoadDistributionDto loadDist =
                        loadForecaster.forecastLoad(user, d, effective, st, recentLoads, constraints);

                double stdAdj = loadDist.getStd() * uncertaintyMultiplier(profile, injuryIndex, readiness, ws, effective);

                double loadSample = sampleNonNegativeNormal(rng, loadDist.getMean(), stdAdj);
                weeklyLoad += loadSample;

                // advance
                st = st.next(loadSample);

                // record
                tsbSamplesPerDay.get(i).add(st.tsb());

                // utility (same as your original)
                utility += trainingReward(effective, loadSample);
                utility -= fatiguePenalty(st.tsb(), effective);
                utility -= injuryPenalty(injuryIndex, effective);
                utility -= readinessPenalty(readiness, effective);
                utility -= weatherPenalty(ws, effective);
            }

            // weekly undertraining penalty (ONCE)
            double targetWeek = profile.targetWeeklyLoad() * weekTargetMultiplier(injuryIndex, readiness);
            double deficit = Math.max(0.0, targetWeek - weeklyLoad);
            double k = 0.0035 / Math.max(0.4, profile.riskAversion());
            utility -= k * deficit * deficit;

            totalUtility += utility;
            utilSamples.add(utility);
        }

        double avgUtility = totalUtility / sims;
        JuliaDist utilDist = toDist(utilSamples);
        double lambda = 0.35 + 0.9 * profile.riskAversion(); // e.g. ~0.6..1.6
        double riskAdjusted = avgUtility - lambda * utilDist.getStd();

        // samples -> distributions
        List<LoadDistributionDto> tsbDists = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            tsbDists.add(toDistribution(tsbSamplesPerDay.get(i)));
        }

        return new SimResult(avgUtility, utilDist, riskAdjusted, tsbDists);
    }


    private JuliaDist toDist(List<Double> samples) {
        if (samples == null || samples.isEmpty()) {
            return new JuliaDist(0, 0, 0, 0, 0);
        }
        List<Double> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);

        double mean = sorted.stream().mapToDouble(x -> x).average().orElse(0);
        double std = std(sorted, mean);

        double p10 = quantile(sorted, 0.10);
        double p50 = quantile(sorted, 0.50);
        double p90 = quantile(sorted, 0.90);

        return new JuliaDist(p10, p50, p90, mean, std);
    }

    private record EffectiveWeek(List<WorkoutType> effective, List<Double> weatherScores) {
    }

    private EffectiveWeek computeEffectiveWeek(
            List<WorkoutType> planned,
            double injuryIndex,
            int readiness,
            List<CompactWeatherDto> weatherPerDay,
            ExperienceConfig cfg
    ) {
        List<WorkoutType> eff = new ArrayList<>(7);
        List<Double> ws = new ArrayList<>(7);

        for (int i = 0; i < 7; i++) {
            CompactWeatherDto w = weatherPerDay.get(i);
            Double score = (w == null) ? null : w.getWeatherScore();
            ws.add(score);
            eff.add(effectiveWorkoutType(planned.get(i), injuryIndex, readiness, score, cfg));
        }
        return new EffectiveWeek(eff, ws);
    }

    private List<DayDebugDto> buildDayDebug(ApplicationUser user,
                                            LocalDate startDate,
                                            List<WorkoutType> bestTemplate,
                                            ForecastState initialState,
                                            List<Integer> recentLoads,
                                            List<LoadDistributionDto> bestTsbDists,
                                            double injuryIndex,
                                            int readiness,
                                            List<CompactWeatherDto> weatherPerDay,
                                            LoadConstraints constraints) {

        List<DayDebugDto> out = new ArrayList<>(7);
        ForecastState st = initialState;

        for (int i = 0; i < 7; i++) {
            LocalDate d = startDate.plusDays(i);

            CompactWeatherDto w = weatherPerDay.get(i);

            WorkoutType planned = bestTemplate.get(i);

            Double wsObj = (w == null) ? null : w.getWeatherScore();
            double ws = (wsObj == null) ? 0.6 : wsObj;

            ExperienceConfig cfg = expCfg(user);

            WorkoutType effective = effectiveWorkoutType(planned, injuryIndex, readiness, wsObj, cfg);

            LoadDistributionDto load = loadForecaster.forecastLoad(user, d, effective, st, recentLoads, constraints);

            // advance expected trajectory using mean
            st = st.next(load.getMean());

            double fatP = fatiguePenalty(st.tsb(), effective);
            double injP = injuryPenalty(injuryIndex, effective);
            double readyP = readinessPenalty(readiness, effective);
            double weathP = weatherPenalty(wsObj, effective);

            out.add(new DayDebugDto(
                    d,
                    planned,
                    effective,
                    ws,
                    load.getMean(),
                    load.getStd(),
                    bestTsbDists.get(i).getP50(),
                    fatP,
                    injP,
                    readyP,
                    weathP
            ));
        }

        return out;
    }

    private List<PlannedDayDto> materializePlanWithTsbDists(ApplicationUser user,
                                                            LocalDate startDate,
                                                            List<WorkoutType> template,
                                                            ForecastState initialState,
                                                            List<Integer> recentLoads,
                                                            List<LoadDistributionDto> tsbDists,
                                                            double injuryIndex,
                                                            Map<BodyPart, Double> injuriesMap,
                                                            int readiness,
                                                            List<CompactWeatherDto> weatherPerDay,
                                                            LoadConstraints constraints,
                                                            String planId) {

        List<PlannedDayDto> out = new ArrayList<>(7);
        ForecastState st = initialState;

        for (int i = 0; i < 7; i++) {
            LocalDate d = startDate.plusDays(i);

            CompactWeatherDto weatherDto = weatherPerDay.get(i);
            Double ws = (weatherDto == null) ? null : weatherDto.getWeatherScore();

            WorkoutType planned = template.get(i);

            ExperienceConfig cfg = expCfg(user);
            WorkoutType effective = effectiveWorkoutType(planned, injuryIndex, readiness, ws, cfg);

            LoadDistributionDto load = loadForecaster.forecastLoad(user, d, effective, st, recentLoads, constraints);

            st = st.next(load.getMean());

            GymWorkoutDto gym = null;
            if (effective == WorkoutType.GYM_PREHAB || effective == WorkoutType.MOBILITY) {
                gym = gymWorkoutSelectorService.getGymWorkout(user, d, injuriesMap, readiness);
            }

            RouteDto routeDto = null;
            boolean isRun =
                    effective == WorkoutType.EASY_RUN
                            || effective == WorkoutType.TEMPO_RUN
                            || effective == WorkoutType.INTERVAL_RUN
                            || effective == WorkoutType.LONG_RUN;

            if (isRun) {
                routeDto = computeRouteDtoForDay(user, d, effective, load, readiness, planId);
            }

            out.add(new PlannedDayDto(
                    d,
                    effective,
                    load,
                    tsbDists.get(i),
                    weatherDto,
                    confidenceFromStd(load),
                    explanation(effective, load, tsbDists.get(i).getP50(), injuryIndex, readiness),
                    gym,
                    routeDto
            ));
        }

        return out;
    }


    private String confidenceFromStd(LoadDistributionDto d) {
        double frac = d.getStd() / Math.max(1.0, d.getMean());
        if (frac < 0.18) {
            return "high";
        }
        if (frac < 0.32) {
            return "medium";
        }
        return "low";
    }

    private List<String> explanation(WorkoutType wt, LoadDistributionDto load, double tsb, double injuryIndex, int readiness) {
        List<String> e = new ArrayList<>();
        e.add("Planned: " + wt.toString());
        e.add("Expected load ≈ " + Math.round(load.getMean()) + " (P10 " + Math.round(load.getP10()) + " – P90 " + Math.round(load.getP90()) + ")");
        e.add("Readiness today: " + readiness + "/100");
        if (injuryIndex >= 0.7) {
            e.add("Injury-aware: intensity reduced.");
        }
        if (tsb < -20) {
            e.add("Fatigue risk: predicted TSB very low.");
        } else if (tsb < -10) {
            e.add("Fatigue: predicted TSB somewhat low.");
        } else {
            e.add("Recovery: predicted TSB acceptable.");
        }
        return e;
    }


    private double trainingReward(WorkoutType wt, double load) {
        return switch (wt) {
            case REST_DAY -> 0.0;
            case MOBILITY -> 2.0;
            case GYM_PREHAB -> 3.0;
            case EASY_RUN -> 6.0 + 0.015 * load;
            case LONG_RUN -> 10.0 + 0.020 * load;
            case TEMPO_RUN -> 12.0 + 0.018 * load;
            case INTERVAL_RUN -> 13.0 + 0.017 * load;
            default -> 7.0 + 0.015 * load;
        };
    }

    private double fatiguePenalty(double tsb, WorkoutType wt) {
        // Penalize hard sessions when fatigue is high
        double p = 0.0;
        if (tsb < -25) {
            p += 25;
        } else if (tsb < -15) {
            p += 12;
        } else if (tsb < -10) {
            p += 6;
        }

        // extra penalty if intensity day
        if ((wt == WorkoutType.INTERVAL_RUN || wt == WorkoutType.TEMPO_RUN || wt == WorkoutType.LONG_RUN) && tsb < -10) {
            p += 6;
        }
        return p;
    }

    private double sampleNonNegativeNormal(Random rng, double mean, double std) {
        if (std <= 0) {
            return Math.max(0, mean);
        }
        double u1 = Math.max(1e-12, rng.nextDouble());
        double u2 = rng.nextDouble();
        double z = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
        return Math.max(0, mean + std * z);
    }

    private double safe(SupplierWithException<Double> s, double fallback) {
        try {
            return s.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    private LoadDistributionDto toDistribution(List<Double> samples) {
        if (samples == null || samples.isEmpty()) {
            return new LoadDistributionDto(0, 0, 0, 0, 0);
        }
        List<Double> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);

        double mean = sorted.stream().mapToDouble(x -> x).average().orElse(0);
        double std = std(sorted, mean);

        double p10 = quantile(sorted, 0.10);
        double p50 = quantile(sorted, 0.50);
        double p90 = quantile(sorted, 0.90);

        return new LoadDistributionDto(p10, p50, p90, mean, std);
    }

    private double quantile(List<Double> sorted, double q) {
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

    private double std(List<Double> values, double mean) {
        if (values.size() < 2) {
            return 0;
        }
        double var = 0;
        for (double v : values) {
            double d = v - mean;
            var += d * d;
        }
        var /= (values.size() - 1);
        return Math.sqrt(var);
    }

    private WorkoutType mapWorkoutForInjury(WorkoutType wt, double injuryIndex) {
        if (injuryIndex >= 0.7) {
            return switch (wt) {
                case INTERVAL_RUN, TEMPO_RUN -> WorkoutType.MOBILITY;
                case LONG_RUN -> WorkoutType.EASY_RUN;
                default -> wt;
            };
        } else {
            return switch (wt) {
                case INTERVAL_RUN -> WorkoutType.TEMPO_RUN;
                default -> wt;
            };
        }
    }

    private double injuryPenalty(double injuryIndex, WorkoutType wt) {
        if (injuryIndex < 0.4) {
            return 0.0;
        }

        double p = 0.0;
        boolean hardRun = (wt == WorkoutType.INTERVAL_RUN || wt == WorkoutType.TEMPO_RUN || wt == WorkoutType.LONG_RUN);

        if (injuryIndex >= 0.7) {
            if (hardRun) {
                p += 30;
            }
            if (wt == WorkoutType.EASY_RUN) {
                p += 8; // still some penalty
            }
        } else { // 0.4 - 0.7
            if (wt == WorkoutType.INTERVAL_RUN) {
                p += 18;
            }
            if (wt == WorkoutType.TEMPO_RUN) {
                p += 8;
            }
        }
        return p;
    }

    private int safeInt(SupplierWithException<Integer> s, int fallback) {
        try {
            return s.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    private double readinessPenalty(int readiness, WorkoutType wt) {
        // readiness is 0..100
        if (readiness >= 70) {
            return 0.0;
        }

        boolean hardRun = (wt == WorkoutType.INTERVAL_RUN || wt == WorkoutType.TEMPO_RUN || wt == WorkoutType.LONG_RUN);

        if (readiness < 40) {
            // very low readiness: strongly avoid intensity
            if (hardRun) {
                return 25.0;
            }
            if (wt == WorkoutType.EASY_RUN) {
                return 8.0;
            }
            return 0.0; // mobility/gym/rest fine
        }

        // 40..69: moderate caution
        if (hardRun) {
            double x = (70 - readiness) / 30.0;
            return 2.0 + 8.0 * clamp01(x);
        }
        if (wt == WorkoutType.EASY_RUN && readiness < 55) {
            return 3.0;
        }

        return 0.0;
    }

    private WorkoutType mapWorkoutForReadiness(WorkoutType workoutType, int readiness) {
        if (readiness < 40) {
            return mapForVeryLowReadiness(workoutType);
        }
        return mapForModerateReadiness(workoutType);
    }

    private WorkoutType mapForVeryLowReadiness(WorkoutType wt) {
        return switch (wt) {
            case INTERVAL_RUN, TEMPO_RUN, LONG_RUN -> WorkoutType.MOBILITY;
            default -> wt;
        };
    }

    private WorkoutType mapForModerateReadiness(WorkoutType wt) {
        return switch (wt) {
            case INTERVAL_RUN -> WorkoutType.TEMPO_RUN; // still quality, just reduced
            case TEMPO_RUN -> WorkoutType.TEMPO_RUN;    // keep
            case LONG_RUN -> WorkoutType.LONG_RUN;      // keep (reduce volume via constraints/forecaster)
            default -> wt;
        };
    }

    private String utcTimeStringFor(LocalDate date, int localHour) {
        // local date/time in Vienna
        var local = date.atTime(localHour, 0).atZone(ZONE);
        var utc = local.withZoneSameInstant(ZoneId.of("UTC"));
        return utc.toLocalDateTime().withMinute(0).withSecond(0).withNano(0)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    }

    private CompactWeatherDto buildCompactWeatherDtoForDay(
            LocalDate day,
            double latitude,
            double longitude,
            int localHour
    ) {
        // default fallback weather response + score if anything fails
        try {
            String timeUtc = utcTimeStringFor(day, localHour);

            WeatherResponse wr = weatherService.getWeatherAtTime(latitude, longitude, timeUtc);
            double weatherScore = weatherService.calculateWeatherScore(wr);

            return new CompactWeatherDto(
                    weatherScore,
                    wr.getTemperature2m(),
                    wr.getWindSpeed10m(),
                    wr.getPrecipitation(),
                    wr.getRelativeHumidity(),
                    weatherService.estimatePerformancePenalty(wr),
                    weatherService.evaluateWeatherScore(weatherScore),
                    weatherService.buildWeatherDescription(wr)
            );
        } catch (Exception e) {
            return new CompactWeatherDto(
                    0.6,
                    null, null, null, null, null,
                    "Weather unavailable",
                    null
            );

        }
    }

    private double weatherPenalty(Double weatherScore, WorkoutType wt) {

        if (weatherScore == null) {
            return 0.0;
        }

        // only penalize outdoor runs
        boolean outdoorRun = (wt == WorkoutType.EASY_RUN || wt == WorkoutType.TEMPO_RUN
                || wt == WorkoutType.INTERVAL_RUN || wt == WorkoutType.LONG_RUN);

        if (!outdoorRun) {
            return 0.0;
        }

        if (weatherScore >= 0.7) {
            return 0.0; // very good
        }
        if (weatherScore >= 0.5) {
            return 2.0; // acceptable
        }
        if (weatherScore >= 0.3) {
            return 8.0; // bad
        }
        return 18.0; // very bad
    }

    private WorkoutType mapWorkoutForWeather(WorkoutType wt, Double weatherScore) {

        if (weatherScore == null) {
            return WorkoutType.GYM_PREHAB;
        }

        boolean run = (wt == WorkoutType.EASY_RUN || wt == WorkoutType.TEMPO_RUN
                || wt == WorkoutType.INTERVAL_RUN || wt == WorkoutType.LONG_RUN);

        if (!run) {
            return wt;
        }

        if (weatherScore < 0.2) {
            return WorkoutType.GYM_PREHAB;  // too dangerous outside
        }
        if (weatherScore < 0.3) {
            return WorkoutType.MOBILITY;    // very unpleasant
        }
        return wt;
    }

    private List<CompactWeatherDto> precomputeWeather(LocalDate startDate, double latitude, double longitude, int localHour) {
        List<CompactWeatherDto> weather = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate d = startDate.plusDays(i);
            weather.add(buildCompactWeatherDtoForDay(d, latitude, longitude, localHour));
        }
        return weather;
    }

    private WorkoutType effectiveWorkoutType(WorkoutType planned, double injuryIndex, int readiness, Double weatherScore, ExperienceConfig cfg) {
        WorkoutType wt = gateByExperience(planned, cfg);

        wt = mapWorkoutForInjury(wt, injuryIndex);
        wt = mapWorkoutForReadiness(wt, readiness);
        wt = mapWorkoutForWeather(wt, weatherScore);

        return wt;
    }


    private int clampSims(Integer sims) {
        if (sims == null) {
            return 120;
        }
        return Math.max(20, Math.min(400, sims));
    }

    private long defaultSeed(Long seed) {
        return seed == null ? 42L : seed;
    }

    private <T> List<T> safeList(SupplierWithException<List<T>> s) {
        try {
            return s.get();
        } catch (Exception e) {
            return List.of();
        }
    }

    private RouteDto computeRouteDtoForDay(
            ApplicationUser user,
            LocalDate date,
            WorkoutType wt,
            LoadDistributionDto load,
            int readiness,
            String planId
    ) {
        // Only runs get a route
        boolean isRun = switch (wt) {
            case EASY_RUN, TEMPO_RUN, INTERVAL_RUN, LONG_RUN -> true;
            default -> false;
        };

        if (!isRun) {
            return null;
        }

        // 1) base distance from existing logic
        RouteDto base = routeGenerationService.generateRouteDetails(user, wt, readiness);
        if (base == null || base.getDistance() == null) {
            return null;
        }
        double baseMeters = base.getDistance();


        // 2) determine jitter %
        double jitterPct = switch (confidenceFromStd(load)) {
            case "high" -> 0.05;
            case "medium" -> 0.08;
            default -> 0.12;
        };

        // 3) tighten bands for harder workouts
        if (wt == WorkoutType.INTERVAL_RUN || wt == WorkoutType.TEMPO_RUN) {
            jitterPct = Math.min(jitterPct, 0.06);
        }
        if (wt == WorkoutType.LONG_RUN) {
            jitterPct = Math.max(jitterPct, 0.07);
        }

        // 4) stable RNG per day
        long seed = stableSeed(planId, date, wt);
        Random rng = new Random(seed);

        double factor = 1.0 + (rng.nextDouble() * 2.0 - 1.0) * jitterPct; // [1-j, 1+j]
        double meters = baseMeters * factor;

        // 5) clamp to sensible bounds
        meters = clampRunDistanceMeters(wt, meters);

        // 6) keep pace/elevation estimates from your existing service
        int s = orsSeedFromLong(seed);
        s = (s == Integer.MIN_VALUE) ? 0 : Math.abs(s);

        return new RouteDto(meters, base.getPace(), base.getElevation(), s);
    }

    private long stableSeed(String planId, LocalDate date, WorkoutType wt) {
        // simple stable hash -> long
        String key = planId + ":" + date + ":" + wt.name();
        return key.hashCode() * 2654435761L; // stable enough, deterministic
    }

    private int orsSeedFromLong(long seed) {
        long z = seed;
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return (int) z; // 32-bit signed, ORS accepts int
    }

    private double clampRunDistanceMeters(WorkoutType wt, double meters) {
        double min;
        double max;
        switch (wt) {
            case INTERVAL_RUN -> {
                min = 4000;
                max = 12000;
            }
            case TEMPO_RUN -> {
                min = 5000;
                max = 16000;
            }
            case EASY_RUN -> {
                min = 3000;
                max = 14000;
            }
            case LONG_RUN -> {
                min = 9000;
                max = 28000;
            }
            default -> {
                min = 0;
                max = 100000;
            }
        }
        return Math.max(min, Math.min(max, meters));
    }

    private PlannerProfile buildPlannerProfile(ApplicationUser user, List<Integer> recentLoads) {
        List<Integer> last = (recentLoads != null && recentLoads.size() > 28)
                ? recentLoads.subList(recentLoads.size() - 28, recentLoads.size())
                : (recentLoads == null ? List.of() : recentLoads);

        double mean = last.stream().mapToDouble(x -> x == null ? 0 : x).average().orElse(0.0);

        double std = 0.0;
        if (last.size() >= 2) {
            double var = 0.0;
            for (Integer xi : last) {
                double x = (xi == null ? 0 : xi);
                double d = x - mean;
                var += d * d;
            }
            var /= (last.size() - 1);
            std = Math.sqrt(var);
        }

        double cv = std / Math.max(1.0, mean);

        // map cv -> consistency in 0..1 (tune these two numbers freely)
        // cv ~0.15 -> very consistent; cv >=0.7 -> chaotic
        double consistency = 1.0 - clamp01((cv - 0.15) / (0.70 - 0.15));

        // risk aversion higher when consistency is low
        double riskAversion = 0.25 + (1.10 * (1.0 - consistency)); // ~0.25..1.35

        // uncertaintyScale higher when consistency low
        double uncertaintyScale = 0.85 + (0.75 * (1.0 - consistency)); // ~0.85..1.6

        int activeTrainingDays = (user.getActiveWeekdays() == null) ? 0 : user.getActiveWeekdays().size();
        int trainDays = Math.max(3, Math.min(6, activeTrainingDays == 0 ? 4 : activeTrainingDays));

        double histWeek = mean * 7.0;

        double dayBase = 35.0; // or scale by experience level
        double floorWeek = dayBase * trainDays;

        // Only apply floor when history is basically missing
        double targetWeeklyLoad = (histWeek < 30.0) ? floorWeek : Math.max(histWeek, 0.8 * floorWeek);

        return new PlannerProfile(
                mean,
                std,
                cv,
                consistency,
                riskAversion,
                targetWeeklyLoad,
                uncertaintyScale
        );
    }


    private double clamp01(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }

    private double templatePrior(PlannerProfile profile, List<WorkoutType> plannedTemplate) {
        if (plannedTemplate == null || plannedTemplate.size() != 7) {
            return 0.0;
        }

        int hard = 0;
        int longRuns = 0;
        int runDays = 0;
        int gym = 0;
        int mob = 0;
        int rest = 0;

        for (WorkoutType wt : plannedTemplate) {
            if (wt == null) {
                continue;
            }

            boolean isRun = (wt == WorkoutType.EASY_RUN || wt == WorkoutType.TEMPO_RUN
                    || wt == WorkoutType.INTERVAL_RUN || wt == WorkoutType.LONG_RUN);

            if (isRun) {
                runDays++;
            }

            switch (wt) {
                case INTERVAL_RUN, TEMPO_RUN -> hard++;
                case LONG_RUN -> {
                    hard++;
                    longRuns++;
                }
                case GYM_PREHAB -> gym++;
                case MOBILITY -> mob++;
                default -> rest++;
            }
        }

        double s = 0.0;

        // Encourage exactly 1 long run if you have enough run days
        if (runDays >= 3) {
            s += (longRuns == 1 ? 8.0 : (longRuns == 0 ? -10.0 : -12.0));
        }

        // Prefer 0-2 hard days depending on consistency
        double preferredHard = (profile.consistency() > 0.6) ? 2 : 1;
        s -= 6.0 * Math.abs(hard - preferredHard);

        // Too few rest days is bad (especially for inconsistent athletes)
        int minRest = (profile.consistency() > 0.7) ? 1 : 2;
        if (rest < minRest) {
            s -= (minRest - rest) * 10.0;
        }

        // Too much gym/mob crowds out running
        if (gym > 2) {
            s -= (gym - 2) * 6.0;
        }
        if (mob > 2) {
            s -= (mob - 2) * 4.0;
        }

        // Encourage “soft buffer” around long/hard days
        s += bufferBonus(plannedTemplate);

        return s;
    }

    private double bufferBonus(List<WorkoutType> t) {
        double s = 0.0;
        for (int i = 0; i < 7; i++) {
            WorkoutType wt = t.get(i);
            if (wt == WorkoutType.LONG_RUN) {
                if (i > 0 && (t.get(i - 1) == WorkoutType.EASY_RUN || t.get(i - 1) == WorkoutType.REST_DAY)) {
                    s += 4.0;
                }
                if (i < 6 && (t.get(i + 1) == WorkoutType.REST_DAY || t.get(i + 1) == WorkoutType.MOBILITY)) {
                    s += 4.0;
                }
            }
            if (wt == WorkoutType.INTERVAL_RUN || wt == WorkoutType.TEMPO_RUN) {
                if (i > 0 && (t.get(i - 1) == WorkoutType.EASY_RUN || t.get(i - 1) == WorkoutType.REST_DAY)) {
                    s += 2.5;
                }
                if (i < 6 && (t.get(i + 1) == WorkoutType.EASY_RUN || t.get(i + 1) == WorkoutType.REST_DAY
                        || t.get(i + 1) == WorkoutType.MOBILITY)) {
                    s += 2.5;
                }
            }
        }
        return s;
    }


    private double uncertaintyMultiplier(
            PlannerProfile profile,
            double injuryIndex,
            int readiness,
            Double weatherScore,
            WorkoutType wt
    ) {
        double m = profile.uncertaintyScale();

        // harder workouts are more uncertain
        if (wt == WorkoutType.INTERVAL_RUN) {
            m *= 1.25;
        }
        if (wt == WorkoutType.TEMPO_RUN) {
            m *= 1.15;
        }
        if (wt == WorkoutType.LONG_RUN) {
            m *= 1.18;
        }

        // injury / readiness increase uncertainty (more “things can go wrong”)
        if (injuryIndex >= 0.7) {
            m *= 1.25;
        } else if (injuryIndex >= 0.4) {
            m *= 1.10;
        }

        if (readiness < 40) {
            m *= 1.25;
        } else if (readiness < 55) {
            m *= 1.10;
        }

        // bad weather makes outdoor execution more variable
        if (weatherScore != null) {
            if (weatherScore < 0.3) {
                m *= 1.25;
            } else if (weatherScore < 0.5) {
                m *= 1.10;
            }
        }

        return Math.max(0.7, Math.min(2.2, m));
    }

    private double weekTargetMultiplier(double injuryIndex, int readiness) {
        double m = 1.0;

        // Injury gating
        if (injuryIndex >= 0.7) {
            m *= 0.55;
        } else if (injuryIndex >= 0.4) {
            m *= 0.75;
        } else {
            m *= 1.0;
        }

        // Readiness gating
        if (readiness < 40) {
            m *= 0.70;
        } else if (readiness < 55) {
            m *= 0.85;
        } else if (readiness > 75) {
            m *= 1.05;
        }

        return Math.max(0.45, Math.min(1.10, m));
    }

    private double missingKeySessionsPenalty(
            List<WorkoutType> t,
            PlannerProfile p,
            double injury,
            int readiness,
            ExperienceConfig exp // NEW
    ) {
        final int longCnt = (int) t.stream().filter(w -> w == WorkoutType.LONG_RUN).count();
        final int qualCnt = (int) t.stream().filter(w -> w == WorkoutType.TEMPO_RUN || w == WorkoutType.INTERVAL_RUN).count();
        final int runCnt  = (int) t.stream().filter(this::isRun).count();
        final int restCnt = (int) t.stream().filter(w -> w == WorkoutType.REST_DAY).count();

        // Gate expectations if user is high injury / low readiness
        double gate = (injury > 0.7 || readiness < 35) ? 0.4 :
                (injury > 0.5 || readiness < 50) ? 0.7 : 1.0;

        double s = 0.0;

        // --- LONG RUN expectation ---
        // Only require long if experience says long is allowed AND enough run days exist
        boolean expectLong = exp.allowLong() && runCnt >= exp.minRunDaysForLong();
        if (expectLong && longCnt == 0) {
            s -= 18.0 * gate; // was -35, too brutal for casual/cold start
        }

        // --- QUALITY expectation ---
        // Only require quality if this level actually expects quality
        boolean expectQuality = exp.maxQualitySessions() >= 1 && (exp.allowTempo() || exp.allowIntervals());
        if (expectQuality && qualCnt == 0) {
            s -= 16.0 * gate; // was -30 (way too dominant)
        }

        // If quality expected but the week is tiny, don’t double-punish
        if (expectQuality && qualCnt == 0 && runCnt <= 3) {
            s -= 6.0 * gate;
        }

        // Rest days: casual/beginner can have more rest without being “bad”
        int softMaxRest = (exp.maxQualitySessions() == 0) ? 2 : 1;
        if (restCnt > softMaxRest) {
            s -= 6.0 * (restCnt - softMaxRest) * gate;
        }

        return s;
    }


    private double stimulusWeight(WorkoutType wt) {
        return switch (wt) {
            case REST_DAY -> 0.0;
            case MOBILITY -> 0.2;
            case GYM_PREHAB -> 0.35;
            case EASY_RUN -> 1.0;
            case LONG_RUN -> 1.15;
            case TEMPO_RUN -> 1.25;
            case INTERVAL_RUN -> 1.35;
            default -> 1.0;
        };
    }

    private record ExperienceConfig(
            int minRunDaysForLong,
            int maxQualitySessions,   // tempo/interval count
            boolean allowIntervals,
            boolean allowTempo,
            boolean allowLong,
            int maxHardPerWeek,       // hard = tempo/interval/long
            double easyBias           // higher => prefers easy
    ) {
    }

    private ExperienceConfig expCfg(ApplicationUser user) {
        ExperienceLevel lvl = user.getExperienceLevel() == null
                ? ExperienceLevel.INTERMEDIATE
                : user.getExperienceLevel();

        return switch (lvl) {
            case BEGINNER -> new ExperienceConfig(
                    4, 0,
                    false, false, true,
                    1,
                    2.5
            );
            case CASUAL -> new ExperienceConfig(
                    3, 1,
                    false, true, true,
                    2,
                    1.6
            );
            case INTERMEDIATE -> new ExperienceConfig(
                    3, 1,
                    true, true, true,
                    3,
                    1.0
            );
            case ADVANCED -> new ExperienceConfig(
                    3, 2,
                    true, true, true,
                    4,
                    0.6
            );
            case COMPETITIVE_ATHLETE -> new ExperienceConfig(
                    3, 2,
                    true, true, true,
                    4,
                    0.3
            );
        };
    }

    private WorkoutType gateByExperience(WorkoutType wt, ExperienceConfig cfg) {
        if (wt == WorkoutType.INTERVAL_RUN && !cfg.allowIntervals()) {
            return WorkoutType.TEMPO_RUN;
        }
        if (wt == WorkoutType.TEMPO_RUN && !cfg.allowTempo()) {
            return WorkoutType.EASY_RUN;
        }

        // beginners/casual: keep long but prevent it turning into "hard overload"
        if (wt == WorkoutType.LONG_RUN && !cfg.allowLong()) {
            return WorkoutType.EASY_RUN;
        }

        return wt;
    }

    private double experienceTemplatePrior(ExperienceConfig cfg, List<WorkoutType> t) {
        int qual = (int) t.stream().filter(w -> w == WorkoutType.TEMPO_RUN || w == WorkoutType.INTERVAL_RUN).count();
        int hard = (int) t.stream().filter(this::isHard).count();

        double s = 0.0;

        // beginners: strongly penalize any quality
        if (cfg.maxQualitySessions() == 0) {
            s -= 40.0 * qual;
        }

        // competitive: penalize having *no* quality
        if (cfg.maxQualitySessions() >= 2 && qual == 0) {
            s -= 25.0;
        }

        // cap hard days based on cfg
        if (hard > cfg.maxHardPerWeek()) {
            s -= 12.0 * (hard - cfg.maxHardPerWeek());
        }

        // easy bias (makes beginner prefer easy, comp not care)
        int easy = (int) t.stream().filter(w -> w == WorkoutType.EASY_RUN).count();
        s += cfg.easyBias() * easy;

        return s;
    }

    private record TemplateGenCfg(
            int minRunDays,
            int maxQuality,
            boolean allowTempo,
            boolean allowIntervals,
            boolean allowLong,
            int maxGym,
            int maxMobility,
            boolean requireRestDay,
            int minRestDays,
            int minGymDays,
            int minMobilityDays
    ) {}

    private void enforceMinimums(WorkoutType[] week, TemplateGenCfg cfg) {
        // count helper
        java.util.function.IntSupplier restCount = () -> (int) java.util.Arrays.stream(week).filter(w -> w == WorkoutType.REST_DAY).count();
        java.util.function.IntSupplier gymCount  = () -> (int) java.util.Arrays.stream(week).filter(w -> w == WorkoutType.GYM_PREHAB).count();
        java.util.function.IntSupplier mobCount  = () -> (int) java.util.Arrays.stream(week).filter(w -> w == WorkoutType.MOBILITY).count();

        // 1) ensure min rest days by downgrading EASY_RUN first
        while (restCount.getAsInt() < cfg.minRestDays()) {
            int idx = lastIndexOf(week, WorkoutType.EASY_RUN);
            if (idx < 0) {
                idx = firstIndexOf(week, WorkoutType.MOBILITY); // next best to remove
            }
            if (idx < 0) {
                idx = firstIndexOf(week, WorkoutType.GYM_PREHAB);
            }
            if (idx < 0) {
                break; // don't destroy hard days here
            }
            week[idx] = WorkoutType.REST_DAY;
        }

        // 2) ensure min mobility: prefer converting REST, else convert EASY_RUN
        while (mobCount.getAsInt() < cfg.minMobilityDays()) {
            int idx = firstIndexOf(week, WorkoutType.REST_DAY);
            if (idx < 0) {
                idx = lastIndexOf(week, WorkoutType.EASY_RUN);
            }
            if (idx < 0) {
                break;
            }
            week[idx] = WorkoutType.MOBILITY;
        }

        // 3) ensure min gym: prefer converting REST, else convert EASY_RUN
        while (gymCount.getAsInt() < cfg.minGymDays()) {
            int idx = firstIndexOf(week, WorkoutType.REST_DAY);
            if (idx < 0) {
                idx = lastIndexOf(week, WorkoutType.EASY_RUN);
            }
            if (idx < 0) {
                break;
            }
            week[idx] = WorkoutType.GYM_PREHAB;
        }
    }

    private TemplateGenCfg cfgFor(ExperienceLevel lvl) {
        if (lvl == null) {
            lvl = ExperienceLevel.INTERMEDIATE;
        }

        return switch (lvl) {
            case BEGINNER -> new TemplateGenCfg(
                    2,   // minRunDays
                    0,   // maxQuality
                    false, false,
                    false, // allowLong
                    1, 2,  // maxGym, maxMobility
                    true,  // requireRestDay
                    2,     // minRestDays
                    0,     // minGymDays
                    1      // minMobilityDays
            );

            case CASUAL -> new TemplateGenCfg(
                    2,
                    1,
                    true, false, // tempo allowed, intervals not
                    true,        // allowLong
                    1, 2,        // maxGym, maxMobility
                    true,        // requireRestDay
                    1,           // minRestDays
                    0,           // minGymDays
                    1            // minMobilityDays
            );

            case INTERMEDIATE -> new TemplateGenCfg(
                    3,
                    1,
                    true, true,
                    true,
                    2, 1,        // maxGym, maxMobility
                    false,
                    1,           // minRestDays
                    1,           // minGymDays
                    0            // minMobilityDays
            );

            case ADVANCED -> new TemplateGenCfg(
                    4,
                    2,
                    true, true,
                    true,
                    2, 1,
                    false,
                    1,           // minRestDays
                    1,           // minGymDays
                    0            // minMobilityDays
            );

            case COMPETITIVE_ATHLETE -> new TemplateGenCfg(
                    4,
                    2,
                    true, true,
                    true,
                    1, 1,        // keep cross-training small
                    false,
                    0,           // minRestDays (they can run 6-7 if available)
                    0,           // minGymDays (optional)
                    0            // minMobilityDays (optional)
            );
        };
    }

    private boolean isColdStart(double ctl, double atl, List<Integer> recentLoads) {
        int nonZero = 0;
        if (recentLoads != null) {
            for (Integer x : recentLoads) {
                if (x != null && x > 0) {
                    nonZero++;
                }
            }
        }
        // any of these means “we basically know nothing”
        return nonZero < 5 || (ctl <= 5.0 && atl <= 5.0);
    }


    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    private record PlanChoice(
            int bestTemplateIndex,
            double bestScore,
            List<WorkoutType> bestTemplate,
            List<LoadDistributionDto> bestTsbDists,
            TrainingPlanDebugDto debug
    ) {
    }

    private record PlannerProfile(
            double meanDailyLoad,
            double stdDailyLoad,
            double cv,                 // coefficient of variation
            double consistency,         // 0..1 (1 = very consistent)
            double riskAversion,        // 0.2..1.2 (higher = more risk-averse)
            double targetWeeklyLoad,    // meanDailyLoad * 7
            double uncertaintyScale     // multiplies std in simulations
    ) {
    }

    private record SimResult(double avgUtility, JuliaDist utilDist, double riskAdjustedScore,
                             List<LoadDistributionDto> tsbDists) {
    }

    private record ScoreBreakdown(
            double simRiskAdj,
            double templatePrior,
            double missingPenalty,
            double expPrior,
            double shape,
            double total
    ) {}

    private void logBreakdown(int idx, List<WorkoutType> eff, ScoreBreakdown b) {
        log.info("score {} simRiskAdj={} templatePrior={} missing={} expPrior={} shape={} TOTAL={} eff={}",
                idx,
                round2(b.simRiskAdj),
                round2(b.templatePrior),
                round2(b.missingPenalty),
                round2(b.expPrior),
                round2(b.shape),
                round2(b.total),
                eff
        );
    }

    private double round2(double x) {
        return Math.round(x * 100.0) / 100.0;
    }

    private List<List<WorkoutType>> postProcessTemplates(
            List<List<WorkoutType>> templates,
            ExperienceLevel exp,
            boolean coldStart
    ) {
        return templates.stream()
                .map(t -> normalizeWeek(t, exp, coldStart))
                .filter(t -> isWeekValid(t, exp, coldStart))
                .toList();
    }

    private List<WorkoutType> normalizeWeek(List<WorkoutType> week, ExperienceLevel exp, boolean coldStart) {
        List<WorkoutType> w = new java.util.ArrayList<>(week);

        if (coldStart) {
            // 1) ban tempo/interval entirely
            for (int i = 0; i < w.size(); i++) {
                if (w.get(i) == WorkoutType.TEMPO_RUN || w.get(i) == WorkoutType.INTERVAL_RUN) {
                    w.set(i, WorkoutType.EASY_RUN);
                }
            }

            // 2) beginners/casual: long run becomes easy if you want super safe start
            if (exp == ExperienceLevel.BEGINNER || exp == ExperienceLevel.CASUAL) {
                for (int i = 0; i < w.size(); i++) {
                    if (w.get(i) == WorkoutType.LONG_RUN) {
                        w.set(i, WorkoutType.EASY_RUN);
                    }
                }
            }

            // 3) enforce min recovery days in cold start
            ensureAtLeastN(w, WorkoutType.REST_DAY, minRestDays(exp));
            ensureAtLeastN(w, WorkoutType.MOBILITY, 1);

            // 4) optional: ensure at least one non-run strength day for casual+
            if (exp != ExperienceLevel.BEGINNER) {
                ensureAtLeastN(w, WorkoutType.GYM_PREHAB, 1);
            }

            // 5) avoid >2 consecutive runs
            capRunStreak(w, 2);
        }

        return java.util.List.copyOf(w);
    }

    private boolean isWeekValid(List<WorkoutType> w, ExperienceLevel exp, boolean coldStart) {
        int rest = count(w, WorkoutType.REST_DAY);
        int mob  = count(w, WorkoutType.MOBILITY);
        int gym  = count(w, WorkoutType.GYM_PREHAB);
        int hard = count(w, WorkoutType.TEMPO_RUN) + count(w, WorkoutType.INTERVAL_RUN);
        int longR = count(w, WorkoutType.LONG_RUN);

        if (coldStart) {
            if (hard > 0) {
                return false;                 // should be zero after normalize anyway
            }
            if (rest < minRestDays(exp)) {
                return false;  // enforce actual recovery
            }
            if (mob < 1) {
                return false;
            }
            if ((exp == ExperienceLevel.BEGINNER || exp == ExperienceLevel.CASUAL) && longR > 0) {
                return false;
            }
            if (maxConsecutiveRuns(w) > 2) {
                return false;
            }
        }

        return true;
    }

    private int minRestDays(ExperienceLevel exp) {
        if (exp == null) {
            return 2;
        }
        return switch (exp) {
            case BEGINNER -> 3;
            case CASUAL -> 2;
            default -> 1;
        };
    }

    private int count(List<WorkoutType> w, WorkoutType t) {
        int c = 0;
        for (WorkoutType x : w) {
            if (x == t) {
                c++;
            }
        }
        return c;
    }

    private void ensureAtLeastN(List<WorkoutType> w, WorkoutType t, int n) {
        int have = count(w, t);
        for (int i = 0; have < n && i < w.size(); i++) {
            // replace easiest “filler” first
            if (w.get(i) == WorkoutType.EASY_RUN) {
                w.set(i, t);
                have++;
            }
        }
        // if still not enough, replace any non-essential day
        for (int i = 0; have < n && i < w.size(); i++) {
            if (w.get(i) != WorkoutType.LONG_RUN && w.get(i) != WorkoutType.REST_DAY) {
                w.set(i, t);
                have++;
            }
        }
    }

    private void capRunStreak(List<WorkoutType> w, int maxStreak) {
        int streak = 0;
        for (int i = 0; i < w.size(); i++) {
            if (isRun(w.get(i))) {
                streak++;
                if (streak > maxStreak) {
                    w.set(i, WorkoutType.REST_DAY);
                    streak = 0;
                }
            } else {
                streak = 0;
            }
        }
    }

    private int maxConsecutiveRuns(List<WorkoutType> w) {
        int best = 0;
        int cur = 0;
        for (WorkoutType t : w) {
            if (isRun(t)) {
                cur++;
                best = Math.max(best, cur);
            } else {
                cur = 0;
            }
        }
        return best;
    }







}
