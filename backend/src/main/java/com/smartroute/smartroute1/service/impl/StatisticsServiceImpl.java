package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.ConsistencyScoreResultDto;
import com.smartroute.smartroute1.endpoint.dto.DetailedActivityDto;
import com.smartroute.smartroute1.endpoint.dto.ViewInjuryDto;
import com.smartroute.smartroute1.endpoint.dto.statistics.ConsistencyHistoryDto;
import com.smartroute.smartroute1.endpoint.dto.statistics.GymHistoryDto;
import com.smartroute.smartroute1.endpoint.dto.statistics.InjuryHistoryDto;
import com.smartroute.smartroute1.endpoint.dto.statistics.RunHistoryDto;
import com.smartroute.smartroute1.endpoint.mapper.InjuryMapper;
import com.smartroute.smartroute1.endpoint.mapper.StravaActivityMapper;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.GymWorkout;
import com.smartroute.smartroute1.entity.Injuries;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.Weekday;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.exception.CannotCalculateConsistencyScoreException;
import com.smartroute.smartroute1.exception.InsufficientTrainingDataException;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.GymWorkoutRepository;
import com.smartroute.smartroute1.repository.InjuryRepository;
import com.smartroute.smartroute1.service.ConsistencyAnalyzerService;
import com.smartroute.smartroute1.service.FatigueAndOverloadService;
import com.smartroute.smartroute1.service.StatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

@Service
@Slf4j
public class StatisticsServiceImpl implements StatisticsService {

    private final FatigueAndOverloadService fatigueAndOverloadService;
    private final ConsistencyAnalyzerService consistencyAnalyzerService;
    private final ActivityRepository activityRepository;
    private final GymWorkoutRepository gymWorkoutRepository;
    private final InjuryRepository injuryRepository;
    private final List<WorkoutType> runTypes = List.of(
            WorkoutType.EASY_RUN,
            WorkoutType.INTERVAL_RUN,
            WorkoutType.LONG_RUN,
            WorkoutType.TEMPO_RUN
    );
    private final InjuryMapper injuryMapper;
    private final StravaActivityMapper stravaActivityMapper;
    private int numberOfDaysInYear;     //For checking leap years

    public StatisticsServiceImpl(FatigueAndOverloadService fatigueAndOverloadService,
                                 ConsistencyAnalyzerService consistencyAnalyzerService,
                                 ActivityRepository activityRepository,
                                 GymWorkoutRepository gymWorkoutRepository,
                                 InjuryRepository injuryRepository, InjuryMapper injuryMapper, StravaActivityMapper stravaActivityMapper) {
        this.fatigueAndOverloadService = fatigueAndOverloadService;
        this.consistencyAnalyzerService = consistencyAnalyzerService;
        this.activityRepository = activityRepository;
        this.gymWorkoutRepository = gymWorkoutRepository;
        this.injuryRepository = injuryRepository;
        numberOfDaysInYear = 365;
        if (Year.isLeap(LocalDate.now().getYear())) {
            numberOfDaysInYear = 366;
        }
        this.injuryMapper = injuryMapper;
        this.stravaActivityMapper = stravaActivityMapper;
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
                .map(stravaActivityMapper::toDetailedViewDto)
                .toList();
        return new RunHistoryDto(numberOfRuns, time, distance, activityDtos);
    }

    @Override
    public ConsistencyHistoryDto getConsistencyHistory(ApplicationUser user) {
        HashMap<Instant, ConsistencyScoreResultDto> consistencyHistory = new HashMap<>();
        HashMap<Instant, Double> ctlHistory = new HashMap<>();
        HashMap<Instant, Double> atlHistory = new HashMap<>();
        HashMap<Instant, Double> tsbHistory = new HashMap<>();
        Set<Weekday> preferredDays = user.getActiveWeekdays();
        int plannedWeeklySessions = preferredDays.size();
        int minWeeklySessions = getMinWeeklySessions(user.getExperienceLevel());
        int maxWeeklySessions = getMaxWeeklySessions(user.getExperienceLevel());

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
                ctlHistory.put(date, fatigueAndOverloadService.ctlOn(user, localDate));
                atlHistory.put(date, fatigueAndOverloadService.atlOn(user, localDate));
                tsbHistory.put(date, fatigueAndOverloadService.tsbOn(user, localDate));
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

    // Min weekly sessions by experience (recommendations for beginner, intermediate, advanced from: https://pubmed.ncbi.nlm.nih.gov/19204579/)
    private int getMinWeeklySessions(ExperienceLevel experienceLevel) {
        return switch (experienceLevel) {
            case BEGINNER -> 2;
            case CASUAL -> 2;
            case INTERMEDIATE -> 3;
            case ADVANCED -> 4;
            case COMPETITIVE_ATHLETE -> 4;
        };
    }

    // Max weekly sessions by experience (recommendations for beginner, intermediate, advanced from: https://pubmed.ncbi.nlm.nih.gov/19204579/)
    private int getMaxWeeklySessions(ExperienceLevel experienceLevel) {
        return switch (experienceLevel) {
            case BEGINNER -> 3;
            case CASUAL -> 4;
            case INTERMEDIATE -> 4;
            case ADVANCED -> 5;
            case COMPETITIVE_ATHLETE -> 6;
        };
    }
}
