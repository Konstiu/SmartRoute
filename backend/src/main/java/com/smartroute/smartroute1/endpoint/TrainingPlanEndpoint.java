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

import java.time.LocalDate;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/training-plan")
@Tag(
        name = "Training Plan",
        description = """
                Provides personalized training recommendations based on the athlete's current training history, injuries,
                and local weather conditions.
                """
)
public class TrainingPlanEndpoint {

    private final TrainingPlanService trainingPlanService;

    @GetMapping()
    @Secured("ROLE_USER")
    @Operation(
            summary = "Get a recommended activity for the logged-in user",
            description = """
                    Generates a personalized training recommendation for the authenticated user.
                    The recommendation considers the user's current fitness and health, preferred training days and
                    the current weather conditions at the provided location.
                    """
    )
    public RecommendedActivityDto getTrainingPlan(@RequestParam("lat") double latitude, @RequestParam("long") double longitude) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        return trainingPlanService.getTrainingPlan(email, latitude, longitude);
    }

    @GetMapping("/get-predicted-day")
    @Secured("ROLE_USER")
    @Operation(
    )
    public RecommendedActivityDto getPlannedDay(@RequestParam("planId") String planId, @RequestParam("date") LocalDate date) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        return trainingPlanService.getPlannedDay(email, planId, date);
    }
}
