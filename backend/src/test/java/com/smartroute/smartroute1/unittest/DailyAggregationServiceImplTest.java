package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.DailySummary;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.DailyAggregationService;
import com.smartroute.smartroute1.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;

@Transactional
@SpringBootTest
@ActiveProfiles({"test", "generateData"})
class DailyAggregationServiceImplTest {

    @Autowired
    private DailyAggregationService aggregationService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityRepository activityRepository;

    private ApplicationUser user;
    private static final String USEREMAIL = "agg-test@smartrun.com";

    @BeforeEach
    public void setUp() {
        user = new ApplicationUser();
        user.setFirstname("Test");
        user.setLastname("User");
        user.setPassword("password");
        user.setSex(Sex.OTHER);
        user.setEmail(USEREMAIL);

        userRepository.save(user);
    }

    @Test
    void aggregatesActivitiesAndFillsMissingDays() throws Exception {
        Long userId = user.getId();

        Instant now = Instant.now();

        Activity activity = new Activity();
        activity.setUser(user);
        activity.setStartDateLocal(now);
        activity.setStartDate(now);
        activity.setDistance(6000);
        activity.setMovingTime(1800);
        activity.setTotalElevationGain(80);
        activity.setSessionLoad(45);
        activityRepository.save(activity);

        List<DailySummary> summaries = aggregationService.getDailySummaries(userId, 7);


        long nonZeroDays = summaries.stream()
                .filter(d -> d.getTotalLoad() > 0)
                .count();


        DailySummary trainingDay = summaries.stream()
                .filter(d -> d.getTotalLoad() > 0)
                .findFirst()
                .orElseThrow();

        assertAll(
                () -> assertEquals(7, summaries.size()),
                () -> assertEquals(1, nonZeroDays),
                () -> assertEquals(45, trainingDay.getTotalLoad()),
                () -> assertEquals(6000, Math.round(trainingDay.getTotalDistanceMeters())),
                () -> assertEquals(1800, trainingDay.getTotalMovingTimeSeconds()),
                () -> assertEquals(false, trainingDay.isHasHeartRate())
        );
    }
}

