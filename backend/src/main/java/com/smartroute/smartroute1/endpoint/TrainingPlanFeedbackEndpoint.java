package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlanFeedbackRequestDto;
import com.smartroute.smartroute1.service.TrainingPlanFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/training-plan")
@RequiredArgsConstructor
public class TrainingPlanFeedbackEndpoint {

    private final TrainingPlanFeedbackService feedbackService;

    @PostMapping("/next-7-days/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submitFeedback(@RequestParam("email") String email, @RequestBody TrainingPlanFeedbackRequestDto dto) {
        feedbackService.recordFeedback(email, dto);
    }
}
