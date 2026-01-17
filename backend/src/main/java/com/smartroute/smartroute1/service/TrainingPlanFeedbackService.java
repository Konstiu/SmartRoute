package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.SubmitTrainingPlanFeedbackDto;

public interface TrainingPlanFeedbackService {

    /**
     * Submits user feedback.
     *
     * @param email email of a user.
     *
     * @param dto the feedback of the user.
     *
     * @param weatherScore optional weather.
     *
     * @param readiness optional readiness.
     *
     * @param injuryIndex optional injury.
     */
    void submit(String email, SubmitTrainingPlanFeedbackDto dto, Double weatherScore, Integer readiness, Double injuryIndex);
}
