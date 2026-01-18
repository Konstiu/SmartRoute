package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlanFeedbackRequestDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.TrainingPlanFeedback;
import com.smartroute.smartroute1.entity.TrainingPlanUserProfile;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.repository.TrainingPlanFeedbackRepository;
import com.smartroute.smartroute1.repository.TrainingPlanUserProfileRepository;
import com.smartroute.smartroute1.service.TrainingPlanFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TrainingPlanFeedbackServiceImpl implements TrainingPlanFeedbackService {

    private final UserRepository userRepository;
    private final TrainingPlanFeedbackRepository feedbackRepo;
    private final TrainingPlanUserProfileRepository profileRepo;

    // tuning knobs (keep simple first)
    private static final double ETA_BIAS = 0.15;      // learning rate for preferences
    private static final double BIAS_CLAMP = 2.0;     // keep bounded
    private static final int ADHERENCE_WINDOW_DAYS = 28;

    @Override
    public void recordFeedback(String email, TrainingPlanFeedbackRequestDto dto) {
        ApplicationUser user = userRepository.findUserByEmail(email);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        TrainingPlanFeedback fb = TrainingPlanFeedback.builder()
                .user(user)
                .date(dto.getDate())
                .plannedWorkout(dto.getPlannedWorkout())
                .userChosenWorkout(dto.getUserChosenWorkout())
                .completed(dto.isCompleted())
                .satisfactionScore(dto.getSatisfactionScore())
                .perceivedEffort(dto.getPerceivedEffort())
                .createdAt(Instant.now())
                .build();

        feedbackRepo.save(fb);

        TrainingPlanUserProfile profile = profileRepo.findById(user.getId())
                .orElseGet(() -> newProfile(user));

        updateTypeBias(profile, fb);
        updateUncertaintyFromAdherence(profile, user);

        profile.setUpdatedAt(Instant.now());
        profileRepo.save(profile);
    }

    private TrainingPlanUserProfile newProfile(ApplicationUser user) {
        Map<WorkoutType, Double> bias = new EnumMap<>(WorkoutType.class);
        Map<WorkoutType, Double> mult = new EnumMap<>(WorkoutType.class);

        for (WorkoutType wt : WorkoutType.values()) {
            bias.put(wt, 0.0);
            mult.put(wt, 1.0);
        }

        return TrainingPlanUserProfile.builder()
                .user(user)
                .typeBias(bias)
                .loadMultiplier(mult)
                .uncertaintyScale(1.0)
                .updatedAt(Instant.now())
                .build();
    }

    private void updateTypeBias(TrainingPlanUserProfile profile, TrainingPlanFeedback fb) {
        WorkoutType planned = fb.getPlannedWorkout();
        WorkoutType chosen = fb.getUserChosenWorkout();

        // If user didn’t provide a choice, just use completion signal (handled elsewhere)
        if (chosen == null || planned == null) {
            return;
        }

        // Only learn if they differ (this is the “correction”)
        if (chosen != planned) {
            bump(profile.getTypeBias(), chosen, +ETA_BIAS);
            bump(profile.getTypeBias(), planned, -ETA_BIAS);
        } else {
            // small reinforcement when they accept it
            bump(profile.getTypeBias(), planned, +ETA_BIAS * 0.25);
        }
    }

    private void bump(Map<WorkoutType, Double> m, WorkoutType wt, double delta) {
        double v = m.getOrDefault(wt, 0.0) + delta;
        v = Math.max(-BIAS_CLAMP, Math.min(BIAS_CLAMP, v));
        m.put(wt, v);
    }

    private void updateUncertaintyFromAdherence(TrainingPlanUserProfile profile, ApplicationUser user) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(ADHERENCE_WINDOW_DAYS);

        List<TrainingPlanFeedback> events = feedbackRepo.findByUserAndDateBetween(user, from, today);
        if (events.isEmpty()) {
            profile.setUncertaintyScale(1.0);
            return;
        }

        long plannedCount = events.size();
        long completedCount = events.stream().filter(TrainingPlanFeedback::isCompleted).count();

        double adherence = (double) completedCount / (double) plannedCount; // 0..1

        // map adherence -> uncertainty scale
        // 100% adherence => 1.0
        // 0% adherence   => 1.6
        double scale = 1.0 + (1.0 - adherence) * 0.6;
        profile.setUncertaintyScale(scale);
    }
}
