package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.RecommendedActivityDto;
import com.smartroute.smartroute1.service.TrainingPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/training-plan")
@Tag(
        name = "Training Plan",
        description = ""
)
public class TrainingPlanEndpoint {

    private final TrainingPlanService trainingPlanService;

    @GetMapping()
    @Secured("ROLE_USER")
    @Operation(
            summary = "",
            description = ""
    )
    public RecommendedActivityDto getTrainingPlan(@RequestParam("lat") double latitude, @RequestParam("long") double longitude) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        return trainingPlanService.getTrainingPlan(email, latitude, longitude);
    }
}
