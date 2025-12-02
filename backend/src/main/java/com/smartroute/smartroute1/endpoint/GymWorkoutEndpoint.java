package com.smartroute.smartroute1.endpoint;


import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.GymWorkout;
import com.smartroute.smartroute1.entity.enums.BodyPart;
import com.smartroute.smartroute1.service.GymWorkoutSelectorService;
import com.smartroute.smartroute1.service.InjuryAwareTrainingService;
import com.smartroute.smartroute1.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/v1/gym")
@RequiredArgsConstructor
public class GymWorkoutEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final GymWorkoutSelectorService gymWorkoutSelectorService;
    private final UserService userService;
    private final InjuryAwareTrainingService injuryAwareTrainingService;


    @GetMapping()
    @ResponseStatus(HttpStatus.OK)
    @Secured("ROLE_USER")
    @Operation(
            summary = "Get all gym workouts of the authenticated user",
            description = "Returns a list of gym workouts for the currently logged-in user. "
    )
    public List<GymWorkout> getGymWorkouts() {
        LOGGER.info("/api/v1/gym");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        return gymWorkoutSelectorService.getAllGymWorkouts(email);
    }

    @GetMapping("/generate")
    @ResponseStatus(HttpStatus.OK)
    @Secured("ROLE_USER")
    @Operation(
            summary = "Create a new gym workout for the authenticated user",
            description = "Returns the new gym workout for the currently logged-in user. "
    )
    public GymWorkout getGymWorkout() {
        LOGGER.info("/api/v1/gym/generate");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        Map<BodyPart, Double> injuryMap = injuryAwareTrainingService
                .calculateInjuriesMap(injuryAwareTrainingService
                        .findInjuriesByEmail(email));
        ApplicationUser user = userService.findApplicationUserByEmail(email);
        //TODO ADD ACTUAL READINESS SCORE WHEN AVAILABLE
        return gymWorkoutSelectorService.getGymWorkout(user, injuryMap, 100);
    }


}
