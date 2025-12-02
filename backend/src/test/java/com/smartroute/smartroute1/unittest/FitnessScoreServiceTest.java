package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.endpoint.dto.StravaStreamDto;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.AthleteZone;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.AthleteZoneRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.FitnessScoreService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles({"test", "generateData"})
@Transactional
class FitnessScoreServiceTest extends BaseTest {
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FitnessScoreService fitnessScoreService;
    @Autowired
    private AthleteZoneRepository athleteZoneRepository;

    @BeforeEach
    void beforeEach() {
        activityRepository.deleteAll();
        athleteZoneRepository.deleteAll();
    }

    @Test
    void testFitnessScore_calculatedCorrectly() {
        ApplicationUser user = userRepository.findAll().getFirst();
        Activity a1 = new Activity();
        Activity a2 = new Activity();

        a1.setUser(user);
        a1.setType("Run");
        a1.setSessionLoad(100);
        a1.setStartDate(Instant.now());

        a2.setUser(user);
        a2.setType("Run");
        a2.setSessionLoad(50);
        a2.setStartDate(Instant.now());

        activityRepository.save(a1);
        activityRepository.save(a2);

        int fitnessScore = fitnessScoreService.calculateFitnessScore(Instant.now(), user);

        assertEquals(150, fitnessScore);
    }

    @Test
    void testFitnessScore_whenNoActivitiesOnThatDay_returnsZero() {
        ApplicationUser user = userRepository.findAll().getFirst();
        Activity a1 = new Activity();
        Activity a2 = new Activity();

        a1.setUser(user);
        a1.setType("Run");
        a1.setSessionLoad(100);
        a1.setStartDate(Instant.now());

        a2.setUser(user);
        a2.setType("Run");
        a2.setSessionLoad(50);
        a2.setStartDate(Instant.now());

        activityRepository.save(a1);
        activityRepository.save(a2);

        int fitnessScore = fitnessScoreService.calculateFitnessScore(Instant.now().minus(1, ChronoUnit.DAYS), user);

        assertEquals(0, fitnessScore);
    }

    @Test
    void testFitnessScore_ignoresNonRunningActivities() {
        ApplicationUser user = userRepository.findAll().getFirst();
        Activity a1 = new Activity();
        Activity a2 = new Activity();

        a1.setUser(user);
        a1.setType("Run");
        a1.setSessionLoad(100);
        a1.setStartDate(Instant.now());

        a2.setUser(user);
        a2.setType("Ride");
        a2.setSessionLoad(50);
        a2.setStartDate(Instant.now());

        activityRepository.save(a1);
        activityRepository.save(a2);

        int fitnessScore = fitnessScoreService.calculateFitnessScore(Instant.now(), user);

        assertEquals(100, fitnessScore);
    }

    @Test
    void testCalculateSessionLoad_fromSufferScore_returnsCorrectFitnessScore() {
        int sufferScore = 50;
        int elevationGain = 120;

        float elevationFactor = 1 + 0.05f * elevationGain / 100;

        Integer fitnessScore = fitnessScoreService.calculateSessionLoad(sufferScore, elevationGain);

        assertEquals(fitnessScore, Math.round(sufferScore * elevationFactor));
    }

