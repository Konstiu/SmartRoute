package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Injuries;
import com.smartroute.smartroute1.service.ActivityProcessingService;
import com.smartroute.smartroute1.service.FatigueAndOverloadService;
import com.smartroute.smartroute1.service.InjuryAwareTrainingService;
import com.smartroute.smartroute1.service.impl.CalculateReadinessScoreImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import jakarta.transaction.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles({"test", "generateData"})
@Transactional
class ReadinessScoreServiceTest {

    @MockitoBean
    private FatigueAndOverloadService fatigueSvc;

    @MockitoBean
    private InjuryAwareTrainingService injurySvc;

    @MockitoBean
    private ActivityProcessingService activitySvc;

    @Autowired
    private CalculateReadinessScoreImpl service;

    private ApplicationUser user(String email) {
        ApplicationUser u = new ApplicationUser();
        u.setEmail(email);
        u.setVerified(true);
        return u;
    }

    private Activity activityWithSatisfaction(int score) {
        Activity a = new Activity();
        a.setSatisfactionScore(score);
        return a;
    }

    @Test
    void calculateReadinessScore_withInjuryAndSatisfaction_allComponentsWeighted() {
        ApplicationUser u = user("a@b.com");
        LocalDate d = LocalDate.now();

        when(fatigueSvc.ctlOn(u, d)).thenReturn(80.0); // around C_0
        when(fatigueSvc.tsbOn(u, d)).thenReturn(10.0); // moderate fatigue

        Injuries inj = new Injuries();
        inj.setInjuryIndex(0.2);
        when(injurySvc.getInjuryIndex(u.getEmail())).thenReturn(0.2);

        when(activitySvc.getLastActivityBeforeDate(u.getEmail(), d))
                .thenReturn(Optional.of(activityWithSatisfaction(4)));

        int score = service.calculateReadinessScore(u, d);

        assertEquals(73, score);
    }

    @Test
    void calculateReadinessScore_noInjury_weightRedistributedFromInjury() {
        ApplicationUser u = user("noinjury@x.com");
        LocalDate d = LocalDate.now();

        when(fatigueSvc.ctlOn(u, d)).thenReturn(80.0);
        when(fatigueSvc.tsbOn(u, d)).thenReturn(0.0);
        when(injurySvc.getInjuryIndex(u.getEmail())).thenReturn(0.0);
        when(activitySvc.getLastActivityBeforeDate(u.getEmail(), d))
                .thenReturn(Optional.of(activityWithSatisfaction(3)));

        int score = service.calculateReadinessScore(u, d);
        assertEquals(57, score);
    }

    @Test
    void calculateReadinessScore_noSatisfaction_weightRedistributedFromSatisfaction() {
        ApplicationUser u = user("nosat@x.com");
        LocalDate d = LocalDate.now();

        when(fatigueSvc.ctlOn(u, d)).thenReturn(100.0);
        when(fatigueSvc.tsbOn(u, d)).thenReturn(5.0);
        when(injurySvc.findInjuriesByEmail(u.getEmail())).thenReturn(List.of(new Injuries()));
        when(activitySvc.getLastActivityBeforeDate(u.getEmail(), d))
                .thenReturn(Optional.empty());

        int score = service.calculateReadinessScore(u, d);
        assertEquals(81, score);
    }

    @Test
    void calculateReadinessScore_extremeFitnessHighFatigueLow_increasesScore() {
        ApplicationUser u = user("fit@x.com");
        LocalDate d = LocalDate.now();

        when(fatigueSvc.ctlOn(u, d)).thenReturn(200.0); // high CTL
        when(fatigueSvc.tsbOn(u, d)).thenReturn(10.0); // high TSB (freshness)
        when(injurySvc.findInjuriesByEmail(u.getEmail())).thenReturn(List.of());
        when(activitySvc.getLastActivityBeforeDate(u.getEmail(), d))
                .thenReturn(Optional.of(activityWithSatisfaction(5)));

        int score = service.calculateReadinessScore(u, d);
        assertEquals(98, score);
    }

    @Test
    void calculateReadinessScore_extremeFatigueHighFitnessLow_reducesScore() {
        ApplicationUser u = user("fatigue@x.com");
        LocalDate d = LocalDate.now();

        when(fatigueSvc.ctlOn(u, d)).thenReturn(20.0); // low CTL
        when(fatigueSvc.tsbOn(u, d)).thenReturn(30.0); // high fatigue
        when(injurySvc.getInjuryIndex(u.getEmail())).thenReturn(0.0);
        when(activitySvc.getLastActivityBeforeDate(u.getEmail(), d))
                .thenReturn(Optional.of(activityWithSatisfaction(2)));

        int score = service.calculateReadinessScore(u, d);
        assertEquals(59, score);
    }

