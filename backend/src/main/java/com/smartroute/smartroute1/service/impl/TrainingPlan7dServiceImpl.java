package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.CompactWeatherDto;
import com.smartroute.smartroute1.endpoint.dto.GymWorkoutDto;
import com.smartroute.smartroute1.endpoint.dto.RouteDto;
import com.smartroute.smartroute1.endpoint.dto.ViewInjuryDto;
import com.smartroute.smartroute1.endpoint.dto.AthleteStatusDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.DailySummary;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.DayDebugDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.FitUserModelResponse;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.JuliaDist;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.LoadDistributionDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.PlannedDayDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.TemplateScoreDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlan7dDto;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlanDebugDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Injuries;
import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.entity.enums.BodyPart;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.DailyAggregationService;
import com.smartroute.smartroute1.service.DaySelectorService;
import com.smartroute.smartroute1.service.FatigueAndOverloadService;
import com.smartroute.smartroute1.service.GymWorkoutSelectorService;
import com.smartroute.smartroute1.service.InjuryAwareTrainingService;
import com.smartroute.smartroute1.service.LoadForecaster;
import com.smartroute.smartroute1.service.ReadinessScoreService;
import com.smartroute.smartroute1.service.RouteGenerationService;
import com.smartroute.smartroute1.service.TrainingPlan7dService;
import com.smartroute.smartroute1.service.TrainingPlanStore;
import com.smartroute.smartroute1.service.UserModelStore;
import com.smartroute.smartroute1.service.WeatherService;
import com.smartroute.smartroute1.util.ForecastState;
import com.smartroute.smartroute1.util.LoadConstraints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
public class TrainingPlan7dServiceImpl implements TrainingPlan7dService {

    private static final ZoneId zone = ZoneId.of("Europe/Vienna");
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

    /**
     * Creates the service with the system Vienna clock.
     */
    @Autowired
    public TrainingPlan7dServiceImpl(
            UserRepository userRepository,
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
        this(
                userRepository,
                dailyAggregationService,
                loadForecaster,
                fatigueAndOverloadService,
                Clock.system(zone),
                injuryAwareTrainingService,
                readinessScoreService,
                weatherService,
                daySelectorService,
                gymWorkoutSelectorService,
                routeGenerationService,
                trainingPlanStore,
                userModelStore
        );
    }

    /**
     * Creates the service with an injected clock (useful for tests).
     */
    public TrainingPlan7dServiceImpl(
            UserRepository userRepository,
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
            UserModelStore userModelStore
    ) {
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

    // =====================================================================
    // 1) PUBLIC ENTRYPOINT
    // =====================================================================

    /**
     * Builds the next 7 days training plan (with optional caching and debug).
     */
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

        LocalDate today = LocalDate.now(clock);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        String planId = "week:" + weekStart;

        Optional<TrainingPlan7dDto> cached = loadFromCache(email, planId, debug, overrides, regen);

        if (cached.isPresent() && !regen) {
            TrainingPlan7dDto cachedPlan = cached.get();

            List<Injuries> injuriesNow = safeList(() -> injuryAwareTrainingService.findInjuriesByEmail(email));
            long currentSignature = injuriesSignatureFromEntities(injuriesNow);

            if (!shouldPatchForInjuryChange(cachedPlan, currentSignature)) {
                return cachedPlan;
            }

            LocalDate cutoffDate = computeCutoffDate(today, injuriesNow);

            Optional<LocalDate> clampedOpt = clampCutoffToWeek(cutoffDate, weekStart);
            if (clampedOpt.isEmpty()) {
                return cachedPlan;
            }

            LocalDate clampedCutoff = clampedOpt.get();

            TrainingPlan7dDto recomputedFromCutoff = computePlanNoCache(
                    user,
                    email,
                    latitude,
                    longitude,
                    debug,
                    sims,
                    seed,
                    overrides,
                    clampedCutoff,
                    planId
            );

            TrainingPlan7dDto merged = mergeByDateKeepingPrefix(
                    cachedPlan,
                    recomputedFromCutoff,
                    clampedCutoff
            );

            trainingPlanStore.put(email, planId, merged);

            return merged;
        }

        OverridesResolved resolved = resolveOverrides(user, email, today, overrides, seed);

        List<CompactWeatherDto> weatherPerDay = precomputeWeather(today, latitude, longitude, 18);
        List<Integer> recentLoads = resolved.recentLoads();

        PlannerProfile profile = buildPlannerProfile(user, recentLoads);

        ForecastState initialState = new ForecastState(resolved.ctl(), resolved.atl());
        boolean coldStart = isColdStart(initialState.ctl(), initialState.atl(), recentLoads);

        log.info(
                "7d inputs USED planId={} seed={} sims={} injuryIndex={} readiness={} ctl={} atl={} recentLoads(n={}, head={})",
                planId, seed, sims,
                resolved.injuryIndex(), resolved.readiness(), initialState.ctl(), initialState.atl(),
                recentLoads.size(),
                recentLoads.stream().limit(7).toList()
        );

        Optional<FitUserModelResponse> fitModelOpt = loadFitModelFromCache(email, weekStart, regen);

        List<List<WorkoutType>> templates = generateTemplates(user, today, coldStart);

        PlanChoice choice = chooseBestPlan(
                user,
                today,
                templates,
                initialState,
                recentLoads,
                resolved.injuryIndex(),
                resolved.readiness(),
                weatherPerDay,
                resolved.constraints(),
                sims,
                seed,
                debug,
                profile,
                fitModelOpt
        );

        List<PlannedDayDto> days = materializePlanWithTsbDists(
                user,
                today,
                choice.bestTemplate(),
                initialState,
                recentLoads,
                choice.bestTsbDists(),
                resolved.injuryIndex(),
                resolved.injuriesMap(),
                resolved.readiness(),
                weatherPerDay,
                resolved.constraints(),
                planId
        );

        TrainingPlan7dDto dto = new TrainingPlan7dDto(days);
        dto.setPlanId(planId);
        dto.setDebug(choice.debug());

        dto.setGeneratedAt(java.time.Instant.now(clock));

        List<Injuries> injuriesNow = safeList(() -> injuryAwareTrainingService.findInjuriesByEmail(email));
        dto.setCurrentAthleteStatus(buildAthleteStatusDto(resolved, injuriesNow));


        trainingPlanStore.put(email, planId, dto);
        return dto;
    }

    // =====================================================================
    // 2) CACHE + OVERRIDES
    // =====================================================================

    /**
     * Loads a plan from cache (unless regen); also removes cache when regen is requested.
     */
    private Optional<TrainingPlan7dDto> loadFromCache(
            String email,
            String planId,
            boolean debug,
            DevOverrides overrides,
            boolean regen
    ) {
        if (!regen) {
            Optional<TrainingPlan7dDto> cached = trainingPlanStore.get(email, planId);
            if (cached.isPresent()) {
                log.info("7d plan CACHE HIT planId={} debug={} overrides={}", planId, debug, overrides);
                return cached;
            }
            return Optional.empty();
        }

        log.info("7d plan RECOMPUTE planId={} debug={} regen={} overrides={}", planId, debug, regen, overrides);
        trainingPlanStore.remove(email, planId);
        return Optional.empty();
    }

    /**
     * Loads a cached fit-model response for the current week if available and allowed.
     */
    private Optional<FitUserModelResponse> loadFitModelFromCache(
            String email,
            LocalDate weekStart,
            boolean regen
    ) {
        String modelKey = "fit:" + weekStart;
        if (regen) {
            return Optional.empty();
        }
        return userModelStore.get(email, modelKey);
    }

    /**
     * Applies DEV overrides and computes injury/readiness/constraints/history/ctl/atl.
     */
    private OverridesResolved resolveOverrides(
            ApplicationUser user,
            String email,
            LocalDate today,
            DevOverrides overrides,
            long seed
    ) {
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

        List<Injuries> injuries = safeList(() -> injuryAwareTrainingService.findInjuriesByEmail(email));
        Map<BodyPart, Double> injuriesMap = injuryAwareTrainingService.calculateInjuriesMap(injuries);

        LoadConstraints constraints = new LoadConstraints(
                injuryAwareTrainingService.calculateIntensityScaling(injuryIndex),
                injuryAwareTrainingService.calculateVolumeScaling(injuryIndex),
                injuryAwareTrainingService.calculateHighImpactPenalty(injuryIndex)
        );

        List<Integer> recentLoads = resolveRecentLoads(user, historyDays, historyMean, historyStd, seed);

        double ctl = (ctlOverride != null) ? ctlOverride : safe(() -> fatigueAndOverloadService.currentCtl(user), 0.0);
        double atl = (atlOverride != null) ? atlOverride : safe(() -> fatigueAndOverloadService.currentAtl(user), 0.0);

        return new OverridesResolved(injuryIndex, readiness, injuriesMap, constraints, recentLoads, ctl, atl);
    }

    /**
     * Uses real history or deterministic fake history if dev overrides request it.
     */
    private List<Integer> resolveRecentLoads(
            ApplicationUser user,
            Integer historyDays,
            Double historyMean,
            Double historyStd,
            long seed
    ) {
        if (historyDays != null && historyDays > 0) {
            double mean = (historyMean != null) ? historyMean : 35.0;
            double std = (historyStd != null) ? historyStd : 10.0;
            return fakeRecentLoads(historyDays, mean, std, seed);
        }

        List<DailySummary> history = dailyAggregationService.getDailySummaries(user, 60);
        return history.stream().map(DailySummary::getTotalLoad).toList();
    }

