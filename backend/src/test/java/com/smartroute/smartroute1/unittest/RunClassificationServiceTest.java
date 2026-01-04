package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.endpoint.dto.RunClassificationDecisionDto;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.AthleteZone;
import com.smartroute.smartroute1.entity.RunClassificationDecision;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.RunType;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.entity.enums.Weekday;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.AthleteZoneRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.FitnessScoreService;
import com.smartroute.smartroute1.service.RunClassificationService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles({"test", "generateData"})
@Transactional
class RunClassificationServiceTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RunClassificationService service;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private AthleteZoneRepository athleteZoneRepository;
    @Autowired
    private FitnessScoreService fitnessScoreService;

    @BeforeEach
    void setUp() {
        activityRepository.deleteAll();

        if (userRepository.findUserByEmail("a@b") == null) {
            ApplicationUser user = new ApplicationUser();
            user.setSex(Sex.MALE);
            user.setHeight(123);
            user.setWeight(BigDecimal.valueOf(67));
            user.setFtp(250);
            user.setExperienceLevel(ExperienceLevel.BEGINNER);
            user.setFirstname("a");
            user.setLastname("b");
            user.setEmail("a@b");
            user.setPassword("aaaa");
            HashSet<Weekday> set = new HashSet<>();
            set.add(Weekday.MONDAY);
            user.setActiveWeekdays(set);
            user.setBirthdate(LocalDate.now().minusYears(30));
            userRepository.save(user);
        }

        setupUserAndZones();
    }

    void setupUserAndZones() {
        ApplicationUser user = userRepository.getByEmail("a@b");
        user.setExperienceLevel(ExperienceLevel.BEGINNER);
        userRepository.save(user);

        // maxHR = 200
        List<AthleteZone> zones = athleteZoneRepository.findAllByUser(user);
        zones.forEach(zone -> {
            switch (zone.getZoneIndex()) {
                case 1 -> {
                    zone.setMin(0);
                    zone.setMax(119);
                }
                case 2 -> {
                    zone.setMin(120);
                    zone.setMax(139);
                }
                case 3 -> {
                    zone.setMin(140);
                    zone.setMax(159);
                }
                case 4 -> {
                    zone.setMin(160);
                    zone.setMax(179);
                }
                case 5 -> {
                    zone.setMin(180);
                    zone.setMax(-1);
                }
            }
        });
        athleteZoneRepository.saveAll(zones);
    }

    Activity getTemplateRun(int distance, int elevation, RunType runType, ExperienceLevel experienceLevel) {
        Activity activity = new Activity();
        activity.setName("Activity");
        activity.setSportType("Run");
        activity.setType("Run");

        double paceFactor = switch (runType) {
            case RunType.EASY_RUN -> 1;
            case RunType.LONG_RUN -> 1.05;
            case RunType.TEMPO_RUN -> 1.4;
            case RunType.INTERVAL_RUN -> 1.25;
        };

        int movingTime = (int) switch (experienceLevel) {
            case ExperienceLevel.BEGINNER -> distance / (2.4 * paceFactor);
            case ExperienceLevel.CASUAL -> distance / (2.8 * paceFactor);
            case ExperienceLevel.INTERMEDIATE -> distance / (3.4 * paceFactor);
            case ExperienceLevel.ADVANCED -> distance / (3.8 * paceFactor);
            case ExperienceLevel.COMPETITIVE_ATHLETE -> distance / (4.2 * paceFactor);
        };

        activity.setDistance(distance);
        activity.setMovingTime(movingTime);

        activity.setElapsedTime(movingTime);

        activity.setTotalElevationGain(elevation);

        activity.setStartDate(Instant.now());

        activity.setAverageSpeed((float) distance / movingTime);

        switch (runType) {
            case TEMPO_RUN -> activity.setMaxSpeed((float) (distance / movingTime) * 1.4f);
            case LONG_RUN -> activity.setMaxSpeed((float) (distance / movingTime) * 1.25f);
            case EASY_RUN -> activity.setMaxSpeed((float) (distance / movingTime) * 1.15f);
            case INTERVAL_RUN -> activity.setMaxSpeed((float) (distance / movingTime) * 1.5f);
        }

        switch (runType) {
            case TEMPO_RUN -> {
                activity.setAverageHeartrate(170f);
                activity.setMaxHeartrate(180f);

                activity.setTimeZ1((int) (movingTime * .05));
                activity.setTimeZ2((int) (movingTime * .20));
                activity.setTimeZ3((int) (movingTime * .30));
                activity.setTimeZ4((int) (movingTime * .30));
                activity.setTimeZ5((int) (movingTime * .15));
            }
            case LONG_RUN -> {
                activity.setAverageHeartrate(145f);
                activity.setMaxHeartrate(155f);

                activity.setTimeZ1((int) (movingTime * .10));
                activity.setTimeZ2((int) (movingTime * .70));
                activity.setTimeZ3((int) (movingTime * .15));
                activity.setTimeZ4((int) (movingTime * .05));
                activity.setTimeZ5((int) (movingTime * .0));
            }
            case EASY_RUN -> {
                activity.setAverageHeartrate(130f);
                activity.setMaxHeartrate(145f);

                activity.setTimeZ1((int) (movingTime * .15));
                activity.setTimeZ2((int) (movingTime * .80));
                activity.setTimeZ3((int) (movingTime * .05));
                activity.setTimeZ4((int) (movingTime * .0));
                activity.setTimeZ5((int) (movingTime * .0));
            }
            case INTERVAL_RUN -> {
                activity.setAverageHeartrate(160f);
                activity.setMaxHeartrate(190f);

                activity.setTimeZ1((int) (movingTime * .05));
                activity.setTimeZ2((int) (movingTime * .30));
                activity.setTimeZ3((int) (movingTime * .30));
                activity.setTimeZ4((int) (movingTime * .20));
                activity.setTimeZ5((int) (movingTime * .15));
            }
        }

        activity.setSessionLoad(fitnessScoreService.calculateSessionLoad(distance, movingTime));

        activity.setActivityStream(null);

        return activity;
    }

    Activity saveActivity(Activity activity) {
        ApplicationUser user = userRepository.getByEmail("a@b");

        activity.setUser(user);
        return activityRepository.save(activity);
    }

    @Test
    void testClassifyRun_savesClassification() {
        Activity activity = getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER);

        activity = saveActivity(activity);

        RunClassificationDecisionDto dto = service.classifyRun(activity.getId());

        Activity saved = activityRepository.getReferenceById(activity.getId());

        assertAll(
                () -> assertNotNull(dto),
                () -> assertNotNull(saved.getRunTypeClassification()),
                () -> Assertions.assertEquals(dto.getRunType(), saved.getRunTypeClassification().getRunType()),
                () -> assertEquals(dto.getProbabilities().get(RunType.EASY_RUN), saved.getRunTypeClassification().getProbabilities().get(RunType.EASY_RUN)),
                () -> assertEquals(dto.getProbabilities().get(RunType.INTERVAL_RUN), saved.getRunTypeClassification().getProbabilities().get(RunType.INTERVAL_RUN)),
                () -> assertEquals(dto.getProbabilities().get(RunType.LONG_RUN), saved.getRunTypeClassification().getProbabilities().get(RunType.LONG_RUN)),
                () -> assertEquals(dto.getProbabilities().get(RunType.TEMPO_RUN), saved.getRunTypeClassification().getProbabilities().get(RunType.TEMPO_RUN))
        );
    }

    @Test
    void testClassifyRun_whenActivityIdDoesNotExist_throws() {
        assertThrows(NoSuchElementException.class, () -> service.classifyRun(-1L));
    }

    @Test
    void testClassifyRun_whenActivitySportTypeNotRun_returnsNull() {
        Activity activity = new Activity();
        activity.setSportType("Not a run");

        Activity a = saveActivity(activity);

        assertNull(service.classifyRun(a.getId()));
    }

    @Test
    void testClassifyRun_withTempoRunAndEasyRunsAsBaseline_classifiesAsTempoRun() {
        List<Activity> previousRuns = List.of(
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER)
        );
        previousRuns.forEach(this::saveActivity);

        Activity tempoRun = getTemplateRun(5000, 0, RunType.TEMPO_RUN, ExperienceLevel.BEGINNER);
        saveActivity(tempoRun);

        RunClassificationDecisionDto dto = service.classifyRun(tempoRun.getId());

        assertAll(
                () -> assertNotNull(dto),
                () -> assertEquals(RunType.TEMPO_RUN, dto.getRunType())
        );
    }

    @Test
    void testClassifyRun_missingHR_dataDoesNotCrash() {
        Activity activity = getTemplateRun(5000, 0, RunType.INTERVAL_RUN, ExperienceLevel.BEGINNER);

        activity.setAverageHeartrate(null);
        activity.setMaxHeartrate(null);

        saveActivity(activity);

        RunClassificationDecisionDto dto = service.classifyRun(activity.getId());

        assertAll(
                () -> assertNotNull(dto),
                () -> assertNotNull(dto.getRunType()),
                () -> assertNotNull(dto.getRunType())
        );
    }

    @Test
    void testClassifyRun_probabilitiesSumToOne() {
        Activity activity = getTemplateRun(5000, 0, RunType.TEMPO_RUN, ExperienceLevel.BEGINNER);
        saveActivity(activity);

        RunClassificationDecisionDto dto = service.classifyRun(activity.getId());

        double sum = dto.getProbabilities().values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 1e-6, "Probabilities should sum to 1");
    }

    @Test
    void testClassifyRun_classificationAdaptsToImprovingUser() {
        // Baseline
        List<Activity> previousRuns = List.of(
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER)
        );
        previousRuns.forEach(this::saveActivity);

        Activity benchmarkRun1 = getTemplateRun(12000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER);
        saveActivity(benchmarkRun1);

        RunClassificationDecisionDto benchmarkRun1dto = service.classifyRun(benchmarkRun1.getId());

        // User starts improving
        previousRuns = List.of(
                getTemplateRun(7000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(8000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(9000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(13000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(9000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(9000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(12000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(14000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(14000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(19000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(19000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(14000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER),
                getTemplateRun(18000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER)
        );
        previousRuns.forEach(this::saveActivity);

        // User repeats identical benchmark run
        Activity benchmarkRun2 = getTemplateRun(12000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER);
        saveActivity(benchmarkRun2);

        RunClassificationDecisionDto benchmarkRun2dto = service.classifyRun(benchmarkRun2.getId());

        assertAll(
                () -> assertTrue(benchmarkRun1dto.getProbabilities().get(RunType.LONG_RUN) > benchmarkRun2dto.getProbabilities().get(RunType.LONG_RUN)),
                () -> assertNotEquals(RunType.LONG_RUN, benchmarkRun2dto.getRunType())
        );
    }

    @Test
    public void test_WhenCorrectRun_UpdatesMapSuccessfully() {
        Activity activity = getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER);
        activity.setRunTypeClassification(new RunClassificationDecision(new HashMap<>(), RunType.EASY_RUN));
        activity = saveActivity(activity);
        Long activityId = activity.getId();

        service.correctRun(activityId, RunType.LONG_RUN);

        Activity result = activityRepository.findById(activityId).get();

        assertAll(
                () -> assertEquals(0.01, result.getUser().getCorrectionMap().getEasyToLong()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getEasyToInterval()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getEasyToTempo()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getLongToTempo()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getLongToInterval()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getLongToEasy()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getIntervalToTempo()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getIntervalToEasy()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getIntervalToLong()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getTempoToLong()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getTempoToInterval()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getTempoToEasy()),
                () -> assertEquals(RunType.LONG_RUN, result.getRunTypeClassification().getRunType())
        );


    }

    @Test
    public void test_WhenCorrectRunWithSameRunType_ThenNoChange() {
        Activity activity = getTemplateRun(5000, 0, RunType.EASY_RUN, ExperienceLevel.BEGINNER);
        activity.setRunTypeClassification(new RunClassificationDecision(new HashMap<>(), RunType.EASY_RUN));
        activity = saveActivity(activity);
        Long activityId = activity.getId();

        service.correctRun(activityId, RunType.EASY_RUN);

        Activity result = activityRepository.findById(activityId).get();

        assertAll(
                () -> assertEquals(0, result.getUser().getCorrectionMap().getEasyToLong()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getEasyToInterval()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getEasyToTempo()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getLongToTempo()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getLongToInterval()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getLongToEasy()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getIntervalToTempo()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getIntervalToEasy()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getIntervalToLong()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getTempoToLong()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getTempoToInterval()),
                () -> assertEquals(0, result.getUser().getCorrectionMap().getTempoToEasy()),
                () -> assertEquals(RunType.EASY_RUN, result.getRunTypeClassification().getRunType())
        );
    }

}
