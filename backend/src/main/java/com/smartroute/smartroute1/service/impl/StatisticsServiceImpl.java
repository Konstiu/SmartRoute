package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.ConsistencyScoreResultDto;
import com.smartroute.smartroute1.endpoint.dto.DetailedActivityDto;
import com.smartroute.smartroute1.endpoint.dto.ViewInjuryDto;
import com.smartroute.smartroute1.endpoint.dto.statistics.ConsistencyHistoryDto;
import com.smartroute.smartroute1.endpoint.dto.statistics.GymHistoryDto;
import com.smartroute.smartroute1.endpoint.dto.statistics.InjuryHistoryDto;
import com.smartroute.smartroute1.endpoint.dto.statistics.RunHistoryDto;
import com.smartroute.smartroute1.endpoint.mapper.InjuryMapper;
import com.smartroute.smartroute1.endpoint.mapper.RunClassificationMapper;
import com.smartroute.smartroute1.endpoint.mapper.StravaActivityMapper;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Atl;
import com.smartroute.smartroute1.entity.Ctl;
import com.smartroute.smartroute1.entity.GymWorkout;
import com.smartroute.smartroute1.entity.Injuries;
import com.smartroute.smartroute1.entity.Tsb;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.Weekday;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.exception.CannotCalculateConsistencyScoreException;
import com.smartroute.smartroute1.exception.InsufficientTrainingDataException;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.GymWorkoutRepository;
import com.smartroute.smartroute1.repository.InjuryRepository;
import com.smartroute.smartroute1.repository.statistics.AtlRepository;
import com.smartroute.smartroute1.repository.statistics.CtlRepository;
import com.smartroute.smartroute1.repository.statistics.TsbRepository;
import com.smartroute.smartroute1.service.ConsistencyAnalyzerService;
import com.smartroute.smartroute1.service.FatigueAndOverloadService;
import com.smartroute.smartroute1.service.StatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StatisticsServiceImpl implements StatisticsService {

    private final FatigueAndOverloadService fatigueAndOverloadService;
    private final ConsistencyAnalyzerService consistencyAnalyzerService;
    private final ActivityRepository activityRepository;
    private final GymWorkoutRepository gymWorkoutRepository;
    private final InjuryRepository injuryRepository;
    private final TsbRepository tsbRepository;
    private final AtlRepository atlRepository;
    private final CtlRepository ctlRepository;
    private final List<WorkoutType> runTypes = List.of(
            WorkoutType.EASY_RUN,
            WorkoutType.INTERVAL_RUN,
            WorkoutType.LONG_RUN,
            WorkoutType.TEMPO_RUN
    );
    private final InjuryMapper injuryMapper;
    private final StravaActivityMapper stravaActivityMapper;
    private final RunClassificationMapper runClassificationMapper;
    private int numberOfDaysInYear;     //For checking leap years

    public StatisticsServiceImpl(FatigueAndOverloadService fatigueAndOverloadService,
                                 ConsistencyAnalyzerService consistencyAnalyzerService,
                                 ActivityRepository activityRepository,
                                 GymWorkoutRepository gymWorkoutRepository,
                                 InjuryRepository injuryRepository,
                                 TsbRepository tsbRepository,
                                 AtlRepository atlRepository,
                                 CtlRepository ctlRepository,
                                 InjuryMapper injuryMapper,
                                 StravaActivityMapper stravaActivityMapper,
                                 RunClassificationMapper runClassificationMapper) {
        this.fatigueAndOverloadService = fatigueAndOverloadService;
        this.consistencyAnalyzerService = consistencyAnalyzerService;
        this.activityRepository = activityRepository;
        this.gymWorkoutRepository = gymWorkoutRepository;
        this.injuryRepository = injuryRepository;
        this.tsbRepository = tsbRepository;
        this.atlRepository = atlRepository;
        this.ctlRepository = ctlRepository;
        numberOfDaysInYear = 365;
        if (Year.isLeap(LocalDate.now().getYear())) {
            numberOfDaysInYear = 366;
        }
        this.injuryMapper = injuryMapper;
        this.stravaActivityMapper = stravaActivityMapper;
        this.runClassificationMapper = runClassificationMapper;
    }


    @Override
    public InjuryHistoryDto getInjuryHistory(ApplicationUser user) {
        List<Injuries> injuries = injuryRepository.getAllByUserBetweenDateOrderByDateAsc(user, (LocalDate.now().minusYears(1)), LocalDate.now());
        List<ViewInjuryDto> injuryDtos = injuries.stream()
                .map(injuryMapper::entitytoDto)
                .toList();
        return new InjuryHistoryDto(injuries.size(), injuryDtos);
    }

    @Override
    public RunHistoryDto getRunHistory(ApplicationUser user) {

        List<Activity> runs = activityRepository.findRunsInPeriod(user, LocalDate.now().minusYears(1).atStartOfDay(ZoneId.systemDefault()).toInstant(), Instant.now());
        double distance = runs.stream().mapToDouble(Activity::getDistance).sum();
        double time = runs.stream().mapToDouble(Activity::getElapsedTime).sum();
        int numberOfRuns = runs.size();
        List<DetailedActivityDto> activityDtos = runs.stream()
                .map((Activity entity) -> stravaActivityMapper.toDetailedViewDto(entity, runClassificationMapper.entityToDto(entity.getRunTypeClassification())))
                .toList();
        return new RunHistoryDto(numberOfRuns, time, distance, activityDtos);
    }

    @Override
    public ConsistencyHistoryDto getConsistencyHistory(ApplicationUser user) {
        HashMap<Instant, ConsistencyScoreResultDto> consistencyHistory = new HashMap<>();

        Set<Weekday> preferredDays = user.getActiveWeekdays();
        int plannedWeeklySessions = preferredDays.size();
        int minWeeklySessions = getMinWeeklySessions(user.getExperienceLevel());
        int maxWeeklySessions = getMaxWeeklySessions(user.getExperienceLevel());

        HashMap<Instant, Double> ctlHistory = new HashMap<>(ctlRepository.getCtlByUserAndDateBetween(user, LocalDate.now().minusDays(numberOfDaysInYear).atStartOfDay(ZoneId.systemDefault()).toInstant(), Instant.now())
                .stream()
                .collect(Collectors.toMap(
                        Ctl::getDate,
                        Ctl::getScore
                )));
        HashMap<Instant, Double> atlHistory = new HashMap<>(atlRepository.getAtlByUserAndDateBetween(user, LocalDate.now().minusDays(numberOfDaysInYear).atStartOfDay(ZoneId.systemDefault()).toInstant(), Instant.now())
                .stream()
                .collect(Collectors.toMap(
                        Atl::getDate,
                        Atl::getScore
                )));
        HashMap<Instant, Double> tsbHistory =
                new HashMap<>(tsbRepository.getTsbByUserAndDateBetween(user, LocalDate.now().minusDays(numberOfDaysInYear).atStartOfDay(ZoneId.systemDefault()).toInstant(), Instant.now())
                        .stream()
                        .collect(Collectors.toMap(
                                Tsb::getDate,
                                Tsb::getScore
                        )));

        for (int i = 0; i < numberOfDaysInYear; i++) {
            LocalDate localDate = LocalDate.now().minusDays(numberOfDaysInYear - i);
            Instant date = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant from = localDate.minusDays(14).atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant to = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
            try {
                consistencyHistory.put(date, consistencyAnalyzerService.computeScore(
                        user,
                        from,
                        to,
                        Math.clamp(plannedWeeklySessions, minWeeklySessions, maxWeeklySessions))
                );
            } catch (CannotCalculateConsistencyScoreException e) {
                consistencyHistory.put(date, new ConsistencyScoreResultDto(0.0, 0.0, 0.0));
            }

            try {
                if (!ctlHistory.containsKey(date)) {
                    Double ctl = fatigueAndOverloadService.ctlOn(user, localDate);
                    Ctl ctlObject = new Ctl();
                    ctlObject.setDate(date);
                    ctlObject.setScore(ctl);
                    ctlObject.setUser(user);
                    ctlRepository.save(ctlObject);
                    ctlHistory.put(date, ctl);
                }
                if (!atlHistory.containsKey(date)) {
                    Double atl = fatigueAndOverloadService.atlOn(user, localDate);
                    Atl atlObject = new Atl();
                    atlObject.setDate(date);
                    atlObject.setScore(atl);
                    atlObject.setUser(user);
                    atlRepository.save(atlObject);
                    atlHistory.put(date, atl);
                }
                if (!tsbHistory.containsKey(date)) {
                    Double tsb = fatigueAndOverloadService.tsbOn(user, localDate);
                    Tsb tsbObject = new Tsb();
                    tsbObject.setDate(date);
                    tsbObject.setScore(tsb);
                    tsbObject.setUser(user);
                    tsbRepository.save(tsbObject);
                    tsbHistory.put(date, tsb);
                }
            } catch (InsufficientTrainingDataException e) {
                ctlHistory.put(date, 0.0);
                atlHistory.put(date, 0.0);
                tsbHistory.put(date, 0.0);
            }
        }

        return new ConsistencyHistoryDto(consistencyHistory, ctlHistory, atlHistory, tsbHistory);
    }

    @Override
    public GymHistoryDto getGymHistory(ApplicationUser user) {
        List<GymWorkout> workouts = gymWorkoutRepository.findGymWorkoutByUserBetweenDatesOrderByStartDateAsc(user, LocalDate.now().minusDays(numberOfDaysInYear), LocalDate.now());
        return new GymHistoryDto(workouts.size(), workouts);
    }


    private void resetConsistency(ApplicationUser user) {
        atlRepository.deleteAtlsByUser(user);
        tsbRepository.deleteTsbsByUser(user);
        ctlRepository.deleteCtlsByUser(user);
    }

    @Async
    @Override
    public void preLoadConsistencyHistory(ApplicationUser user) {
        resetConsistency(user);
        getConsistencyHistory(user);
    }

    // Min weekly sessions by experience (recommendations for beginner, intermediate, advanced from: https://pubmed.ncbi.nlm.nih.gov/19204579/)
    private int getMinWeeklySessions(ExperienceLevel experienceLevel) {
        return switch (experienceLevel) {
            case BEGINNER, CASUAL -> 2;
            case INTERMEDIATE -> 3;
            case ADVANCED, COMPETITIVE_ATHLETE -> 4;
        };
    }

    // Max weekly sessions by experience (recommendations for beginner, intermediate, advanced from: https://pubmed.ncbi.nlm.nih.gov/19204579/)
    private int getMaxWeeklySessions(ExperienceLevel experienceLevel) {
        return switch (experienceLevel) {
            case BEGINNER -> 3;
            case CASUAL, INTERMEDIATE -> 4;
            case ADVANCED -> 5;
            case COMPETITIVE_ATHLETE -> 6;
        };
    }
}