    @Test
    void calculateReadinessScore_injuryIndexBounds_affectScoreMonotonically() {
        ApplicationUser u = user("injury@x.com");
        LocalDate d = LocalDate.now();

        Injuries healthy = new Injuries();
        healthy.setInjuryIndex(0.0);
        Injuries severe = new Injuries();
        severe.setInjuryIndex(1.0);

        when(fatigueSvc.ctlOn(u, d)).thenReturn(80.0);
        when(fatigueSvc.tsbOn(u, d)).thenReturn(5.0);
        when(activitySvc.getLastActivityBeforeDate(u.getEmail(), d))
                .thenReturn(Optional.of(activityWithSatisfaction(3)));

        when(injurySvc.getInjuryIndex(u.getEmail())).thenReturn(0.0);
        int scoreHealthy = service.calculateReadinessScore(u, d);

        when(injurySvc.getInjuryIndex(u.getEmail())).thenReturn(1.0);
        int scoreSevere = service.calculateReadinessScore(u, d);

        assertAll(
            () -> assertTrue(scoreHealthy >= scoreSevere, "Healthy score should be >= injured score"),
            () -> assertEquals(69, scoreHealthy),
            () -> assertEquals(54, scoreSevere)
        );

    }

    @Test
    void calculateReadinessScore_usesSmallestInjuryIndexWhenMultiple() {
        ApplicationUser u = user("multiinjury@x.com");
        LocalDate d = LocalDate.now();

        Injuries inj1 = new Injuries(); inj1.setInjuryIndex(0.6);
        Injuries inj2 = new Injuries(); inj2.setInjuryIndex(0.3);
        Injuries inj3 = new Injuries(); inj3.setInjuryIndex(0.8);

        when(fatigueSvc.ctlOn(u, d)).thenReturn(90.0);
        when(fatigueSvc.tsbOn(u, d)).thenReturn(2.0);
        when(activitySvc.getLastActivityBeforeDate(u.getEmail(), d))
                .thenReturn(Optional.of(activityWithSatisfaction(4)));

        when(injurySvc.getInjuryIndex(u.getEmail())).thenReturn(0.6);
        int scoreHigh = service.calculateReadinessScore(u, d);

        when(injurySvc.getInjuryIndex(u.getEmail())).thenReturn(0.3);
        int scoreSmallest = service.calculateReadinessScore(u, d);

        assertAll(
            () -> assertTrue(scoreSmallest >= scoreHigh, "Score with smallest injury index should be >= single injury score"),
            () -> assertEquals(59, scoreHigh),
            () -> assertEquals(62, scoreSmallest)
        );
    }

    // die redistribute-tests nutzen nun das autowirende service

    @Test
    void redistribute_evenlyDistributesRemovedValue() {
        double[] weights = new double[]{0.25, 0.35, 0.15, 0.25};

        service.redistribute(weights, 2);

        assertAll(
            () -> assertEquals(0.30, weights[0], 1e-8),
            () -> assertEquals(0.40, weights[1], 1e-8),
            () -> assertEquals(0.00, weights[2], 1e-8),
            () -> assertEquals(0.30, weights[3], 1e-8)
        );

        // Sum still 1.0
        double sum = weights[0] + weights[1] + weights[2] + weights[3];
        assertEquals(1.0, sum, 1e-8);
    }

    @Test
    void redistribute_skipsZeroRecipients() {
        double[] weights = new double[]{0.25, 0.0, 0.15, 0.60};

        service.redistribute(weights, 2);

        // removed 0.15 distributed among index 0 and 3 -> +0.075 each
        assertAll(
            () -> assertEquals(0.325, weights[0], 1e-8),
            () -> assertEquals(0.0, weights[1], 1e-8),
            () -> assertEquals(0.0, weights[2], 1e-8),
            () -> assertEquals(0.675, weights[3], 1e-8)
        );

        double sum = weights[0] + weights[1] + weights[2] + weights[3];
        assertEquals(1.0, sum, 1e-8);
    }

    @Test
    void redistribute_noRecipients_leavesZeros() {
        double[] weights = new double[]{0.0, 0.0, 0.5, 0.0};

        service.redistribute(weights, 2);

        assertAll(
            () -> assertEquals(0.0, weights[0], 1e-8),
            () -> assertEquals(0.0, weights[1], 1e-8),
            () -> assertEquals(0.0, weights[2], 1e-8),
            () -> assertEquals(0.0, weights[3], 1e-8)
        );
    }

    @Test
    void redistribute_nullArray_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.redistribute(null, 0));
    }

    @Test
    void redistribute_indexOutOfBounds_throws() {
        double[] weights = new double[]{0.25, 0.25, 0.25, 0.25};
        assertThrows(IllegalArgumentException.class, () -> service.redistribute(weights, -1));
        assertThrows(IllegalArgumentException.class, () -> service.redistribute(weights, 4));
    }
}