    /**
     * Generates deterministic fake recent loads (non-negative normal).
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

    /**
     * Clamps sims to a safe range so Monte Carlo doesn’t explode runtime.
     */
    private int clampSims(Integer sims) {
        if (sims == null) {
            return 120;
        }
        return Math.max(20, Math.min(400, sims));
    }

    /**
     * Returns a deterministic default seed when none is provided.
     */
    private long defaultSeed(Long seed) {
        if (seed == null) {
            return 42L;
        }
        return seed;
    }

    // =====================================================================
    // 3) TEMPLATE GENERATION
    // =====================================================================

    /**
     * Generates candidate weekly workout templates (planned types) and post-processes them.
     */
    private List<List<WorkoutType>> generateTemplates(ApplicationUser user, LocalDate startDate, boolean coldStart) {
        TemplateGenCfg cfg = cfgFor(user.getExperienceLevel());

        List<Integer> availableIdx = computeAvailableTrainingDays(user, startDate);

        if (availableIdx.isEmpty()) {
            return List.of(List.of(
                    WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY,
                    WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY,
                    WorkoutType.REST_DAY
            ));
        }

        List<List<Integer>> runIdxVariants = runDaySubsets(availableIdx, cfg);

        List<List<WorkoutType>> out = new ArrayList<>();

        for (List<Integer> runIdx : runIdxVariants) {
            int runN = runIdx.size();

            if (runN < cfg.minRunDays()) {
                out.add(buildSimpleEasyWeekFromAvailability(availableIdx, runIdx, cfg));
                continue;
            }

            int numIntensity = switch (runN) {
                case 0, 1, 2 -> 0;
                case 3, 4 -> 1;
                default -> 2;
            };
            numIntensity = Math.min(numIntensity, cfg.maxQuality());

            boolean includeLong = cfg.allowLong() && (runN >= 3);

            List<Integer> longPosChoices = includeLong
                    ? List.of(runN - 1, Math.max(0, runN - 2))
                    : List.of();

            List<int[]> intensityPosChoices = buildIntensityPositionChoices(numIntensity, runN);
            List<WorkoutType[]> intensityTypeVariants = buildIntensityTypeVariants(cfg, numIntensity);

            List<GymMobStrategy> strategies = strategiesFor(user.getExperienceLevel());

            List<Integer> longPosLoop = includeLong
                    ? longPosChoices
                    : Collections.singletonList(null);

            for (Integer longPos : longPosLoop) {
                for (int[] intenPos : intensityPosChoices) {
                    for (WorkoutType[] intenTypes : intensityTypeVariants) {
                        for (GymMobStrategy strat : strategies) {
                            List<WorkoutType> template = buildTemplate(
                                    availableIdx,
                                    runIdx,
                                    runN,
                                    longPos,
                                    intenPos,
                                    intenTypes,
                                    strat,
                                    cfg
                            );
                            if (template != null) {
                                out.add(template);
                            }
                        }
                    }
                }
            }
        }

        out = postProcessTemplates(out, user.getExperienceLevel(), coldStart);

        return out.stream().distinct().limit(24).toList();
    }

