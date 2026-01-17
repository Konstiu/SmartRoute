package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.SubmitTrainingPlanFeedbackDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.TrainingPlanFeedback;
import com.smartroute.smartroute1.repository.TrainingPlanFeedbackRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.TrainingPlanFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TrainingPlanFeedbackServiceImpl implements TrainingPlanFeedbackService {

    private final UserRepository userRepository;
    private final TrainingPlanFeedbackRepository repo;

    @Override
    public void submit(String email, SubmitTrainingPlanFeedbackDto dto,
                       Double weatherScore, Integer readiness, Double injuryIndex) {

        ApplicationUser user = userRepository.findUserByEmail(email);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        TrainingPlanFeedback fb = repo.findByUserAndPlannedDate(user, dto.getPlannedDate()).orElseGet(TrainingPlanFeedback::new);

        fb.setUser(user);
        fb.setPlannedDate(dto.getPlannedDate());
        fb.setRecommendedWorkoutType(dto.getRecommendedWorkoutType());
        fb.setUserPreferredWorkoutType(dto.getUserPreferredWorkoutType());
        fb.setDidFollow(dto.getDidFollow());
        fb.setReason(dto.getReason());
        fb.setComment(dto.getComment());

        // optional metadata snapshot
        fb.setWeatherScore(weatherScore);
        fb.setReadinessScore(readiness);
        fb.setInjuryIndex(injuryIndex);

        repo.save(fb);
    }
}
