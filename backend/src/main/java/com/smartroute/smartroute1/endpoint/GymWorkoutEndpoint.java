package com.smartroute.smartroute1.endpoint;


import com.smartroute.smartroute1.endpoint.dto.GymWorkoutDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.BodyPart;
import com.smartroute.smartroute1.service.GymWorkoutSelectorService;
import com.smartroute.smartroute1.service.InjuryAwareTrainingService;
import com.smartroute.smartroute1.service.ReadinessScoreService;
import com.smartroute.smartroute1.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


@RestController
@RequestMapping("/api/v1/gym")
@RequiredArgsConstructor
public class GymWorkoutEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final GymWorkoutSelectorService gymWorkoutSelectorService;
    private final UserService userService;
    private final InjuryAwareTrainingService injuryAwareTrainingService;
    private final ReadinessScoreService readinessScoreService;


    @GetMapping()
    @ResponseStatus(HttpStatus.OK)
    @Secured("ROLE_USER")
    @Transactional
    @Operation(
            summary = "Get all gym workouts of the authenticated user",
            description = "Returns a list of gym workouts for the currently logged-in user. "
    )
    public List<GymWorkoutDto> getGymWorkouts() {
        LOGGER.info("GET /api/v1/gym");

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
    public GymWorkoutDto getGymWorkout() {
        LOGGER.info("GET /api/v1/gym/generate");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        Map<BodyPart, Double> injuryMap = injuryAwareTrainingService
                .calculateInjuriesMap(injuryAwareTrainingService
                        .findInjuriesByEmail(email));
        ApplicationUser user = userService.findApplicationUserByEmail(email);
        return gymWorkoutSelectorService.getGymWorkout(user, injuryMap, readinessScoreService.calculateReadinessScore(user, LocalDate.now()));
    }

    @GetMapping("/get/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Secured("ROLE_USER")
    @Transactional
    @Operation(
            summary = "Get a gym workout by their id"
    )
    public GymWorkoutDto getGymWorkoutById(@PathVariable("id") Long id) {
        LOGGER.info("GET /api/v1/gym/get/{}", id);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return gymWorkoutSelectorService.getGymWorkoutById(id, authentication.getName());
    }


}
