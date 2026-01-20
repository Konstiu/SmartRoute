package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.DetailedActivityDto;
import com.smartroute.smartroute1.endpoint.dto.ActivityDto;
import com.smartroute.smartroute1.endpoint.dto.RunClassificationDecisionDto;
import com.smartroute.smartroute1.endpoint.mapper.RunClassificationMapper;
import com.smartroute.smartroute1.endpoint.dto.SyncCountDto;
import com.smartroute.smartroute1.endpoint.dto.SyncStatusDto;
import com.smartroute.smartroute1.endpoint.dto.SyncStatusStoreEntryDto;
import com.smartroute.smartroute1.endpoint.mapper.StravaActivityMapper;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.RunClassificationDecision;
import com.smartroute.smartroute1.entity.enums.RunType;
import com.smartroute.smartroute1.service.ActivityProcessingService;
import com.smartroute.smartroute1.service.ActivityService;
import com.smartroute.smartroute1.service.RunClassificationService;
import com.smartroute.smartroute1.service.ActivitySyncServiceOrch;
import com.smartroute.smartroute1.service.SyncStatusStore;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.server.ResponseStatusException;


import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/activities")
public class ViewActivitiesEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final StravaActivityMapper activityMapper;
    private final ActivityProcessingService activityProcessingService;
    private final ActivitySyncServiceOrch activityServiceOrch;
    private final SyncStatusStore syncStatusStore;
    private final ActivityService activityService;
    private final RunClassificationService runClassificationService;
    private final RunClassificationMapper runClassificationMapper;

    @GetMapping()
    @ResponseStatus(HttpStatus.OK)
    @Secured("ROLE_USER")
    @Operation(
            summary = "Get all activities of the authenticated user",
            description = "Returns a list of summarized activities for the currently logged-in user. "
                    + "Each entry contains basic information such as name, distance, duration and timestamp."
    )
    public List<ActivityDto> getActivities() {
        LOGGER.info("GET /api/v1/activities/");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<Activity> list = activityProcessingService.getActivities(auth.getName());
        List<ActivityDto> dtos = new ArrayList<>();
        for (Activity stravaActivity : list) {
            RunClassificationDecision decision = stravaActivity.getRunTypeClassification();
            RunClassificationDecisionDto runClassification;
            if (stravaActivity.getSportType().equals("Run") && decision == null) {
                runClassification = runClassificationService.classifyRun(stravaActivity.getId());
            } else {
                runClassification = runClassificationMapper.entityToDto(decision);
            }
            dtos.add(activityMapper.toViewDto(stravaActivity, runClassification));
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
    public DetailedActivityDto getOneActivity(@PathVariable("id") long id) {
        LOGGER.info("GET /api/v1/activities/{}", id);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Activity activity = activityProcessingService.getActivity(auth.getName(), id);

        RunClassificationDecision decision = activity.getRunTypeClassification();
        RunClassificationDecisionDto runClassification;
        if (activity.getSportType().equals("Run") && decision == null) {
            runClassification = runClassificationService.classifyRun(activity.getId());
        } else {
            runClassification = runClassificationMapper.entityToDto(decision);
        }
        return activityMapper.toDetailedViewDto(activity, runClassification);
    }

    @PostMapping("sync")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Secured("ROLE_USER")
    @Operation(
            summary = "Synchronize activities from connected services",
            description = """
                    Triggers a synchronization with all connected activity providers (Strava, Garmin).
                    The backend fetches the latest <count> activities from each platform and merges
                    them into the database.
                    
                    This endpoint does not return data — it only initiates a sync process.
                    Use GET /api/v1/activities to fetch updated activities afterwards.
                    """
    )
    public void synchronize(@RequestBody SyncCountDto request, @RequestHeader("X-Request-Id") UUID requestId) throws Exception {
        LOGGER.info("POST /api/v1/activities/sync/");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        activityServiceOrch.synchronize(email, request.getCount(), requestId);
    }

    @GetMapping("/sync/status/{requestId}")
    @ResponseStatus(HttpStatus.OK)
    @Secured("ROLE_USER")
    public SyncStatusDto getSyncStatus(@PathVariable("requestId") UUID requestId) {
        LOGGER.info("GET /api/v1/activities/sync/status/{}", requestId);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        SyncStatusStoreEntryDto entry = syncStatusStore.get(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unknown or expired requestId"
                ));

        if (!email.equals(entry.getEmail())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "requestId does not belong to current user"
            );
        }

        LOGGER.trace("GET /api/v1/activities/sync/status/{} (user={}, state={})",
                requestId, email, entry.getState());

        SyncStatusDto s = new SyncStatusDto();
        s.setMessage(entry.getMessage());
        s.setState(entry.getState());
        return s;
    }

    @GetMapping("classification/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Secured("ROLE_USER")
    @Operation(
            summary = "Returns the run type classification",
            description = """
                    Returns the run type classification and the probabilities for each run type.
                    """
    )
    public RunClassificationDecisionDto getClassification(@PathVariable("id") Long id) {
        LOGGER.info("GET /api/v1/strava/classification/{}", id);
        return runClassificationService.classifyRun(id);
    }

    @PostMapping("classification/correction/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Secured("ROLE_USER")
    @Operation(
            summary = "Updates the RuntypeClassification",
            description = """
                    Corrects the classification of a run and updates the correction map for the user.
                    """
    )
    public void correctClassification(@PathVariable("id") Long id, @RequestBody RunType runType) {
        LOGGER.info("POST /api/v1/strava/classification/correct/{}", id);
        runClassificationService.correctRun(id, runType);
    }

}
