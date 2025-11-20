package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.DetailedActivityDto;
import com.smartroute.smartroute1.endpoint.dto.ActivityDto;
import com.smartroute.smartroute1.endpoint.mapper.StravaActivityMapper;
import com.smartroute.smartroute1.entity.StravaActivity;
import com.smartroute.smartroute1.service.StravaService;
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
import org.springframework.web.bind.annotation.PathVariable;


import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/activities")
public class ViewActivitiesEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final  StravaActivityMapper activityMapper;
    private final StravaService stravaService;


    @GetMapping()
    @ResponseStatus(HttpStatus.OK)
    @Secured("ROLE_USER")
    @Operation(
            summary = "Get all activities of the authenticated user",
            description = "Returns a list of summarized activities for the currently logged-in user. "
                    + "Each entry contains basic information such as name, distance, duration and timestamp."
    )
    public List<ActivityDto> getStravaActivities() {
        LOGGER.info("GET /api/v1/activities/");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<StravaActivity> list = stravaService.getStravaActivities(auth.getName());
        List<ActivityDto> dtos = new ArrayList<>();
        for (StravaActivity stravaActivity : list) {
            dtos.add(activityMapper.toViewDto(stravaActivity));
        }
        return dtos;
    }

    @GetMapping("{id}")
    @ResponseStatus(HttpStatus.OK)
    @Secured("ROLE_USER")
    @Operation(
            summary = "Get detailed information about a specific activity",
            description = "Returns a detailed view of a single activity, including metrics such as "
                    + "heart rate, power, elevation, GPS track and additional metadata. "
                    + "The activity must belong to the authenticated user."
    )
    public DetailedActivityDto getOneStravaActivity(@PathVariable("id") long id) {
        LOGGER.info("GET /api/v1/activities/{}", id);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        StravaActivity activity = stravaService.getStravaActivity(auth.getName(), id);
        return activityMapper.toDetailedViewDto(activity);
    }
}
