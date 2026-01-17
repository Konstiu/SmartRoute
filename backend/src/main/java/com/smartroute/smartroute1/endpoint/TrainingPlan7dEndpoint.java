package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.TrainingPlan7dDto;
import com.smartroute.smartroute1.service.TrainingPlan7dService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/training-plan")
@RequiredArgsConstructor
public class TrainingPlan7dEndpoint {
    private final TrainingPlan7dService trainingPlan7dService;

    @GetMapping("/next-7-days")
    public TrainingPlan7dDto next7Days(@RequestParam("email") String email,
                                       @RequestParam("latitude") double latitude,
                                       @RequestParam("longitude") double longitude,
                                       @RequestParam(name = "debug", defaultValue = "false") boolean debug,
                                       @RequestParam(name = "sims", required = false) Integer sims,
                                       @RequestParam(name = "seed", required = false) Long seed) {
        return trainingPlan7dService.buildNext7Days(email, latitude, longitude, debug, sims, seed);
    }
}
