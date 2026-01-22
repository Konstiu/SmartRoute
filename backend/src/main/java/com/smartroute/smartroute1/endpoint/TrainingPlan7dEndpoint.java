package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlan7dDto;
import com.smartroute.smartroute1.service.TrainingPlan7dService;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/v1/training-plan-7-days")
@RequiredArgsConstructor
public class TrainingPlan7dEndpoint {
    private final TrainingPlan7dService trainingPlan7dService;
    private static final Logger log = LoggerFactory.getLogger(TrainingPlan7dEndpoint.class);

    @GetMapping("/next-7-days")
    @Secured("ROLE_USER")
    public TrainingPlan7dDto next7Days(
            @RequestParam("latitude") double latitude,
            @RequestParam("longitude") double longitude,
            @RequestParam(name = "debug", defaultValue = "false") boolean debug,
            @RequestParam(name = "sims", required = false) Integer sims,
            @RequestParam(name = "seed", required = false) Long seed,

            @RequestParam(name = "regen", defaultValue = "false") boolean regen,
            // DEV-ONLY overrides (optional)
            @RequestParam(name = "historyDays", required = false) Integer historyDays,
            @RequestParam(name = "historyMean", required = false) Double historyMean,
            @RequestParam(name = "historyStd", required = false) Double historyStd,
            @RequestParam(name = "ctl", required = false) Double ctl,
            @RequestParam(name = "atl", required = false) Double atl,
            @RequestParam(name = "readiness", required = false) Integer readiness,
            @RequestParam(name = "injuryIndex", required = false) Double injuryIndex
    ) {
        final String email = SecurityContextHolder.getContext().getAuthentication().getName();

        TrainingPlan7dService.DevOverrides overrides = null;

        // Only allow overrides if enabled + debug=true
        if (debug) {
            overrides = new TrainingPlan7dService.DevOverrides(
                    historyDays, historyMean, historyStd,
                    ctl, atl,
                    readiness, injuryIndex
            );
        }

        log.info("next7Days: lat={}, lng={}, debug={}, sims={}, seed={}", latitude, longitude, debug, sims, seed);
        log.info("next7Days overrides: {}", overrides);

        return trainingPlan7dService.buildNext7Days(email, latitude, longitude, debug, sims, seed, overrides, regen);
    }
}