    @Test
    void testCalculateSessionLoad_fromHeartRateZones_returnsCorrectFitnessScore() {
        ApplicationUser user = userRepository.findAll().getFirst();

        Activity a1 = new Activity();
        a1.setUser(user);
        a1.setType("Run");
        a1.setTotalElevationGain(0);
        a1.setDistance(10000);
        a1.setMovingTime(3600);

        a1 = activityRepository.saveAndFlush(a1);

        AthleteZone z1 = new AthleteZone();
        z1.setZoneIndex(1);
        z1.setMin(0);
        z1.setMax(119);
        z1.setUser(user);
        AthleteZone z2 = new AthleteZone();
        z2.setZoneIndex(2);
        z2.setMin(120);
        z2.setMax(139);
        z2.setUser(user);
        AthleteZone z3 = new AthleteZone();
        z3.setZoneIndex(3);
        z3.setMin(140);
        z3.setMax(159);
        z3.setUser(user);
        AthleteZone z4 = new AthleteZone();
        z4.setZoneIndex(4);
        z4.setMin(160);
        z4.setMax(179);
        z4.setUser(user);
        AthleteZone z5 = new AthleteZone();
        z5.setZoneIndex(5);
        z5.setMin(180);
        z5.setMax(-1);
        z5.setUser(user);
        athleteZoneRepository.saveAllAndFlush(List.of(z1, z2, z3, z4, z5));

        StravaStreamDto time = new StravaStreamDto();
        time.setType("time");
        time.setData(List.of(0f,60f,120f,180f,240f,300f,360f));
        StravaStreamDto heartRate = new StravaStreamDto();
        heartRate.setType("heartrate");
        heartRate.setData(List.of(100f,120f,120f,120f,140f,140f,160f));

        // K_i * TK_i * t_i, with t_i in hours
        float t1 = 30 * (60f / 3600f) * Math.min(1, 60f / 60f / 75f);
        float t2 = 60 * (180f / 3600f) * Math.min(1, 180f / 60f / 75f);
        float t3 = 120 * (120f / 3600f);
        int trimp = Math.round(t1 + t2 + t3);

        List<StravaStreamDto> heartRateStream = List.of(time, heartRate);

        Integer fitnessScore = fitnessScoreService.calculateSessionLoad(heartRateStream, a1);

        assertEquals(trimp, fitnessScore);
    }

    @Test
    void testCalculateSessionLoad_fromHeartRateZones_whenHeartRateStreamIncorrect_fallsBackToDistanceCalculation() {
        ApplicationUser user = userRepository.findAll().getFirst();

        Activity a1 = new Activity();
        a1.setUser(user);
        a1.setType("Run");
        a1.setTotalElevationGain(0);
        a1.setDistance(10000);
        a1.setMovingTime(3600);

        a1 = activityRepository.saveAndFlush(a1);

        AthleteZone z1 = new AthleteZone();
        z1.setZoneIndex(1);
        z1.setMin(0);
        z1.setMax(119);
        z1.setUser(user);
        AthleteZone z2 = new AthleteZone();
        z2.setZoneIndex(2);
        z2.setMin(120);
        z2.setMax(139);
        z2.setUser(user);
        AthleteZone z3 = new AthleteZone();
        z3.setZoneIndex(3);
        z3.setMin(140);
        z3.setMax(159);
        z3.setUser(user);
        AthleteZone z4 = new AthleteZone();
        z4.setZoneIndex(4);
        z4.setMin(160);
        z4.setMax(179);
        z4.setUser(user);
        AthleteZone z5 = new AthleteZone();
        z5.setZoneIndex(5);
        z5.setMin(180);
        z5.setMax(-1);
        z5.setUser(user);
        athleteZoneRepository.saveAllAndFlush(List.of(z1, z2, z3, z4, z5));

        StravaStreamDto time = new StravaStreamDto();
        time.setType("incorrectType");
        time.setData(List.of(0f,60f,120f,180f,240f,300f,360f));
        StravaStreamDto heartRate = new StravaStreamDto();
        heartRate.setType("heartrate");
        heartRate.setData(List.of(100f,120f,120f,120f,140f,140f,160f));


        int load = Math.round(a1.getDistance() * a1.getMovingTime() / 12);

        List<StravaStreamDto> heartRateStream = List.of(time, heartRate);

        Integer fitnessScore = fitnessScoreService.calculateSessionLoad(heartRateStream, a1);

        assertEquals(load, fitnessScore);
    }

    @Test
    void testCalculateSessionLoad_fromPower_returnsCorrectFitnessScore() {
        int ftp = 250;
        int movingTime = 3600;
        float averageWatts = 190;
        float totalElevationGain = 0;

        Integer fitnessScore = fitnessScoreService.calculateSessionLoad(ftp, movingTime, averageWatts, totalElevationGain);

        int load = Math.round((float) movingTime / 3600 * (float) Math.pow(averageWatts / ftp, 2) * 100);

        assertEquals(load, fitnessScore);
    }

    @Test
    void testCalculateSessionLoad_fromKilojoules_returnsCorrectFitnessScore() {
        float kilojoules = 4000;
        float weight = 85;
        float totalElevationGain = 0;

        Integer fitnessScore = fitnessScoreService.calculateSessionLoad(kilojoules, weight, totalElevationGain);

        int load = Math.round(kilojoules / weight);

        assertEquals(load, fitnessScore);
    }
}
