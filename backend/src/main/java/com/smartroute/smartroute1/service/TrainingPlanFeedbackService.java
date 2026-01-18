package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlanFeedbackRequestDto;

public interface TrainingPlanFeedbackService {

    /**
     * Submits user feedback.
     *
     * @param email email of a user.
     *
     * @param dto the feedback of the user.
     *
     */
    void recordFeedback(String email, TrainingPlanFeedbackRequestDto dto);
}