    /**
     * Computes which weekdays are “available” for training (not necessarily “must run”).
     */
    private List<Integer> computeAvailableTrainingDays(ApplicationUser user, LocalDate startDate) {
        List<Integer> availableIdx = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = startDate.plusDays(i);
            if (daySelectorService.isTrainingDay(d, user)) {
                availableIdx.add(i);
            }
        }
        return availableIdx;
    }

    /**
     * Returns possible intensity placements within the run-day list.
     */
    private List<int[]> buildIntensityPositionChoices(int numIntensity, int runN) {
        List<int[]> intensityPosChoices = new ArrayList<>();
        if (numIntensity == 0) {
            intensityPosChoices.add(new int[]{});
            return intensityPosChoices;
        }

        if (numIntensity == 1) {
            intensityPosChoices.add(new int[]{0});
            intensityPosChoices.add(new int[]{Math.min(1, runN - 1)});
            intensityPosChoices.add(new int[]{Math.max(0, runN / 2)});
            return intensityPosChoices;
        }

        intensityPosChoices.add(new int[]{0, Math.max(1, runN / 2)});
        intensityPosChoices.add(new int[]{0, Math.max(1, runN - 3)});
        intensityPosChoices.add(new int[]{1, Math.max(2, runN - 3)});
        return intensityPosChoices;
    }

    /**
     * Returns possible intensity type combinations (tempo/interval) for the week.
     */
    private List<WorkoutType[]> buildIntensityTypeVariants(TemplateGenCfg cfg, int numIntensity) {
        if (numIntensity == 0) {
            return Collections.singletonList(new WorkoutType[]{});
        }

        if (!cfg.allowIntervals() && cfg.allowTempo()) {
            return Collections.singletonList(
                    (numIntensity == 1)
                            ? new WorkoutType[]{WorkoutType.TEMPO_RUN}
                            : new WorkoutType[]{WorkoutType.TEMPO_RUN, WorkoutType.TEMPO_RUN}
            );
        }

        if (numIntensity == 1) {
            return List.of(
                    new WorkoutType[]{WorkoutType.TEMPO_RUN},
                    new WorkoutType[]{WorkoutType.INTERVAL_RUN}
            );
        }

        return List.of(
                new WorkoutType[]{WorkoutType.TEMPO_RUN, WorkoutType.INTERVAL_RUN},
                new WorkoutType[]{WorkoutType.INTERVAL_RUN, WorkoutType.TEMPO_RUN},
                new WorkoutType[]{WorkoutType.TEMPO_RUN, WorkoutType.TEMPO_RUN}
        );
    }

    /**
     * Returns gym strategy candidates for the athlete level.
     */
    private List<GymMobStrategy> strategiesFor(ExperienceLevel level) {
        ExperienceLevel lvl = (level == null) ? ExperienceLevel.INTERMEDIATE : level;

        return switch (lvl) {
            case BEGINNER -> List.of(GymMobStrategy.GYM_ON_EARLIEST_NONRUN);
            case CASUAL -> List.of(GymMobStrategy.GYM_AFTER_INTENSITY, GymMobStrategy.GYM_ON_EARLIEST_NONRUN);
            case INTERMEDIATE, ADVANCED -> List.of(
                    GymMobStrategy.GYM_BEFORE_LONG,
                    GymMobStrategy.GYM_AFTER_INTENSITY,
                    GymMobStrategy.GYM_ON_EARLIEST_NONRUN
            );
            case COMPETITIVE_ATHLETE -> List.of(GymMobStrategy.GYM_AFTER_INTENSITY);
            default -> List.of(GymMobStrategy.GYM_ON_EARLIEST_NONRUN);
        };
    }

    /**
     * Picks different run-day subsets from availability (creates rest/gym/mob slots).
     */
    private List<List<Integer>> runDaySubsets(List<Integer> availableIdx, TemplateGenCfg cfg) {
        int n = availableIdx.size();

        int desiredRunDays = n;
        if (cfg.requireRestDay()) {
            desiredRunDays = Math.max(1, n - 1);
        }

        desiredRunDays = Math.max(1, Math.min(n, desiredRunDays));

        List<List<Integer>> variants = new ArrayList<>();

        variants.add(new ArrayList<>(availableIdx.subList(0, desiredRunDays)));

        if (n <= 3) {
            return variants;
        }

        variants.add(new ArrayList<>(availableIdx.subList(n - desiredRunDays, n)));

        if (desiredRunDays <= n - 1) {
            List<Integer> midDrop = new ArrayList<>(availableIdx);
            midDrop.remove(n / 2);
            variants.add(midDrop.subList(0, desiredRunDays));
        }

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

        return variants;
    }

    /**
     * Strategy variants for assigning gym/mobility within a template.
     */
    private enum GymMobStrategy {
        GYM_BEFORE_LONG,
        GYM_AFTER_INTENSITY,
        GYM_ON_EARLIEST_NONRUN
    }

    /**
     * Builds a “simple easy week” when run days are too few to place structure.
     */
    private List<WorkoutType> buildSimpleEasyWeekFromAvailability(
            List<Integer> availableIdx,
            List<Integer> runIdx,
            TemplateGenCfg cfg
    ) {
        WorkoutType[] week
                = new WorkoutType[]{WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY};

        for (int idx : runIdx) {
            week[idx] = WorkoutType.EASY_RUN;
        }

        boolean[] isAvailable = new boolean[7];
        boolean[] isRun = new boolean[7];

        for (int i : availableIdx) {
            isAvailable[i] = true;
        }
        for (int i : runIdx) {
            isRun[i] = true;
        }

        int addedGym = 0;

        for (int i = 0; i < 7 && addedGym < cfg.maxGym(); i++) {
            if (isAvailable[i] && !isRun[i] && week[i] == WorkoutType.REST_DAY) {
                week[i] = WorkoutType.GYM_PREHAB;
                addedGym++;
            }
        }

        if (cfg.requireRestDay()) {
            boolean hasRest = java.util.Arrays.stream(week).anyMatch(w -> w == WorkoutType.REST_DAY);
            if (!hasRest) {
                int idx = lastIndexOf(week, WorkoutType.EASY_RUN);
                if (idx >= 0) {
                    week[idx] = WorkoutType.REST_DAY;
                }
            }
        }

        return java.util.Arrays.asList(week);
    }


    /**
     * Builds a candidate template from run-day indices plus placements for long/quality/gym/mob.
     */
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
                = new WorkoutType[]{WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY, WorkoutType.REST_DAY};

        for (int absDay : runIdx) {
            week[absDay] = WorkoutType.EASY_RUN;
        }

        Integer longAbsDay = null;
        if (longPosInRunIdx != null && runN > 0) {
            int lp = Math.max(0, Math.min(runN - 1, longPosInRunIdx));
            longAbsDay = runIdx.get(lp);
            week[longAbsDay] = WorkoutType.LONG_RUN;
        }

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

        if (!passesHardDaySpacing(week)) {
            return null;
        }

        applyGymMobility(week, availableIdx, runIdx, longAbsDay, strat);

        capGymMobility(week, cfg);

        if (cfg.requireRestDay() && java.util.Arrays.stream(week).noneMatch(w -> w == WorkoutType.REST_DAY)) {
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

    /**
     * Ensures hard workouts do not appear on consecutive days.
     */
    private boolean passesHardDaySpacing(WorkoutType[] week) {
        for (int i = 1; i < 7; i++) {
            if (isHard(week[i - 1]) && isHard(week[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Defines which planned types count as “hard”.
     */
    private boolean isHard(WorkoutType workoutType) {
        return workoutType == WorkoutType.INTERVAL_RUN
                || workoutType == WorkoutType.TEMPO_RUN
                || workoutType == WorkoutType.LONG_RUN;
    }

    /**
     * Applies gym strategies only on available non-run rest slots.
     */
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
            default -> {
                if (!eligibleNonRun.isEmpty()) {
                    week[eligibleNonRun.get(0)] = WorkoutType.GYM_PREHAB;
                }
            }
        }
    }

    /**
     * Caps gym occurrences by converting extras back to rest.
     */
    private void capGymMobility(WorkoutType[] week, TemplateGenCfg cfg) {
        int gym = 0;

        for (int i = 0; i < 7; i++) {
            if (week[i] == WorkoutType.GYM_PREHAB) {
                gym++;
            }
        }

        for (int i = 0; i < 7 && gym > cfg.maxGym(); i++) {
            if (week[i] == WorkoutType.GYM_PREHAB) {
                week[i] = WorkoutType.REST_DAY;
                gym--;
            }
        }
    }

    /**
     * Finds the first index of any of the candidate workout types, or -1.
     */
    private int firstIndexOf(WorkoutType[] week, WorkoutType... candidates) {
        for (int i = 0; i < 7; i++) {
            for (WorkoutType candidate : candidates) {
                if (week[i] == candidate) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Finds the last index of a candidate workout type, or -1.
     */
    private int lastIndexOf(WorkoutType[] week, WorkoutType candidate) {
        for (int i = 6; i >= 0; i--) {
            if (week[i] == candidate) {
                return i;
            }
        }
        return -1;
    }

    // =====================================================================
    // 4) SCORING + CHOICE
    // =====================================================================

    /**
     * Chooses the best template via simulation + priors + shape constraints.
     */
    private PlanChoice chooseBestPlan(
            ApplicationUser user,
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
            Optional<FitUserModelResponse> fitUserModelOpt
    ) {
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

        ExperienceConfig cfg = expCfg(user);

        for (int templateIndex = 0; templateIndex < templates.size(); templateIndex++) {
            List<WorkoutType> template = templates.get(templateIndex);

            EffectiveWeek effectiveWeek = computeEffectiveWeek(template, injuryIndex, readiness, weatherPerDay, cfg);
            List<WorkoutType> effectiveTemplate = effectiveWeek.effective();

            long hardCount = effectiveTemplate.stream().filter(this::isHard).count();
            long longCount = effectiveTemplate.stream().filter(w -> w == WorkoutType.LONG_RUN).count();
            long qualityCount = effectiveTemplate.stream().filter(w -> w == WorkoutType.TEMPO_RUN || w == WorkoutType.INTERVAL_RUN).count();

            log.info("cand {} effective hard={} long={} qual={} template={}", templateIndex, hardCount, longCount, qualityCount, effectiveTemplate);

            long templateSeed = seed ^ (templateIndex * 1315423911L);

            SimResult sim = simulateTemplateJava(
                    user,
                    startDate,
                    template,
                    effectiveWeek,
                    initialState,
                    recentLoads,
                    injuryIndex,
                    readiness,
                    sims,
                    templateSeed,
                    profile,
                    constraints
            );

            double simScore = sim.riskAdjustedScore();
            double tp = templatePrior(profile, effectiveTemplate);
            double mk = missingKeySessionsPenalty(effectiveTemplate, profile, injuryIndex, readiness, cfg);
            double ep = experienceTemplatePrior(cfg, effectiveTemplate);
            double sh = weekShapeScore(effectiveTemplate, profile);

            double scored = wsim * simScore
                    + wtemplateprior * tp
                    + wmissing * mk
                    + wexp * ep
                    + wshape * sh;

            if (debug) {
                logBreakdown(templateIndex, effectiveTemplate, new ScoreBreakdown(simScore, tp, mk, ep, sh, scored));
            }

            if (debug && templateScores != null) {
                templateScores.add(new TemplateScoreDto(templateIndex, sim.avgUtility(), new ArrayList<>(template), new ArrayList<>(effectiveTemplate)));
            }

            if (scored > bestScore) {
                bestScore = scored;
                bestTemplateIndex = templateIndex;
                bestTemplate = template;
                bestTsbDists = sim.tsbDists();
            }
        }

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

    /**
     * Scores a template for “week shape” like rest distribution and minimum training density.
     */
    private double weekShapeScore(List<WorkoutType> template, PlannerProfile profile) {
        if (template == null || template.size() != 7) {
            return 0.0;
        }

        double score = 0.0;

        int hardCount = (int) template.stream().filter(this::isHard).count();
        int runCount = (int) template.stream().filter(this::isRun).count();
        int restCount = (int) template.stream().filter(w -> w == WorkoutType.REST_DAY).count();

        if (restCount >= 1 && hardCount >= 1) {
            score += 5.0;
        }
        if (restCount >= 2) {
            score -= 6.0 * (restCount - 1);
        }
        if (runCount <= 3) {
            score -= 20.0;
        }

        return score;
    }

    /**
     * Returns true if this workout type is any kind of run (easy/tempo/interval/long).
     */
    private boolean isRun(WorkoutType workoutType) {
        return workoutType == WorkoutType.EASY_RUN
                || workoutType == WorkoutType.TEMPO_RUN
                || workoutType == WorkoutType.INTERVAL_RUN
                || workoutType == WorkoutType.LONG_RUN;
    }

    private long injuriesSignatureFromEntities(List<Injuries> injuries) {
        if (injuries == null || injuries.isEmpty()) {
            return 0L;
        }

        long signature = 1469598103934665603L;

        for (Injuries injury : injuries) {
            if (injury == null) {
                continue;
            }

            Long injuryId = injury.getId();
            BodyPart affectedArea = injury.getAffectedArea();
            LocalDate lastHealthyDate = injury.getLastHealthyDate();
            LocalDate lastInjuryDate = injury.getLastInjuryDate();
            Double injuryIndex = injury.getInjuryIndex();

            final long p1 = (injuryId == null) ? 0L : injuryId.longValue();
            final long p2 = (affectedArea == null) ? 0L : (long) affectedArea.ordinal();
            final long p3 = (lastHealthyDate == null) ? 0L : lastHealthyDate.toEpochDay();
            final long p4 = (lastInjuryDate == null) ? 0L : lastInjuryDate.toEpochDay();
            final long p5 = (injuryIndex == null) ? 0L : Double.doubleToLongBits(injuryIndex.doubleValue());

            signature ^= p1;
            signature *= 1099511628211L;

            signature ^= p2;
            signature *= 1099511628211L;

            signature ^= p3;
            signature *= 1099511628211L;

            signature ^= p4;
            signature *= 1099511628211L;

            signature ^= p5;
            signature *= 1099511628211L;
        }

        return signature;
    }


    private long injuriesSignatureFromView(List<ViewInjuryDto> injuries) {
        if (injuries == null || injuries.isEmpty()) {
            return 0L;
        }

        long signature = 1469598103934665603L;

        for (ViewInjuryDto injury : injuries) {
            if (injury == null) {
                continue;
            }

            Long injuryId = injury.getInjuryId();
            BodyPart affectedArea = injury.getAffectedArea();
            LocalDate lastHealthyDate = injury.getLastHealthyDate();
            LocalDate lastInjuryDate = injury.getLastInjuryDate();
            double injuryIndex = injury.getInjuryIndex();

            final long p1 = (injuryId == null) ? 0L : injuryId.longValue();
            final long p2 = (affectedArea == null) ? 0L : (long) affectedArea.ordinal();
            final long p3 = (lastHealthyDate == null) ? 0L : lastHealthyDate.toEpochDay();
            final long p4 = (lastInjuryDate == null) ? 0L : lastInjuryDate.toEpochDay();
            final long p5 = Double.doubleToLongBits(injuryIndex);

            signature ^= p1;
            signature *= 1099511628211L;

            signature ^= p2;
            signature *= 1099511628211L;

            signature ^= p3;
            signature *= 1099511628211L;

            signature ^= p4;
            signature *= 1099511628211L;

            signature ^= p5;
            signature *= 1099511628211L;
        }

        return signature;
    }

    private LocalDate computeCutoffDate(LocalDate today, List<Injuries> injuries) {
        LocalDate cutoffDate = today;

        if (injuries == null || injuries.isEmpty()) {
            return cutoffDate;
        }

        for (Injuries injury : injuries) {
            if (injury == null) {
                continue;
            }

            LocalDate lastHealthyDate = injury.getLastHealthyDate();
            if (lastHealthyDate != null && lastHealthyDate.isAfter(cutoffDate)) {
                cutoffDate = lastHealthyDate;
            }

            LocalDate healedDate = injury.getLastInjuryDate();
            if (healedDate != null && healedDate.isAfter(cutoffDate)) {
                cutoffDate = healedDate;
            }
        }

        if (cutoffDate.isBefore(today)) {
            cutoffDate = today;
        }

        return cutoffDate;
    }

    private Optional<LocalDate> clampCutoffToWeek(LocalDate cutoffDate, LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);

        if (cutoffDate.isBefore(weekStart)) {
            return Optional.of(weekStart);
        }

        if (cutoffDate.isAfter(weekEnd)) {
            return Optional.empty();
        }

        return Optional.of(cutoffDate);
    }


    private boolean shouldPatchForInjuryChange(TrainingPlan7dDto cachedPlan, long currentSignature) {
        if (cachedPlan == null) {
            return false;
        }

        AthleteStatusDto cachedStatus = cachedPlan.getCurrentAthleteStatus();

        if (cachedStatus == null) {
            if (currentSignature == 0L) {
                return false;
            }

            return true;
        }

        long cachedSignature = injuriesSignatureFromView(cachedStatus.getInjuries());

        if (cachedSignature != currentSignature) {
            return true;
        }

        return false;
    }


    private TrainingPlan7dDto computePlanNoCache(
            ApplicationUser user,
            String email,
            double latitude,
            double longitude,
            boolean debug,
            int sims,
            long seed,
            DevOverrides overrides,
            LocalDate startDate,
            String planId
    ) {
        OverridesResolved resolved = resolveOverrides(user, email, startDate, overrides, seed);

        List<CompactWeatherDto> weatherPerDay = precomputeWeather(startDate, latitude, longitude, 18);
        List<Integer> recentLoads = resolved.recentLoads();

        PlannerProfile profile = buildPlannerProfile(user, recentLoads);

        ForecastState initialState = new ForecastState(resolved.ctl(), resolved.atl());
        boolean coldStart = isColdStart(initialState.ctl(), initialState.atl(), recentLoads);

        List<List<WorkoutType>> templates = generateTemplates(user, startDate, coldStart);

        PlanChoice choice = chooseBestPlan(
                user,
                startDate,
                templates,
                initialState,
                recentLoads,
                resolved.injuryIndex(),
                resolved.readiness(),
                weatherPerDay,
                resolved.constraints(),
                sims,
                seed,
                debug,
                profile,
                Optional.empty()
        );

        List<PlannedDayDto> days = materializePlanWithTsbDists(
                user,
                startDate,
                choice.bestTemplate(),
                initialState,
                recentLoads,
                choice.bestTsbDists(),
                resolved.injuryIndex(),
                resolved.injuriesMap(),
                resolved.readiness(),
                weatherPerDay,
                resolved.constraints(),
                planId
        );

        List<Injuries> injuries = safeList(() -> injuryAwareTrainingService.findInjuriesByEmail(email));

        TrainingPlan7dDto dto = new TrainingPlan7dDto(days);

        dto.setPlanId(planId);
        dto.setDebug(choice.debug());
        dto.setGeneratedAt(java.time.Instant.now(clock));
        dto.setCurrentAthleteStatus(buildAthleteStatusDto(resolved, injuries));

        return dto;
    }

    private TrainingPlan7dDto mergeByDateKeepingPrefix(
            TrainingPlan7dDto cachedWeek,
            TrainingPlan7dDto recomputedFromCutoff,
            LocalDate cutoffDate
    ) {
        if (cachedWeek == null || cachedWeek.getDays() == null) {
            return recomputedFromCutoff;
        }

        if (recomputedFromCutoff == null || recomputedFromCutoff.getDays() == null) {
            return cachedWeek;
        }

        java.util.Map<LocalDate, PlannedDayDto> patchByDate = new java.util.HashMap<>();

        for (PlannedDayDto patchDay : recomputedFromCutoff.getDays()) {
            if (patchDay == null || patchDay.getDate() == null) {
                continue;
            }

            patchByDate.put(patchDay.getDate(), patchDay);
        }

        List<PlannedDayDto> mergedDays = new ArrayList<>(cachedWeek.getDays().size());

        for (PlannedDayDto cachedDay : cachedWeek.getDays()) {
            if (cachedDay == null || cachedDay.getDate() == null) {
                mergedDays.add(cachedDay);
                continue;
            }

            LocalDate date = cachedDay.getDate();

            if (date.isBefore(cutoffDate)) {
                mergedDays.add(cachedDay);
                continue;
            }

            PlannedDayDto replacement = patchByDate.get(date);
            if (replacement != null) {
                mergedDays.add(replacement);
            } else {
                mergedDays.add(cachedDay);
            }
        }

        TrainingPlan7dDto merged = new TrainingPlan7dDto(mergedDays);

        merged.setPlanId(cachedWeek.getPlanId());
        merged.setDebug(cachedWeek.getDebug());
        merged.setGeneratedAt(java.time.Instant.now(clock));

        merged.setCurrentAthleteStatus(recomputedFromCutoff.getCurrentAthleteStatus());

        return merged;
    }


    private AthleteStatusDto buildAthleteStatusDto(OverridesResolved resolved, List<Injuries> injuries) {
        Double tsb = null;

        return new AthleteStatusDto(
                tsb,
                resolved.readiness(),
                resolved.injuryIndex(),
                toViewInjuries(injuries)
        );
    }

    private List<ViewInjuryDto> toViewInjuries(List<Injuries> injuries) {
        if (injuries == null || injuries.isEmpty()) {
            return List.of();
        }

        List<ViewInjuryDto> out = new ArrayList<>(injuries.size());

        for (Injuries injury : injuries) {
            if (injury == null) {
                continue;
            }

            ViewInjuryDto dto = new ViewInjuryDto();

            dto.setInjuryId(injury.getId());
            dto.setInjuryIndex(injury.getInjuryIndex());
            dto.setAffectedArea(injury.getAffectedArea());
            dto.setLastHealthyDate(injury.getLastHealthyDate());
            dto.setLastInjuryDate(injury.getLastInjuryDate());

            out.add(dto);
        }

        return out;
    }


    // =====================================================================
    // 5) SIMULATION
    // =====================================================================

    /**
     * Runs Monte Carlo simulation for a template and returns avg utility + risk-adjusted score + TSB distributions.
     */
    private SimResult simulateTemplateJava(
            ApplicationUser user,
            LocalDate startDate,
            List<WorkoutType> plannedTemplate,
            EffectiveWeek effectiveWeekOrNull,
            ForecastState initialState,
            List<Integer> recentLoads,
            double injuryIndex,
            int readiness,
            int sims,
            long seed,
            PlannerProfile profile,
            LoadConstraints constraints
    ) {
        Random rng = new Random(seed);

        ExperienceConfig cfg = expCfg(user);

        EffectiveWeek effectiveWeek = (effectiveWeekOrNull != null)
                ? effectiveWeekOrNull
                : computeEffectiveWeek(plannedTemplate, injuryIndex, readiness, List.of(), cfg);

        List<List<Double>> tsbSamplesPerDay = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            tsbSamplesPerDay.add(new ArrayList<>(sims));
        }

        double totalUtility = 0.0;
        List<Double> utilSamples = new ArrayList<>(sims);

        for (int s = 0; s < sims; s++) {
            ForecastState state = initialState;
            double utility = 0.0;
            double weeklyLoad = 0.0;

            for (int i = 0; i < 7; i++) {
                LocalDate day = startDate.plusDays(i);
                WorkoutType effective = effectiveWeek.effective().get(i);
                Double weatherScore = effectiveWeek.weatherScores().get(i);

                LoadDistributionDto loadDist = loadForecaster.forecastLoad(user, day, effective, state, recentLoads, constraints);

                double stdAdj = loadDist.getStd() * uncertaintyMultiplier(profile, injuryIndex, readiness, weatherScore, effective);
                double loadSample = sampleNonNegativeNormal(rng, loadDist.getMean(), stdAdj);

                weeklyLoad += loadSample;

                state = state.next(loadSample);

                tsbSamplesPerDay.get(i).add(state.tsb());

                utility += trainingReward(effective, loadSample);
                utility -= fatiguePenalty(state.tsb(), effective);
                utility -= injuryPenalty(injuryIndex, effective);
                utility -= readinessPenalty(readiness, effective);
                utility -= weatherPenalty(weatherScore, effective);
            }

            double targetWeek = profile.targetWeeklyLoad() * weekTargetMultiplier(injuryIndex, readiness);
            double deficit = Math.max(0.0, targetWeek - weeklyLoad);
            double k = 0.0035 / Math.max(0.4, profile.riskAversion());
            utility -= k * deficit * deficit;

            totalUtility += utility;
            utilSamples.add(utility);
        }

        double avgUtility = totalUtility / sims;
        JuliaDist utilDist = toDist(utilSamples);

        double lambda = 0.35 + 0.9 * profile.riskAversion();
        double riskAdjusted = avgUtility - lambda * utilDist.getStd();

        List<LoadDistributionDto> tsbDists = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            tsbDists.add(toDistribution(tsbSamplesPerDay.get(i)));
        }

        return new SimResult(avgUtility, utilDist, riskAdjusted, tsbDists);
    }

    /**
     * Converts workout type + load into a positive training “reward”.
     */
    private double trainingReward(WorkoutType workoutType, double load) {
        return switch (workoutType) {
            case REST_DAY -> 0.0;
            case GYM_PREHAB -> 3.0;
            case EASY_RUN -> 6.0 + 0.015 * load;
            case LONG_RUN -> 10.0 + 0.020 * load;
            case TEMPO_RUN -> 12.0 + 0.018 * load;
            case INTERVAL_RUN -> 13.0 + 0.017 * load;
            default -> 7.0 + 0.015 * load;
        };
    }

    /**
     * Penalizes fatigue (TSB) especially on hard sessions.
     */
    private double fatiguePenalty(double tsb, WorkoutType workoutType) {
        double penalty = 0.0;

        if (tsb < -25) {
            penalty += 25;
        } else if (tsb < -15) {
            penalty += 12;
        } else if (tsb < -10) {
            penalty += 6;
        }

        if ((workoutType == WorkoutType.INTERVAL_RUN || workoutType == WorkoutType.TEMPO_RUN || workoutType == WorkoutType.LONG_RUN) && tsb < -10) {
            penalty += 6;
        }

        return penalty;
    }

    /**
     * Samples a non-negative normal random variable via Box-Muller.
     */
    private double sampleNonNegativeNormal(Random rng, double mean, double std) {
        if (std <= 0) {
            return Math.max(0, mean);
        }

        double u1 = Math.max(1e-12, rng.nextDouble());
        double u2 = rng.nextDouble();
        double z = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);

        return Math.max(0, mean + std * z);
    }

    // =====================================================================
    // 6) EFFECTIVE WEEK (EXPERIENCE + INJURY + READINESS + WEATHER)
    // =====================================================================

    /**
     * Effective week computed from planned template + daily weatherScore (type conversions happen here).
     */
    private record EffectiveWeek(List<WorkoutType> effective, List<Double> weatherScores) {
    }

    /**
     * Computes per-day effective workout types and collects per-day weatherScore.
     */
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
            CompactWeatherDto weather = (weatherPerDay == null || weatherPerDay.size() <= i) ? null : weatherPerDay.get(i);
            Double score = (weather == null) ? null : weather.getWeatherScore();

            ws.add(score);
            eff.add(effectiveWorkoutType(planned.get(i), injuryIndex, readiness, score, cfg));
        }

        return new EffectiveWeek(eff, ws);
    }

    /**
     * Produces the final type after gating by experience, injury, readiness, and weather.
     */
    private WorkoutType effectiveWorkoutType(
            WorkoutType planned,
            double injuryIndex,
            int readiness,
            Double weatherScore,
            ExperienceConfig cfg
    ) {
        WorkoutType workoutType = gateByExperience(planned, cfg);

        workoutType = mapWorkoutForInjury(workoutType, injuryIndex);
        workoutType = mapWorkoutForReadiness(workoutType, readiness);
        workoutType = mapWorkoutForWeather(workoutType, weatherScore);

        return workoutType;
    }

    /**
     * Downgrades planned workouts based on injury index.
     */
    private WorkoutType mapWorkoutForInjury(WorkoutType workoutType, double injuryIndex) {
        if (injuryIndex >= 0.7) {
            return switch (workoutType) {
                case INTERVAL_RUN, TEMPO_RUN -> WorkoutType.GYM_PREHAB;
                case LONG_RUN -> WorkoutType.EASY_RUN;
                default -> workoutType;
            };
        }

        return switch (workoutType) {
            case INTERVAL_RUN -> WorkoutType.TEMPO_RUN;
            default -> workoutType;
        };
    }

    /**
     * Returns a penalty for injury risk given the effective workout type.
     */
    private double injuryPenalty(double injuryIndex, WorkoutType workoutType) {
        if (injuryIndex < 0.4) {
            return 0.0;
        }

        double penalty = 0.0;
        boolean hardRun = (workoutType == WorkoutType.INTERVAL_RUN || workoutType == WorkoutType.TEMPO_RUN || workoutType == WorkoutType.LONG_RUN);

        if (injuryIndex >= 0.7) {
            if (hardRun) {
                penalty += 30;
            }
            if (workoutType == WorkoutType.EASY_RUN) {
                penalty += 8;
            }
        } else {
            if (workoutType == WorkoutType.INTERVAL_RUN) {
                penalty += 18;
            }
            if (workoutType == WorkoutType.TEMPO_RUN) {
                penalty += 8;
            }
        }

        return penalty;
    }

    /**
     * Maps planned workouts given readiness (very low -> convert hard to mobility).
     */
    private WorkoutType mapWorkoutForReadiness(WorkoutType workoutType, int readiness) {
        if (readiness < 40) {
            return mapForVeryLowReadiness(workoutType);
        }
        return mapForModerateReadiness(workoutType);
    }

    /**
     * Performs strict readiness gating when readiness is very low.
     */
    private WorkoutType mapForVeryLowReadiness(WorkoutType workoutType) {
        return switch (workoutType) {
            case INTERVAL_RUN, TEMPO_RUN, LONG_RUN -> WorkoutType.GYM_PREHAB;
            default -> workoutType;
        };
    }

    /**
     * Performs moderate readiness gating when readiness is somewhat low.
     */
    private WorkoutType mapForModerateReadiness(WorkoutType workoutType) {
        return switch (workoutType) {
            case INTERVAL_RUN -> WorkoutType.TEMPO_RUN;
            case TEMPO_RUN -> WorkoutType.TEMPO_RUN;
            case LONG_RUN -> WorkoutType.LONG_RUN;
            default -> workoutType;
        };
    }

    /**
     * Returns a penalty term based on readiness and workout type.
     */
    private double readinessPenalty(int readiness, WorkoutType workoutType) {
        if (readiness >= 70) {
            return 0.0;
        }

        boolean hardRun = (workoutType == WorkoutType.INTERVAL_RUN || workoutType == WorkoutType.TEMPO_RUN || workoutType == WorkoutType.LONG_RUN);

        if (readiness < 40) {
            if (hardRun) {
                return 25.0;
            }
            if (workoutType == WorkoutType.EASY_RUN) {
                return 8.0;
            }
            return 0.0;
        }

        if (hardRun) {
            double x = (70 - readiness) / 30.0;
            return 2.0 + 8.0 * clamp01(x);
        }

        if (workoutType == WorkoutType.EASY_RUN && readiness < 55) {
            return 3.0;
        }

        return 0.0;
    }

    /**
     * Maps outdoor runs to indoor gym when weather is too bad.
     */
    private WorkoutType mapWorkoutForWeather(WorkoutType workoutType, Double weatherScore) {
        if (weatherScore == null) {
            return WorkoutType.GYM_PREHAB;
        }

        boolean run = (workoutType == WorkoutType.EASY_RUN
                || workoutType == WorkoutType.TEMPO_RUN
                || workoutType == WorkoutType.INTERVAL_RUN
                || workoutType == WorkoutType.LONG_RUN);

        if (!run) {
            return workoutType;
        }

        if (weatherScore < 0.3) {
            return WorkoutType.GYM_PREHAB;
        }

        return workoutType;
    }

    /**
     * Returns a penalty for bad weather on outdoor runs only.
     */
    private double weatherPenalty(Double weatherScore, WorkoutType workoutType) {
        if (weatherScore == null) {
            return 0.0;
        }

        boolean outdoorRun = (workoutType == WorkoutType.EASY_RUN
                || workoutType == WorkoutType.TEMPO_RUN
                || workoutType == WorkoutType.INTERVAL_RUN
                || workoutType == WorkoutType.LONG_RUN);

        if (!outdoorRun) {
            return 0.0;
        }

        if (weatherScore >= 0.7) {
            return 0.0;
        }
        if (weatherScore >= 0.5) {
            return 2.0;
        }
        if (weatherScore >= 0.3) {
            return 8.0;
        }
        return 18.0;
    }

    // =====================================================================
    // 7) DAY DEBUG + MATERIALIZATION
    // =====================================================================

    /**
     * Builds the per-day debug information for the chosen plan.
     */
    private List<DayDebugDto> buildDayDebug(
            ApplicationUser user,
            LocalDate startDate,
            List<WorkoutType> bestTemplate,
            ForecastState initialState,
            List<Integer> recentLoads,
            List<LoadDistributionDto> bestTsbDists,
            double injuryIndex,
            int readiness,
            List<CompactWeatherDto> weatherPerDay,
            LoadConstraints constraints
    ) {
        List<DayDebugDto> out = new ArrayList<>(7);
        ForecastState state = initialState;

        for (int i = 0; i < 7; i++) {
            LocalDate day = startDate.plusDays(i);
            CompactWeatherDto weather = weatherPerDay.get(i);

            WorkoutType planned = bestTemplate.get(i);

            Double wsObj = (weather == null) ? null : weather.getWeatherScore();
            double ws = (wsObj == null) ? 0.6 : wsObj;

            ExperienceConfig cfg = expCfg(user);
            WorkoutType effective = effectiveWorkoutType(planned, injuryIndex, readiness, wsObj, cfg);

            LoadDistributionDto load = loadForecaster.forecastLoad(user, day, effective, state, recentLoads, constraints);

            state = state.next(load.getMean());

            double fatP = fatiguePenalty(state.tsb(), effective);
            double injP = injuryPenalty(injuryIndex, effective);
            double readyP = readinessPenalty(readiness, effective);
            double weathP = weatherPenalty(wsObj, effective);

            out.add(new DayDebugDto(
                    day,
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

    /**
     * Converts the chosen template into final day DTOs (including gym workouts and routes).
     */
    private List<PlannedDayDto> materializePlanWithTsbDists(
            ApplicationUser user,
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
            String planId
    ) {
        List<PlannedDayDto> out = new ArrayList<>(7);
        ForecastState state = initialState;

        for (int i = 0; i < 7; i++) {
            LocalDate day = startDate.plusDays(i);

            CompactWeatherDto weatherDto = weatherPerDay.get(i);
            Double ws = (weatherDto == null) ? null : weatherDto.getWeatherScore();

            WorkoutType planned = template.get(i);

            ExperienceConfig cfg = expCfg(user);
            WorkoutType effective = effectiveWorkoutType(planned, injuryIndex, readiness, ws, cfg);

            LoadDistributionDto load = loadForecaster.forecastLoad(user, day, effective, state, recentLoads, constraints);

            state = state.next(load.getMean());

            GymWorkoutDto gym = null;
            if (effective == WorkoutType.GYM_PREHAB) {
                gym = gymWorkoutSelectorService.getGymWorkout(user, day, injuriesMap, readiness);
            }

            RouteDto routeDto = null;
            if (isRun(effective)) {
                routeDto = computeRouteDtoForDay(user, day, effective, load, readiness, planId);
            }

            out.add(new PlannedDayDto(
                    day,
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

    /**
     * Converts load variance into a simple confidence label.
     */
    private String confidenceFromStd(LoadDistributionDto dist) {
        double frac = dist.getStd() / Math.max(1.0, dist.getMean());
        if (frac < 0.18) {
            return "high";
        }
        if (frac < 0.32) {
            return "medium";
        }
        return "low";
    }

    /**
     * Builds a human-readable explanation for the day.
     */
    private List<String> explanation(WorkoutType workoutType, LoadDistributionDto load, double tsb, double injuryIndex, int readiness) {
        List<String> explanation = new ArrayList<>();

        explanation.add("Planned: " + workoutType);
        explanation.add("Expected load ≈ " + Math.round(load.getMean()) + " (P10 " + Math.round(load.getP10()) + " – P90 " + Math.round(load.getP90()) + ")");
        explanation.add("Readiness today: " + readiness + "/100");

        if (injuryIndex >= 0.7) {
            explanation.add("Injury-aware: intensity reduced.");
        }

        if (tsb < -20) {
            explanation.add("Fatigue risk: predicted TSB very low.");
        } else if (tsb < -10) {
            explanation.add("Fatigue: predicted TSB somewhat low.");
        } else {
            explanation.add("Recovery: predicted TSB acceptable.");
        }

        return explanation;
    }

    // =====================================================================
    // 8) ROUTE GENERATION
    // =====================================================================

    /**
     * Creates a RouteDto for run days by jittering base distance deterministically per day.
     */
    private RouteDto computeRouteDtoForDay(
            ApplicationUser user,
            LocalDate date,
            WorkoutType workoutType,
            LoadDistributionDto load,
            int readiness,
            String planId
    ) {
        boolean isRun = switch (workoutType) {
            case EASY_RUN, TEMPO_RUN, INTERVAL_RUN, LONG_RUN -> true;
            default -> false;
        };

        if (!isRun) {
            return null;
        }

        RouteDto base = routeGenerationService.generateRouteDetails(user, workoutType, readiness);
        if (base == null || base.getDistance() == null) {
            return null;
        }

        double baseMeters = base.getDistance();

        double jitterPct = switch (confidenceFromStd(load)) {
            case "high" -> 0.05;
            case "medium" -> 0.08;
            default -> 0.12;
        };

        if (workoutType == WorkoutType.INTERVAL_RUN || workoutType == WorkoutType.TEMPO_RUN) {
            jitterPct = Math.min(jitterPct, 0.06);
        }
        if (workoutType == WorkoutType.LONG_RUN) {
            jitterPct = Math.max(jitterPct, 0.07);
        }

        long seed = stableSeed(planId, date, workoutType);
        Random rng = new Random(seed);

        double factor = 1.0 + (rng.nextDouble() * 2.0 - 1.0) * jitterPct;
        double meters = baseMeters * factor;

        meters = clampRunDistanceMeters(workoutType, meters);

        int orsSeed = orsSeedFromLong(seed);
        if (orsSeed == Integer.MIN_VALUE) {
            orsSeed = 0;
        }
        orsSeed = Math.abs(orsSeed);

        return new RouteDto(meters, base.getPace(), base.getElevation(), orsSeed);
    }

    /**
     * Creates a deterministic seed for a specific (planId, date, workoutType).
     */
    private long stableSeed(String planId, LocalDate date, WorkoutType workoutType) {
        String key = planId + ":" + date + ":" + workoutType.name();
        return key.hashCode() * 2654435761L;
    }

    /**
     * Mixes a long into a 32-bit int seed for ORS.
     */
    private int orsSeedFromLong(long seed) {
        long z = seed;

        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);

        return (int) z;
    }

    /**
     * Clamps run distance to sensible bounds per workout type.
     */
    private double clampRunDistanceMeters(WorkoutType workoutType, double meters) {
        double min;
        double max;

        switch (workoutType) {
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

    // =====================================================================
    // 9) WEATHER
    // =====================================================================

    /**
     * Precomputes compact daily weather summaries for the next 7 days.
     */
    private List<CompactWeatherDto> precomputeWeather(LocalDate startDate, double latitude, double longitude, int localHour) {
        List<CompactWeatherDto> weather = new ArrayList<>(7);

        for (int i = 0; i < 7; i++) {
            LocalDate day = startDate.plusDays(i);
            weather.add(buildCompactWeatherDtoForDay(day, latitude, longitude, localHour));
        }

        return weather;
    }

    /**
     * Builds a single-day CompactWeatherDto, falling back to safe defaults on errors.
     */
    private CompactWeatherDto buildCompactWeatherDtoForDay(LocalDate day, double latitude, double longitude, int localHour) {
        try {
            String timeUtc = utcTimeStringFor(day, localHour);

            WeatherResponse weatherResponse = weatherService.getWeatherAtTime(latitude, longitude, timeUtc);
            double weatherScore = weatherService.calculateWeatherScore(weatherResponse);

            return new CompactWeatherDto(
                    weatherScore,
                    weatherResponse.getTemperature2m(),
                    weatherResponse.getWindSpeed10m(),
                    weatherResponse.getPrecipitation(),
                    weatherResponse.getRelativeHumidity(),
                    weatherService.estimatePerformancePenalty(weatherResponse),
                    weatherService.evaluateWeatherScore(weatherScore),
                    weatherService.buildWeatherDescription(weatherResponse)
            );
        } catch (Exception e) {
            return new CompactWeatherDto(
                    0.6,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "Weather unavailable",
                    null
            );
        }
    }

    /**
     * Converts a local date+hour in Vienna into a UTC timestamp string for the API.
     */
    private String utcTimeStringFor(LocalDate date, int localHour) {
        var local = date.atTime(localHour, 0).atZone(zone);
        var utc = local.withZoneSameInstant(ZoneId.of("UTC"));

        return utc.toLocalDateTime()
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    }

    // =====================================================================
    // 10) PROFILE + PRIORS
    // =====================================================================

    /**
     * Builds a planner profile from recent load history (consistency, risk aversion, uncertainty scaling).
     */
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

        double consistency = 1.0 - clamp01((cv - 0.15) / (0.70 - 0.15));
        double riskAversion = 0.25 + (1.10 * (1.0 - consistency));
        double uncertaintyScale = 0.85 + (0.75 * (1.0 - consistency));

        int activeTrainingDays = (user.getActiveWeekdays() == null) ? 0 : user.getActiveWeekdays().size();
        int trainDays = Math.max(3, Math.min(6, activeTrainingDays == 0 ? 4 : activeTrainingDays));

        double histWeek = mean * 7.0;

        double dayBase = 35.0;
        double floorWeek = dayBase * trainDays;

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

    /**
     * Template prior based on “reasonable week structure” for this athlete profile.
     */
    private double templatePrior(PlannerProfile profile, List<WorkoutType> plannedTemplate) {
        if (plannedTemplate == null || plannedTemplate.size() != 7) {
            return 0.0;
        }

        int hard = 0;
        int longRuns = 0;
        int runDays = 0;
        int gym = 0;
        int rest = 0;

        for (WorkoutType workoutType : plannedTemplate) {
            if (workoutType == null) {
                continue;
            }

            if (isRun(workoutType)) {
                runDays++;
            }

            switch (workoutType) {
                case INTERVAL_RUN, TEMPO_RUN -> hard++;
                case LONG_RUN -> {
                    hard++;
                    longRuns++;
                }
                case GYM_PREHAB -> gym++;
                case REST_DAY -> rest++;
                default -> rest++;
            }
        }

        double score = 0.0;

        if (runDays >= 3) {
            score += (longRuns == 1 ? 8.0 : (longRuns == 0 ? -10.0 : -12.0));
        }

        double preferredHard = (profile.consistency() > 0.6) ? 2 : 1;
        score -= 6.0 * Math.abs(hard - preferredHard);

        int minRest = (profile.consistency() > 0.7) ? 1 : 2;
        if (rest < minRest) {
            score -= (minRest - rest) * 10.0;
        }

        if (gym > 2) {
            score -= (gym - 2) * 6.0;
        }

        score += bufferBonus(plannedTemplate);

        return score;
    }

    /**
     * Adds a small bonus for easy/rest buffer around hard/long days.
     */
    private double bufferBonus(List<WorkoutType> template) {
        double score = 0.0;

        for (int i = 0; i < 7; i++) {
            WorkoutType workoutType = template.get(i);

            if (workoutType == WorkoutType.LONG_RUN) {
                if (i > 0 && (template.get(i - 1) == WorkoutType.EASY_RUN || template.get(i - 1) == WorkoutType.REST_DAY)) {
                    score += 4.0;
                }
            }

            if (workoutType == WorkoutType.INTERVAL_RUN || workoutType == WorkoutType.TEMPO_RUN) {
                if (i > 0 && (template.get(i - 1) == WorkoutType.EASY_RUN || template.get(i - 1) == WorkoutType.REST_DAY)) {
                    score += 2.5;
                }
                if (i < 6 && (template.get(i + 1) == WorkoutType.EASY_RUN
                        || template.get(i + 1) == WorkoutType.REST_DAY)) {
                    score += 2.5;
                }
            }
        }

        return score;
    }

    /**
     * Multiplies forecast uncertainty based on profile + context for that day.
     */
    private double uncertaintyMultiplier(
            PlannerProfile profile,
            double injuryIndex,
            int readiness,
            Double weatherScore,
            WorkoutType workoutType
    ) {
        double multiplier = profile.uncertaintyScale();

        if (workoutType == WorkoutType.INTERVAL_RUN) {
            multiplier *= 1.25;
        }
        if (workoutType == WorkoutType.TEMPO_RUN) {
            multiplier *= 1.15;
        }
        if (workoutType == WorkoutType.LONG_RUN) {
            multiplier *= 1.18;
        }

        if (injuryIndex >= 0.7) {
            multiplier *= 1.25;
        } else if (injuryIndex >= 0.4) {
            multiplier *= 1.10;
        }

        if (readiness < 40) {
            multiplier *= 1.25;
        } else if (readiness < 55) {
            multiplier *= 1.10;
        }

        if (weatherScore != null) {
            if (weatherScore < 0.3) {
                multiplier *= 1.25;
            } else if (weatherScore < 0.5) {
                multiplier *= 1.10;
            }
        }

        return Math.max(0.7, Math.min(2.2, multiplier));
    }

    /**
     * Scales the weekly load target down when injury/readiness are low.
     */
    private double weekTargetMultiplier(double injuryIndex, int readiness) {
        double multiplier = 1.0;

        if (injuryIndex >= 0.7) {
            multiplier *= 0.55;
        } else if (injuryIndex >= 0.4) {
            multiplier *= 0.75;
        }

        if (readiness < 40) {
            multiplier *= 0.70;
        } else if (readiness < 55) {
            multiplier *= 0.85;
        } else if (readiness > 75) {
            multiplier *= 1.05;
        }

        return Math.max(0.45, Math.min(1.10, multiplier));
    }

    /**
     * Penalizes missing “key sessions” (long run / quality) with gating for injury/readiness.
     */
    private double missingKeySessionsPenalty(
            List<WorkoutType> template,
            PlannerProfile profile,
            double injury,
            int readiness,
            ExperienceConfig exp
    ) {
        int longCnt = (int) template.stream().filter(w -> w == WorkoutType.LONG_RUN).count();
        int qualCnt = (int) template.stream().filter(w -> w == WorkoutType.TEMPO_RUN || w == WorkoutType.INTERVAL_RUN).count();
        int runCnt = (int) template.stream().filter(this::isRun).count();
        final int restCnt = (int) template.stream().filter(w -> w == WorkoutType.REST_DAY).count();

        double gate = (injury > 0.7 || readiness < 35)
                ? 0.4
                : (injury > 0.5 || readiness < 50)
                ? 0.7
                : 1.0;

        double score = 0.0;

        boolean expectLong = exp.allowLong() && runCnt >= exp.minRunDaysForLong();
        if (expectLong && longCnt == 0) {
            score -= 18.0 * gate;
        }

        boolean expectQuality = exp.maxQualitySessions() >= 1 && (exp.allowTempo() || exp.allowIntervals());
        if (expectQuality && qualCnt == 0) {
            score -= 16.0 * gate;
        }

        if (expectQuality && qualCnt == 0 && runCnt <= 3) {
            score -= 6.0 * gate;
        }

        int softMaxRest = (exp.maxQualitySessions() == 0) ? 2 : 1;
        if (restCnt > softMaxRest) {
            score -= 6.0 * (restCnt - softMaxRest) * gate;
        }

        return score;
    }

    /**
     * Returns a weight for stimulus estimation (not currently used heavily but useful for future scoring).
     */
    private double stimulusWeight(WorkoutType workoutType) {
        return switch (workoutType) {
            case REST_DAY -> 0.0;
            case GYM_PREHAB -> 0.35;
            case EASY_RUN -> 1.0;
            case LONG_RUN -> 1.15;
            case TEMPO_RUN -> 1.25;
            case INTERVAL_RUN -> 1.35;
            default -> 1.0;
        };
    }

    // =====================================================================
    // 11) EXPERIENCE CONFIG
    // =====================================================================

    /**
     * Experience-level configuration controlling what sessions are allowed/expected.
     */
    private record ExperienceConfig(
            int minRunDaysForLong,
            int maxQualitySessions,
            boolean allowIntervals,
            boolean allowTempo,
            boolean allowLong,
            int maxHardPerWeek,
            double easyBias
    ) {
    }

    /**
     * Returns experience configuration for the user’s level.
     */
    private ExperienceConfig expCfg(ApplicationUser user) {
        ExperienceLevel lvl = (user.getExperienceLevel() == null)
                ? ExperienceLevel.INTERMEDIATE
                : user.getExperienceLevel();

        return switch (lvl) {
            case BEGINNER -> new ExperienceConfig(4, 0, false, false, true, 1, 2.5);
            case CASUAL -> new ExperienceConfig(3, 1, false, true, true, 2, 1.6);
            case INTERMEDIATE -> new ExperienceConfig(3, 1, true, true, true, 3, 1.0);
            case ADVANCED -> new ExperienceConfig(3, 2, true, true, true, 4, 0.6);
            case COMPETITIVE_ATHLETE -> new ExperienceConfig(3, 2, true, true, true, 4, 0.3);
        };
    }

    /**
     * Gates an illegal workout type into a supported one for this experience level.
     */
    private WorkoutType gateByExperience(WorkoutType workoutType, ExperienceConfig cfg) {
        if (workoutType == WorkoutType.INTERVAL_RUN && !cfg.allowIntervals()) {
            return WorkoutType.TEMPO_RUN;
        }
        if (workoutType == WorkoutType.TEMPO_RUN && !cfg.allowTempo()) {
            return WorkoutType.EASY_RUN;
        }
        if (workoutType == WorkoutType.LONG_RUN && !cfg.allowLong()) {
            return WorkoutType.EASY_RUN;
        }
        return workoutType;
    }

    /**
     * Adds priors/penalties based purely on experience expectations.
     */
    private double experienceTemplatePrior(ExperienceConfig cfg, List<WorkoutType> template) {
        int qual = (int) template.stream().filter(w -> w == WorkoutType.TEMPO_RUN || w == WorkoutType.INTERVAL_RUN).count();
        int hard = (int) template.stream().filter(this::isHard).count();

        double score = 0.0;

        if (cfg.maxQualitySessions() == 0) {
            score -= 40.0 * qual;
        }

        if (cfg.maxQualitySessions() >= 2 && qual == 0) {
            score -= 25.0;
        }

        if (hard > cfg.maxHardPerWeek()) {
            score -= 12.0 * (hard - cfg.maxHardPerWeek());
        }

        int easy = (int) template.stream().filter(w -> w == WorkoutType.EASY_RUN).count();
        score += cfg.easyBias() * easy;

        return score;
    }

    /**
     * Template-generation configuration (availability vs run-day subset vs min rest).
     */
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
    ) {
    }

    /**
     * Returns template-generation constraints based on the experience level.
     */
    private TemplateGenCfg cfgFor(ExperienceLevel lvl) {
        ExperienceLevel level = (lvl == null) ? ExperienceLevel.INTERMEDIATE : lvl;

        return switch (level) {
            case BEGINNER -> new TemplateGenCfg(2, 0, false, false, false, 1, 2, true, 2, 0, 1);
            case CASUAL -> new TemplateGenCfg(2, 1, true, false, true, 1, 2, true, 1, 0, 1);
            case INTERMEDIATE -> new TemplateGenCfg(3, 1, true, true, true, 2, 1, false, 1, 1, 0);
            case ADVANCED -> new TemplateGenCfg(4, 2, true, true, true, 2, 1, false, 1, 1, 0);
            case COMPETITIVE_ATHLETE -> new TemplateGenCfg(4, 2, true, true, true, 1, 1, false, 0, 0, 0);
        };
    }

    // =====================================================================
    // 12) POST-PROCESSING (SHAPE SAFETY)
    // =====================================================================

    /**
     * Normalizes and filters templates according to safety/shape rules (especially for cold start).
     */
    private List<List<WorkoutType>> postProcessTemplates(List<List<WorkoutType>> templates, ExperienceLevel exp, boolean coldStart) {
        return templates.stream()
                .map(t -> normalizeWeek(t, exp, coldStart))
                .filter(t -> isWeekValid(t, exp, coldStart))
                .toList();
    }

    /**
     * Normalizes a template into a safer shape for cold start (removes intensity, enforces recovery).
     */
    private List<WorkoutType> normalizeWeek(List<WorkoutType> week, ExperienceLevel exp, boolean coldStart) {
        List<WorkoutType> normalized = new ArrayList<>(week);

        if (coldStart) {
            for (int i = 0; i < normalized.size(); i++) {
                if (normalized.get(i) == WorkoutType.TEMPO_RUN || normalized.get(i) == WorkoutType.INTERVAL_RUN) {
                    normalized.set(i, WorkoutType.EASY_RUN);
                }
            }

            if (exp == ExperienceLevel.BEGINNER || exp == ExperienceLevel.CASUAL) {
                for (int i = 0; i < normalized.size(); i++) {
                    if (normalized.get(i) == WorkoutType.LONG_RUN) {
                        normalized.set(i, WorkoutType.EASY_RUN);
                    }
                }
            }

            ensureAtLeastN(normalized, WorkoutType.REST_DAY, minRestDays(exp));

            if (exp != ExperienceLevel.BEGINNER) {
                ensureAtLeastN(normalized, WorkoutType.GYM_PREHAB, 1);
            }

            capRunStreak(normalized, 2);
        }

        return List.copyOf(normalized);
    }

    /**
     * Validates a week for cold start safety rules.
     */
    private boolean isWeekValid(List<WorkoutType> week, ExperienceLevel exp, boolean coldStart) {
        int rest = count(week, WorkoutType.REST_DAY);
        int hard = count(week, WorkoutType.TEMPO_RUN) + count(week, WorkoutType.INTERVAL_RUN);
        int longR = count(week, WorkoutType.LONG_RUN);

        if (coldStart) {
            if (hard > 0) {
                return false;
            }
            if (rest < minRestDays(exp)) {
                return false;
            }
            if ((exp == ExperienceLevel.BEGINNER || exp == ExperienceLevel.CASUAL) && longR > 0) {
                return false;
            }
            if (maxConsecutiveRuns(week) > 2) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the minimum rest days for an experience level.
     */
    private int minRestDays(ExperienceLevel exp) {
        ExperienceLevel level = (exp == null) ? ExperienceLevel.INTERMEDIATE : exp;

        return switch (level) {
            case BEGINNER -> 3;
            case CASUAL -> 2;
            default -> 1;
        };
    }

    /**
     * Counts occurrences of a workout type in a week.
     */
    private int count(List<WorkoutType> week, WorkoutType target) {
        int cnt = 0;
        for (WorkoutType x : week) {
            if (x == target) {
                cnt++;
            }
        }
        return cnt;
    }

    /**
     * Ensures at least N occurrences of a type by converting easy runs first, then non-essential days.
     */
    private void ensureAtLeastN(List<WorkoutType> week, WorkoutType target, int n) {
        int have = count(week, target);

        for (int i = 0; have < n && i < week.size(); i++) {
            if (week.get(i) == WorkoutType.EASY_RUN) {
                week.set(i, target);
                have++;
            }
        }

        for (int i = 0; have < n && i < week.size(); i++) {
            if (week.get(i) != WorkoutType.LONG_RUN && week.get(i) != WorkoutType.REST_DAY) {
                week.set(i, target);
                have++;
            }
        }
    }

    /**
     * Limits maximum consecutive runs by inserting rest days.
     */
    private void capRunStreak(List<WorkoutType> week, int maxStreak) {
        int streak = 0;

        for (int i = 0; i < week.size(); i++) {
            if (isRun(week.get(i))) {
                streak++;
                if (streak > maxStreak) {
                    week.set(i, WorkoutType.REST_DAY);
                    streak = 0;
                }
            } else {
                streak = 0;
            }
        }
    }

    /**
     * Computes the maximum consecutive run streak length for a week.
     */
    private int maxConsecutiveRuns(List<WorkoutType> week) {
        int best = 0;
        int cur = 0;

        for (WorkoutType t : week) {
            if (isRun(t)) {
                cur++;
                best = Math.max(best, cur);
            } else {
                cur = 0;
            }
        }

        return best;
    }

    // =====================================================================
    // 13) STATS (DISTRIBUTIONS)
    // =====================================================================

    /**
     * Converts a sample list into a JuliaDist (p10/p50/p90/mean/std).
     */
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

    /**
     * Converts samples into a LoadDistributionDto (p10/p50/p90/mean/std).
     */
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

    /**
     * Computes a linear-interpolated quantile from a sorted sample list.
     */
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

    /**
     * Computes sample standard deviation (Bessel corrected) from a list and its mean.
     */
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

    // =====================================================================
    // 14) HELPERS + MISC
    // =====================================================================

    /**
     * Returns clamp to [0, 1].
     */
    private double clamp01(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }

    /**
     * Returns true if we have too little history to trust the forecaster/profile.
     */
    private boolean isColdStart(double ctl, double atl, List<Integer> recentLoads) {
        int nonZero = 0;

        if (recentLoads != null) {
            for (Integer x : recentLoads) {
                if (x != null && x > 0) {
                    nonZero++;
                }
            }
        }

        return nonZero < 5 || (ctl <= 5.0 && atl <= 5.0);
    }

    /**
     * Safely evaluates a supplier, returning fallback on exceptions.
     */
    private double safe(SupplierWithException<Double> supplier, double fallback) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Safely evaluates an int supplier, returning fallback on exceptions.
     */
    private int safeInt(SupplierWithException<Integer> supplier, int fallback) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Safely evaluates a list supplier, returning an empty list on exceptions.
     */
    private <T> List<T> safeList(SupplierWithException<List<T>> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Small supplier interface allowing checked exceptions.
     */
    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    /**
     * Represents the chosen plan and optional debug output.
     */
    private record PlanChoice(
            int bestTemplateIndex,
            double bestScore,
            List<WorkoutType> bestTemplate,
            List<LoadDistributionDto> bestTsbDists,
            TrainingPlanDebugDto debug
    ) {
    }

    /**
     * Planner profile derived from history statistics.
     */
    private record PlannerProfile(
            double meanDailyLoad,
            double stdDailyLoad,
            double cv,
            double consistency,
            double riskAversion,
            double targetWeeklyLoad,
            double uncertaintyScale
    ) {
    }

    /**
     * Result of a simulation pass for a template.
     */
    private record SimResult(
            double avgUtility,
            JuliaDist utilDist,
            double riskAdjustedScore,
            List<LoadDistributionDto> tsbDists
    ) {
    }

    /**
     * Debug breakdown to log why a template won.
     */
    private record ScoreBreakdown(
            double simRiskAdj,
            double templatePrior,
            double missingPenalty,
            double expPrior,
            double shape,
            double total
    ) {
    }

    /**
     * Logs a single-line breakdown of scoring terms for a candidate.
     */
    private void logBreakdown(int idx, List<WorkoutType> eff, ScoreBreakdown breakdown) {
        log.info(
                "score {} simRiskAdj={} templatePrior={} missing={} expPrior={} shape={} TOTAL={} eff={}",
                idx,
                round2(breakdown.simRiskAdj),
                round2(breakdown.templatePrior),
                round2(breakdown.missingPenalty),
                round2(breakdown.expPrior),
                round2(breakdown.shape),
                round2(breakdown.total),
                eff
        );
    }

    /**
     * Rounds a value to 2 decimals for logging.
     */
    private double round2(double x) {
        return Math.round(x * 100.0) / 100.0;
    }

    /**
     * Container for resolved overrides + derived state.
     */
    record OverridesResolved(
            double injuryIndex,
            int readiness,
            Map<BodyPart, Double> injuriesMap,
            LoadConstraints constraints,
            List<Integer> recentLoads,
            double ctl,
            double atl
    ) {
    }
}
