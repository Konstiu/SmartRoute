package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.StravaStreamDto;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.service.ActivityProcessingService;
import com.smartroute.smartroute1.service.FitnessScoreService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityProcessingServiceImpl implements ActivityProcessingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActivityProcessingServiceImpl.class);

    private final WebClient webClient;
    private final FitnessScoreService fitnessScoreService;
    private final ActivityRepository activityRepository;
    private final TaskScheduler taskScheduler;

    public void fetchHeartRateDataForActivities(int maxBatchSize, List<Activity> activities, String token) {
        LOGGER.trace("fetchHeartRateDataForActivitiesAsync({},{},*token*)", maxBatchSize, activities);

        // Lists of activities with and without sufferScore are separated to immediately calculate fitnessScore for
        // all activities with the necessary data available.

        // Find running activities with strava sufferScore and missing sessionLoad and calculate sessionLoad
        List<Activity> activitiesWithStravaSufferScore = activities.stream()
                .filter(a -> a.getType().equals("Run") && a.getSufferScore() != null && a.getSessionLoad() == null)
                .toList();
        activitiesWithStravaSufferScore.forEach(a ->
                processActivity(a, token)
        );

        // Find running activities without Strava sufferScore and missing sessionLoad, fetch heartrate stream if available
        // and calculate sessionLoad
        List<Activity> activitiesMissingSessionLoad = activities.stream()
                .filter(a -> a.getType().equals("Run") && a.getSessionLoad() == null && a.getSufferScore() == null)
                .sorted((a, b) -> b.getStartDate().compareTo(a.getStartDate()))
                .toList();

        LOGGER.info("Async calculate sessionLoad for {} activities", activitiesMissingSessionLoad.size());
        try {
            for (int i = 0; i < activitiesMissingSessionLoad.size(); i += maxBatchSize) {
                int batchNumber = i / maxBatchSize;
                int end = Math.min(i + maxBatchSize, activitiesMissingSessionLoad.size());
                List<Activity> batch = activitiesMissingSessionLoad.subList(i, end);

                // Schedule fetching of batches every 5 minutes to avoid hitting Strava API limits
                taskScheduler.schedule(
                        () -> batch.forEach(activity -> processActivity(activity, token)),
                        Instant.now().plus(Duration.ofMinutes(5L * batchNumber))
                );
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to calculate sessionLoad for {} activities", activitiesMissingSessionLoad.size(), ex);
        }
    }

    private void processActivity(Activity activity, String token) {
        try {
            Integer sessionLoad;
            ApplicationUser user = activity.getUser();

            boolean hasSufferScore = activity.getSufferScore() != null;
            boolean isStravaActivity = activity.getStravaId() != null;
            boolean powerBasedCalculationPossible = activity.getAverageWatts() != null && user.getFtp() != null;
            boolean hasEnergy = activity.getKilojoules() != null;

            // 1. If (Strava) sufferScore is present, use it
            if (hasSufferScore) {
                sessionLoad = fitnessScoreService.calculateSessionLoad(activity.getSufferScore(), activity.getTotalElevationGain());
            } else if (isStravaActivity) { // 2. Strava activity but no sufferScore available: fetch heartRate stream and manually calculate a sessionLoad
                List<StravaStreamDto> streams = fetchStreams(activity.getStravaId(), token);
                sessionLoad = fitnessScoreService.calculateSessionLoad(streams, activity);
            } else if (powerBasedCalculationPossible) { // 3. Not a Strava activity: calculate based on power
                sessionLoad = fitnessScoreService.calculateSessionLoad(user.getFtp(), activity.getMovingTime(), activity.getAverageWatts(), activity.getTotalElevationGain());
            } else if (hasEnergy && user.getWeight() != null) { // 4. No power information available: calculate based on energy and weight
                sessionLoad = fitnessScoreService.calculateSessionLoad(activity.getKilojoules(), user.getWeight().floatValue(), activity.getTotalElevationGain());
            } else { // 5. No energy information available: use distance and moving time
                sessionLoad = fitnessScoreService.calculateSessionLoad(activity.getDistance(), activity.getMovingTime(), activity.getTotalElevationGain());
            }

            activity.setSessionLoad(sessionLoad);
            activityRepository.save(activity);

            LOGGER.debug("Saved sessionLoad {} for activity {}", sessionLoad, activity.getId());
        } catch (Exception ex) {
            LOGGER.error("Failed fetching heartRate data for activity {}", activity.getId(), ex);
        }
    }

    private List<StravaStreamDto> fetchStreams(Long activityId, String token) {
        LOGGER.trace("fetchStreams({}, *token*)", activityId);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString("https://www.strava.com/api/v3/activities/" + activityId + "/streams")
                .queryParam("keys", "heartrate,time");

        return webClient.get()
                .uri(builder.build().toUri())
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(
                                        new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                "Strava API 4xx: " + body)))
                )
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(
                                        new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                                "Strava API 5xx: " + body)))
                )
                .bodyToFlux(StravaStreamDto.class)
                .collectList()
                .block();
    }
}
