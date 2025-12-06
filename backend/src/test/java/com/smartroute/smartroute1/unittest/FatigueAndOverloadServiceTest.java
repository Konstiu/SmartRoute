package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.exception.InsufficientTrainingDataException;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.FatigueAndOverloadService;
import com.smartroute.smartroute1.service.FitnessScoreService;
import com.smartroute.smartroute1.service.InjuryAwareTrainingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.smartroute.smartroute1.basetest.TestData.DEFAULT_USER_EMAIL;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest()
@ActiveProfiles({"test", "generateData"})
class FatigueAndOverloadServiceTest extends BaseTest {

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private FitnessScoreService fitnessScoreService;

    @Autowired
    private FatigueAndOverloadService service;

    @Autowired
    private UserRepository userRepository;

    private ApplicationUser testUser;

    @Test
    void currentCtl_noActivities_throwsException() {
        activityRepository.deleteAll();
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);

        assertThatThrownBy(() -> service.currentCtl(testUser))
                .isInstanceOf(InsufficientTrainingDataException.class)
                .hasMessageContaining("No training history available");
    }

    @Test
    void currentAtl_noActivities_throwsException() {
        activityRepository.deleteAll();
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);

        assertThatThrownBy(() -> service.currentAtl(testUser))
                .isInstanceOf(InsufficientTrainingDataException.class)
                .hasMessageContaining("No training history available");
    }

    @Test
    void currentTsb_noActivities_throwsException() {
        activityRepository.deleteAll();
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);

        assertThatThrownBy(() -> service.currentTsb(testUser))
                .isInstanceOf(InsufficientTrainingDataException.class)
                .hasMessageContaining("No training history available");
    }


    @Test
    void current_singleDay_returnsInitialFitnessScore() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        List<Activity> activities = activityRepository.findByUser(testUser);
        assertThat(activities).isNotEmpty();

        Activity firstActivity = activities.get(0);
        activityRepository.deleteAll();
        createActivity(firstActivity.getStartDate(), firstActivity.getDistance(), firstActivity.getMovingTime(), firstActivity.getTotalElevationGain());

        LocalDate activityLocalDate = firstActivity.getStartDate()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        Instant startOfActivityDay = activityLocalDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();


        double expectedScore = fitnessScoreService.calculateFitnessScore(
                startOfActivityDay,
                testUser
        );

        double tsb = service.tsbOn(testUser, activityLocalDate);
        double ctl = service.ctlOn(testUser, activityLocalDate);
        double atl = service.atlOn(testUser, activityLocalDate);

        assertAll(
                () -> assertThat(ctl).isEqualTo(expectedScore),
                () -> assertThat(atl).isEqualTo(expectedScore),
                () -> assertThat(tsb).isEqualTo(0.0)
        );
    }


    @Test
    void currentCtl_noActivities_throwsInsufficientTrainingDataException() {
        activityRepository.deleteAll();
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);

        assertThatThrownBy(() -> service.currentCtl(testUser))
                .isInstanceOf(InsufficientTrainingDataException.class)
                .hasMessageContaining("No training history available for user");
    }


    @Test
    void currentCtl_multipleDays_calculatesEmaCorrectly() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();

        // Arrange
        Instant day1 = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant day2 = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant day3 = Instant.now();

        createActivity(day1, 10000f, 3600, 100f);
        createActivity(day2, 8000f, 3000, 80f);
        createActivity(day3, 15000f, 5400, 150f);

        // Act
        double ctl = service.currentCtl(testUser);
        List<Double> ctlHistory = service.getCtlHistory(testUser);

        // Assert
        assertAll(
                () -> assertThat(ctl).isGreaterThan(0.0),
                () -> assertThat(ctlHistory).hasSize(3),
                () -> assertThat(ctlHistory.get(2)).isEqualTo(ctl),
                // Verify EMA is applied (not simple average)
                () -> assertThat(ctl).isNotEqualTo((ctlHistory.get(0) + ctlHistory.get(1) + ctl) / 3.0)
        );
    }

    @Test
    void currentAtl_noActivities_throwsInsufficientTrainingDataException() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();

        assertThatThrownBy(() -> service.currentAtl(testUser))
                .isInstanceOf(InsufficientTrainingDataException.class)
                .hasMessageContaining("No training history available for user");
    }

    @Test
    void currentAtl_singleDay_returnsInitialFitnessScore() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        Activity activity = createActivity(Instant.now(), 10000f, 3600, 100f);
        double expectedScore = calculateExpectedScore(activity);
        double atl = service.currentAtl(testUser);
        assertThat(atl).isEqualTo(expectedScore);
    }

    @Test
    void currentAtl_multipleDays_respondsQuickerThanCtl() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        Instant baseDay = Instant.now().minus(5, ChronoUnit.DAYS);
        for (int i = 0; i < 5; i++) {
            createActivity(baseDay.plus(i, ChronoUnit.DAYS), 10000f, 3600, 100f);
        }

        double ctlBefore = service.currentCtl(testUser);
        double atlBefore = service.currentAtl(testUser);

        // Big spike in training
        createActivity(Instant.now(), 30000f, 10800, 300f);

        double ctlAfter = service.currentCtl(testUser);
        double atlAfter = service.currentAtl(testUser);

        double ctlIncrease = (ctlAfter - ctlBefore) / ctlBefore;
        double atlIncrease = (atlAfter - atlBefore) / atlBefore;

        assertThat(atlIncrease).isGreaterThan(ctlIncrease);
    }


    @Test
    void currentTsb_noActivities_throwsInsufficientTrainingDataException() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        assertThatThrownBy(() -> service.currentTsb(testUser))
                .isInstanceOf(InsufficientTrainingDataException.class)
                .hasMessageContaining("No training history available for user");
    }

    @Test
    void currentTsb_singleDay_returnsZero() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        createActivity(Instant.now(), 10000f, 3600, 100f);

        double tsb = service.currentTsb(testUser);

        assertThat(tsb).isEqualTo(0.0);
    }

    @Test
    void currentTsb_increasingLoad_becomesMoreNegative() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        Instant day1 = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant day2 = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant day3 = Instant.now();

        createActivity(day1, 10000f, 3600, 100f);
        double tsb1 = service.tsbOn(testUser, day1.atZone(ZoneOffset.systemDefault()).toLocalDate());

        createActivity(day2, 15000f, 5400, 150f);
        double tsb2 = service.tsbOn(testUser, day2.atZone(ZoneOffset.systemDefault()).toLocalDate());

        createActivity(day3, 20000f, 7200, 200f);
        double tsb3 = service.tsbOn(testUser, day3.atZone(ZoneOffset.systemDefault()).toLocalDate());

        assertAll(
                () -> assertThat(tsb1).isEqualTo(0.0),
                () -> assertThat(tsb2).isLessThan(tsb1),
                () -> assertThat(tsb3).isLessThan(tsb2)
        );
    }

    // ==================== ctlOn() - Branch Coverage ====================

    @Test
    void ctlOn_noActivities_throwsInsufficientTrainingDataException() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        LocalDate today = LocalDate.now();

        assertThatThrownBy(() -> service.ctlOn(testUser, today))
                .isInstanceOf(InsufficientTrainingDataException.class)
                .hasMessageContaining("No training history available on or before");
    }

    @Test
    void ctlOn_dateBeforeFirstActivity_throwsInsufficientTrainingDataException() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Arrange
        createActivity(Instant.now(), 10000f, 3600, 100f);
        LocalDate yesterday = LocalDate.now().minusDays(5);

        // Act & Assert
        assertThatThrownBy(() -> service.ctlOn(testUser, yesterday))
                .isInstanceOf(InsufficientTrainingDataException.class)
                .hasMessageContaining("No training history available on or before");
    }

    @Test
    void ctlOn_validDate_returnsCorrectCtl() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Arrange
        Instant day1 = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant day2 = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant day3 = Instant.now();

        createActivity(day1, 10000f, 3600, 100f);
        createActivity(day2, 8000f, 3000, 80f);
        createActivity(day3, 15000f, 5400, 150f);

        LocalDate date2 = day2.atZone(ZoneId.systemDefault()).toLocalDate();

        // Act
        double ctlDay2 = service.ctlOn(testUser, date2);
        double currentCtl = service.currentCtl(testUser);

        // Assert
        assertAll(
                () -> assertThat(ctlDay2).isGreaterThan(0.0),
                () -> assertThat(ctlDay2).isLessThan(currentCtl) // Day 2 CTL < current CTL
        );
    }

    @Test
    void ctlOn_futureDate_returnsLatestCtl() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Arrange
        createActivity(Instant.now().minus(1, ChronoUnit.DAYS), 10000f, 3600, 100f);
        LocalDate futureDate = LocalDate.now().plusDays(10);
        double currentCtl = service.currentCtl(testUser);

        // Act
        double ctlFuture = service.ctlOn(testUser, futureDate);

        // Assert - future date returns latest available CTL
        assertThat(ctlFuture).isEqualTo(currentCtl);
    }

    // ==================== atlOn() - Branch Coverage ====================

    @Test
    void atlOn_noActivities_throwsInsufficientTrainingDataException() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Arrange
        LocalDate today = LocalDate.now();

        // Act & Assert
        assertThatThrownBy(() -> service.atlOn(testUser, today))
                .isInstanceOf(InsufficientTrainingDataException.class)
                .hasMessageContaining("No training history available on or before");
    }

    @Test
    void atlOn_validDate_returnsCorrectAtl() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Arrange
        Instant day1 = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant day2 = Instant.now();

        createActivity(day1, 10000f, 3600, 100f);
        createActivity(day2, 15000f, 5400, 150f);

        LocalDate date1 = day1.atZone(ZoneId.systemDefault()).toLocalDate();

        // Act
        double atlDay1 = service.atlOn(testUser, date1);
        double currentAtl = service.currentAtl(testUser);

        // Assert
        assertAll(
                () -> assertThat(atlDay1).isGreaterThan(0.0),
                () -> assertThat(currentAtl).isGreaterThan(atlDay1)
        );
    }

    // ==================== tsbOn() - Branch Coverage ====================

    @Test
    void tsbOn_noActivities_throwsInsufficientTrainingDataException() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Arrange
        LocalDate today = LocalDate.now();

        // Act & Assert
        assertThatThrownBy(() -> service.tsbOn(testUser, today))
                .isInstanceOf(InsufficientTrainingDataException.class)
                .hasMessageContaining("No training history available on or before");
    }

    @Test
    void tsbOn_firstDay_returnsZero() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Arrange
        Instant day1 = Instant.now();
        createActivity(day1, 10000f, 3600, 100f);
        LocalDate date1 = day1.atZone(ZoneId.systemDefault()).toLocalDate();

        // Act
        double tsbDay1 = service.tsbOn(testUser, date1);

        // Assert
        assertThat(tsbDay1).isEqualTo(0.0);
    }

    @Test
    void tsbOn_afterHardWorkout_isNegative() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Arrange
        Instant day1 = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant day2 = Instant.now();

        createActivity(day1, 10000f, 3600, 100f);
        createActivity(day2, 20000f, 7200, 200f);

        LocalDate date2 = day2.atZone(ZoneId.systemDefault()).toLocalDate();

        // Act
        double tsbDay2 = service.tsbOn(testUser, date2);

        // Assert - TSB should be negative after hard workout
        assertThat(tsbDay2).isLessThan(0.0);
    }

    // ==================== getCtlForLastDays() - Branch Coverage ====================

    @Test
    void getCtlForLastDays_invalidDays_throwsIllegalArgumentException() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Act & Assert
        assertAll(
                () -> assertThatThrownBy(() -> service.getCtlForLastDays(testUser, 0))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("days must be > 0"),
                () -> assertThatThrownBy(() -> service.getCtlForLastDays(testUser, -1))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("days must be > 0")
        );
    }

    @Test
    void getCtlForLastDays_noActivities_returnsEmptyList() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Act
        List<Double> ctls = service.getCtlForLastDays(testUser, 5);

        // Assert
        assertThat(ctls).isEmpty();
    }

    @Test
    void getCtlForLastDays_requestMoreThanAvailable_returnsAllAvailable() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Arrange
        for (int i = 0; i < 3; i++) {
            createActivity(Instant.now().minus(2 - i, ChronoUnit.DAYS), 10000f, 3600, 100f);
        }

        // Act
        List<Double> ctls = service.getCtlForLastDays(testUser, 10);

        // Assert
        assertThat(ctls).hasSize(3);
    }

    @Test
    void getCtlForLastDays_requestFewerThanAvailable_returnsRequestedCount() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Arrange
        for (int i = 0; i < 5; i++) {
            createActivity(Instant.now().minus(4 - i, ChronoUnit.DAYS), 10000f, 3600, 100f);
        }

        // Act
        List<Double> ctls = service.getCtlForLastDays(testUser, 2);

        // Assert
        assertThat(ctls).hasSize(2);
    }

    @Test
    void getCtlForLastDays_returnsInChronologicalOrder() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Arrange
        for (int i = 0; i < 5; i++) {
            createActivity(Instant.now().minus(4 - i, ChronoUnit.DAYS), 10000f, 3600, 100f);
        }

        // Act
        List<Double> ctls = service.getCtlForLastDays(testUser, 5);

        // Assert - values should be monotonically increasing with consistent training
        for (int i = 1; i < ctls.size(); i++) {
            assertThat(ctls.get(i)).isGreaterThanOrEqualTo(ctls.get(i - 1));
        }
    }

    // ==================== getAtlForLastDays() - Branch Coverage ====================

    @Test
    void getAtlForLastDays_invalidDays_throwsIllegalArgumentException() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Act & Assert
        assertThatThrownBy(() -> service.getAtlForLastDays(testUser, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("days must be > 0");
    }

    @Test
    void getAtlForLastDays_noActivities_returnsEmptyList() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Act
        List<Double> atls = service.getAtlForLastDays(testUser, 5);

        // Assert
        assertThat(atls).isEmpty();
    }

    @Test
    void getAtlForLastDays_validRequest_returnsCorrectList() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Arrange
        for (int i = 0; i < 3; i++) {
            createActivity(Instant.now().minus(2 - i, ChronoUnit.DAYS), 10000f, 3600, 100f);
        }

        // Act
        List<Double> atls = service.getAtlForLastDays(testUser, 2);

        // Assert
        assertAll(
                () -> assertThat(atls).hasSize(2),
                () -> assertThat(atls.get(0)).isGreaterThan(0.0),
                () -> assertThat(atls.get(1)).isGreaterThan(0.0)
        );
    }

    // ==================== getTsbForLastDays() - Branch Coverage ====================

    @Test
    void getTsbForLastDays_invalidDays_throwsIllegalArgumentException() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Act & Assert
        assertThatThrownBy(() -> service.getTsbForLastDays(testUser, -5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("days must be > 0");
    }

    @Test
    void getTsbForLastDays_noActivities_returnsEmptyList() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Act
        List<Double> tsbs = service.getTsbForLastDays(testUser, 5);

        // Assert
        assertThat(tsbs).isEmpty();
    }

    @Test
    void getTsbForLastDays_validRequest_firstDayIsZero() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Arrange
        for (int i = 0; i < 3; i++) {
            createActivity(Instant.now().minus(2 - i, ChronoUnit.DAYS), 10000f, 3600, 100f);
        }

        // Act
        List<Double> tsbs = service.getTsbForLastDays(testUser, 3);

        // Assert
        assertAll(
                () -> assertThat(tsbs).hasSize(3),
                () -> assertThat(tsbs.get(0)).isEqualTo(0.0)
        );
    }

    // ==================== getCtlHistory() - Branch Coverage ====================

    @Test
    void getCtlHistory_noActivities_returnsEmptyList() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Act
        List<Double> history = service.getCtlHistory(testUser);

        // Assert
        assertThat(history).isEmpty();
    }

    @Test
    void getCtlHistory_multipleActivities_returnsFullHistory() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Arrange
        for (int i = 0; i < 5; i++) {
            createActivity(Instant.now().minus(4 - i, ChronoUnit.DAYS), 10000f, 3600, 100f);
        }

        // Act
        List<Double> history = service.getCtlHistory(testUser);

        // Assert
        assertAll(
                () -> assertThat(history).hasSize(5),
                () -> assertThat(history.get(0)).isGreaterThan(0.0)
        );
    }

    // ==================== getAtlHistory() - Branch Coverage ====================

    @Test
    void getAtlHistory_noActivities_returnsEmptyList() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Act
        List<Double> history = service.getAtlHistory(testUser);

        // Assert
        assertThat(history).isEmpty();
    }

    @Test
    void getAtlHistory_multipleActivities_returnsFullHistory() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Arrange
        for (int i = 0; i < 3; i++) {
            createActivity(Instant.now().minus(2 - i, ChronoUnit.DAYS), 10000f, 3600, 100f);
        }

        // Act
        List<Double> history = service.getAtlHistory(testUser);

        // Assert
        assertThat(history).hasSize(3);
    }

    // ==================== getTsbHistory() - Branch Coverage ====================

    @Test
    void getTsbHistory_noActivities_returnsEmptyList() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Act
        List<Double> history = service.getTsbHistory(testUser);

        // Assert
        assertThat(history).isEmpty();
    }

    @Test
    void getTsbHistory_multipleActivities_firstDayIsZero() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Arrange
        for (int i = 0; i < 3; i++) {
            createActivity(Instant.now().minus(2 - i, ChronoUnit.DAYS), 10000f, 3600, 100f);
        }

        // Act
        List<Double> history = service.getTsbHistory(testUser);

        // Assert
        assertAll(
                () -> assertThat(history).hasSize(3),
                () -> assertThat(history.get(0)).isEqualTo(0.0)
        );
    }

    // ==================== Edge Cases ====================

    @Test
    void multipleActivitiesSameDay_aggregatesCorrectly() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Arrange - two activities on same day
        Instant morning = Instant.now().truncatedTo(ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS);
        Instant evening = Instant.now().truncatedTo(ChronoUnit.DAYS).plus(18, ChronoUnit.HOURS);

        createActivity(morning, 10000f, 3600, 100f);
        createActivity(evening, 8000f, 3000, 80f);

        // Act
        List<Double> history = service.getCtlHistory(testUser);

        // Assert - should have only 1 entry for that day
        assertThat(history).hasSize(1);
    }

    @Test
    void activitiesOnNonConsecutiveDays_handlesGapsCorrectly() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Arrange - activities on day 1, day 4, day 7 (gaps in between)
        Instant day1 = Instant.now().minus(6, ChronoUnit.DAYS);
        Instant day4 = Instant.now().minus(3, ChronoUnit.DAYS);
        Instant day7 = Instant.now();

        createActivity(day1, 10000f, 3600, 100f);
        createActivity(day4, 10000f, 3600, 100f);
        createActivity(day7, 10000f, 3600, 100f);

        // Act
        List<Double> history = service.getCtlHistory(testUser);

        // Assert - should only have 3 entries (gaps not included as zero-load days)
        assertThat(history).hasSize(7);
    }

    @Test
    void zeroSessionLoad_NotExcludedFromCalculation() {
        testUser = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        activityRepository.deleteAll();
        // Arrange
        Activity activity = new Activity();
        activity.setUser(testUser);
        activity.setType("Run");
        activity.setStartDate(Instant.now());
        activity.setDistance(0f);
        activity.setMovingTime(0);
        activity.setTotalElevationGain(0f);
        activity.setSessionLoad(0); // Zero session load
        activityRepository.save(activity);
        activityRepository.flush();

        // Act
        List<Double> history = service.getCtlHistory(testUser);

        assertThat(history.size()).isEqualTo(1);
    }

    // ==================== Helper Methods ====================

    private Activity createActivity(Instant startDate, float distance, int movingTime, float elevation) {
        Activity activity = new Activity();
        activity.setUser(testUser);
        activity.setType("Run");
        activity.setStartDate(startDate);
        activity.setDistance(distance);
        activity.setMovingTime(movingTime);
        activity.setTotalElevationGain(elevation);

        Integer sessionLoad = fitnessScoreService.calculateSessionLoad(distance, movingTime, elevation);
        activity.setSessionLoad(sessionLoad);

        return activityRepository.save(activity);
    }

    private double calculateExpectedScore(Activity activity) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate activityDate = activity.getStartDate().atZone(zone).toLocalDate();
        Instant startOfDay = activityDate.atStartOfDay(zone).toInstant();
        return fitnessScoreService.calculateFitnessScore(startOfDay, testUser);
    }


}