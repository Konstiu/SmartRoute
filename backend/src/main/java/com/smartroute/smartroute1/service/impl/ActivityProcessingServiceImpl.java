package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.StravaStreamDto;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ActivityStream;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.WeatherResponse;
import com.smartroute.smartroute1.entity.enums.ActivityStreamSource;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.ActivityStreamRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.ActivityProcessingService;
import com.smartroute.smartroute1.service.FitnessScoreService;
import com.smartroute.smartroute1.service.WeatherService;
import com.smartroute.smartroute1.util.Codec;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import com.google.maps.internal.PolylineEncoding;
import com.google.maps.model.LatLng;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ActivityProcessingServiceImpl implements ActivityProcessingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActivityProcessingServiceImpl.class);

    private final WebClient webClient;
    private final FitnessScoreService fitnessScoreService;
    private final ActivityRepository activityRepository;
    private final TaskScheduler taskScheduler;
    private final UserRepository userRepository;
    private final ActivityStreamRepository activityStreamRepository;
    private final WeatherService weatherService;

    @Override
    public void processActivitiesInBatches(int maxBatchSize, List<Activity> activities, String token) {
        LOGGER.trace("processActivitiesInBatches({},{},*token*)", maxBatchSize, activities);

        // Lists of activities with and without sufferScore are separated to immediately calculate fitnessScore for
        // all activities with the necessary data available.

        List<String> processableActivityTypes = List.of("Run", "Walk", "Ride");

        // Find running activities with strava sufferScore and missing sessionLoad and calculate sessionLoad
        List<Activity> activitiesWithStravaSufferScore = activities.stream()
                .filter(a -> a.getSportType() != null && processableActivityTypes.contains(a.getSportType()) && a.getSufferScore() != null && a.getSessionLoad() == null)
                .toList();
        activitiesWithStravaSufferScore.forEach(a ->
                processActivity(a, token)
        );

        // Find running activities without Strava sufferScore and missing sessionLoad, fetch heartrate stream if available
        // and calculate sessionLoad
        List<Activity> activitiesMissingSessionLoad = activities.stream()
                .filter(a -> a.getSportType() != null && processableActivityTypes.contains(a.getSportType()) && a.getSessionLoad() == null && a.getSufferScore() == null)
                .sorted((a, b) -> b.getStartDate().compareTo(a.getStartDate()))
                .toList();

        LOGGER.info("Calculate sessionLoad for {} activities", activitiesMissingSessionLoad.size());
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

    /**
     * Processes imported activities.
     * Calculates time in zones, sessionLoad
     *
     * @param activity the activity to process
     * @param token    the Strava API token to fetch Strava data
     */
    private void processActivity(Activity activity, String token) {
        try {
            Integer sessionLoad;
            ApplicationUser user = activity.getUser();

            boolean hasSufferScore = activity.getSufferScore() != null;
            boolean isStravaActivity = activity.getStravaId() != null;
            boolean powerBasedCalculationPossible = activity.getAverageWatts() != null && user.getFtp() != null;
            boolean hasEnergy = activity.getKilojoules() != null;

            // Fetch weather data
            fetchWeatherForActivity(activity);

            /*
            Fetch Strava activity stravaStreams
            Never re-fetch stravaStreams
             */
            List<StravaStreamDto> stravaStreams;
            if (activity.getActivityStream() == null) {
                stravaStreams = fetchStreams(activity.getStravaId(), token);

                // Calculate time in hr-zones
                Map<Integer, Float> timeInZones = fitnessScoreService.calculateTimeInZones(stravaStreams, user);

                // Set time in hr-zones
                timeInZones.forEach((zone, time) -> {
                    switch (zone) {
                        case 1 -> activity.setTimeZ1(Math.round(time));
                        case 2 -> activity.setTimeZ2(Math.round(time));
                        case 3 -> activity.setTimeZ3(Math.round(time));
                        case 4 -> activity.setTimeZ4(Math.round(time));
                        case 5 -> activity.setTimeZ5(Math.round(time));
                        default -> throw new IllegalStateException("Unexpected value: " + zone);
                    }
                });

                // Extract stravaStreams, map to double list or null if not found and create an ActivityStream object
                ActivityStream activityStream = createActivityStream(
                        stravaStreams.stream().filter(s -> s.getType().equals("time")).findFirst().map(s -> s.getData().stream().map(f -> (double) f).toList()).orElse(null),
                        stravaStreams.stream().filter(s -> s.getType().equals("distance")).findFirst().map(s -> s.getData().stream().map(f -> (double) f).toList()).orElse(null),
                        stravaStreams.stream().filter(s -> s.getType().equals("heartrate")).findFirst().map(s -> s.getData().stream().map(f -> (double) f).toList()).orElse(null),
                        ActivityStreamSource.STRAVA
                );

                if (activityStream != null) {
                    activityStream = activityStreamRepository.save(activityStream);
                    activity.setActivityStream(activityStream);
                }

            } else if (activity.getActivityStream().getHeartrateStream() != null && activity.getActivityStream().getTimeStream() != null) {
                // If stravaStreams are already stored decode time and heartrate streams and add to StravaStreamDto list
                stravaStreams = new ArrayList<>();
                stravaStreams.add(new StravaStreamDto(
                                "time",
                                Codec.toFloatList(Codec.decodeDoubleArray(activity.getActivityStream().getTimeStream())),
                                null,
                                -1,
                                null
                        )
                );
                stravaStreams.add(new StravaStreamDto(
                                "heartrate",
                                Codec.toFloatList(Codec.decodeDoubleArray(activity.getActivityStream().getTimeStream())),
                                null,
                                -1,
                                null
                        )
                );
            } else {
                // Streams list is empty if no hr or time stream stored
                stravaStreams = new ArrayList<>();
            }

            if (hasSufferScore) {
                // 1. If (Strava) sufferScore is present, use it
                sessionLoad = fitnessScoreService.calculateSessionLoad(activity.getSufferScore(), activity.getTotalElevationGain());
            } else if (isStravaActivity) {
                // 2. Strava activity but no sufferScore available: use heartRate stream and manually calculate a sessionLoad
                sessionLoad = fitnessScoreService.calculateSessionLoad(stravaStreams, activity);
            } else if (powerBasedCalculationPossible) {
                // 3. Not a Strava activity: calculate based on power
                sessionLoad = fitnessScoreService.calculateSessionLoad(user.getFtp(), activity.getMovingTime(), activity.getAverageWatts(), activity.getTotalElevationGain());
            } else if (hasEnergy && user.getWeight() != null) {
                // 4. No power information available: calculate based on energy and weight
                sessionLoad = fitnessScoreService.calculateSessionLoad(activity.getKilojoules(), user.getWeight().floatValue(), activity.getTotalElevationGain());
            } else {
                // 5. No energy information available: use distance and moving time
                sessionLoad = fitnessScoreService.calculateSessionLoad(activity.getDistance(), activity.getMovingTime(), activity.getTotalElevationGain(), activity.getSportType());
            }

            activity.setSessionLoad(sessionLoad);

            List<Activity> storedActivities = activityRepository.findAllByUserAndStartDate(user, activity.getStartDate());
            Activity storedActivity = getStoredActivity(activity, storedActivities);
            if (storedActivity == null) {
                activityRepository.save(activity);
            } else {
                storedActivity.setExternalId(activity.getExternalId());
                storedActivity.setStravaId(activity.getStravaId());
                storedActivity.setSufferScore(activity.getSufferScore());
                storedActivity.setAverageWatts(activity.getAverageWatts());
                storedActivity.setKilojoules(activity.getKilojoules());
                storedActivity.setTotalElevationGain(storedActivity.getTotalElevationGain());
                storedActivity.setStartDate(storedActivity.getStartDate());
                storedActivity.setElapsedTime(storedActivity.getElapsedTime());
                storedActivity.setMovingTime(storedActivity.getMovingTime());
                storedActivity.setMaxHeartrate(storedActivity.getMaxHeartrate());

                // Update time in zones only if it was not stored before
                if (activity.getTimeZ1() != null && storedActivity.getTimeZ1() == null) {
                    storedActivity.setTimeZ1(activity.getTimeZ1());
                    storedActivity.setTimeZ2(activity.getTimeZ2());
                    storedActivity.setTimeZ3(activity.getTimeZ3());
                    storedActivity.setTimeZ4(activity.getTimeZ4());
                    storedActivity.setTimeZ5(activity.getTimeZ5());
                }

                storedActivity.setSummaryPolyline(storedActivity.getSummaryPolyline());
                storedActivity.setAverageHeartrate(activity.getAverageHeartrate());
                storedActivity.setAverageSpeed(activity.getAverageSpeed());
                storedActivity.setMaxSpeed(activity.getMaxSpeed());
                storedActivity.setSessionLoad(activity.getSessionLoad());
                // always the first name is going to be the new name of the Activity
                //storedActivity.setName(entity.getName());
                activityRepository.save(storedActivity);
            }

            LOGGER.debug("Saved sessionLoad {} for activity {}", sessionLoad, activity.getId());
        } catch (Exception ex) {
            LOGGER.error("Failed fetching heartRate data for activity {}", activity.getId(), ex);
        }
    }

    @Override
    public void fetchWeatherForActivity(Activity activity) {
        try {
            if (activity.getSummaryPolyline() != null && activity.getStartDate() != null) {
                LatLng startLatLng = PolylineEncoding.decode(activity.getSummaryPolyline()).getFirst();
                ZonedDateTime utcDateTime = activity.getStartDate()
                        .atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(ZoneId.of("UTC")).withMinute(0).withSecond(0).withNano(0);
                String utcTimeStr = utcDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));

                if (utcDateTime.isBefore(LocalDate.now().minusDays(90).atStartOfDay(ZoneId.systemDefault()))) {
                    return;
                }

                WeatherResponse weather = weatherService.getWeatherAtTime(startLatLng.lat, startLatLng.lng, utcTimeStr);
                activity.setWeather(weather);
            }
        } catch (ValidationException e) {
            LOGGER.error("Failed to process activity {}: {}", activity, e.getMessage());
        }
    }

    private Activity getStoredActivity(Activity activity, List<Activity> storedActivities) {
        Activity storedActivity = null;
        if (storedActivities.size() > 1) {
            float newDistance = activity.getDistance();

            for (Activity stored : storedActivities) {
                float storedDistance = stored.getDistance();
                float distanceDiff = Math.abs(storedDistance - newDistance);

                if (distanceDiff <= 1000) {
                    storedActivity = stored;
                    break;
                }
            }
        } else if (storedActivities.size() == 1) {
            storedActivity = storedActivities.get(0);
        }
        return storedActivity;
    }

    /**
     * Fetches Strava streams for an activity.
     *
     * @param activityId the id of the activity to fetch streams for
     * @param token      the Strava API token for the authenticated user
     * @return a list of Strava streams
     */
    private List<StravaStreamDto> fetchStreams(Long activityId, String token) {
        LOGGER.trace("fetchStreams({}, *token*)", activityId);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString("https://www.strava.com/api/v3/activities/" + activityId + "/streams")
                .queryParam("keys", "heartrate,distance,time");

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

    @Override
    public ActivityStream createActivityStream(List<Double> time, List<Double> distance, List<Double> heartRate, ActivityStreamSource source) {
        LOGGER.trace("createActivityStream({},{},{},{})", time, distance, heartRate, source);
        // Check if all non-null lists have the same size. Return null otherwise.
        List<List<?>> listOfLists = Arrays.asList(time, distance, heartRate);
        if (listOfLists.stream().filter(Objects::nonNull).mapToLong(List::size).distinct().count() > 1) {
            LOGGER.error("Failed to create ActivityStream: List sizes do not match");
            return null;
        }

        ActivityStream stream = new ActivityStream();

        stream.setTimeStream(
                time == null ? null : Codec.encodeDoubleArray(
                        time.stream().mapToDouble(Double::doubleValue).toArray()
                )
        );

        stream.setDistanceStream(
                distance == null ? null : Codec.encodeDoubleArray(
                        distance.stream().mapToDouble(Double::doubleValue).toArray()
                )
        );

        stream.setHeartrateStream(
                heartRate == null ? null : Codec.encodeDoubleArray(
                        heartRate.stream().mapToDouble(Double::doubleValue).toArray()
                )
        );

        stream.setSource(source);

        return stream;
    }

    @Override
    public List<Activity> getActivities(String email) {
        LOGGER.trace("Get all Strava activities for user with mail: {}", email);
        ApplicationUser user = userRepository.findUserByEmail(email);
        return activityRepository.findByUser(user);

    }

    @Override
    public Activity getActivity(String email, long id) {
        LOGGER.trace("Get Strava activity for user with mail: {}", email);
        ApplicationUser user = userRepository.findUserByEmail(email);
        Activity act = activityRepository.findByIdAndUser(id, user);
        if (act == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Strava activity not found");
        }
        return act;
    }

    @Override
    public Optional<Activity> getLastActivityBeforeDate(String email, LocalDate date) {
        LOGGER.trace("Get last Strava activity before date {} for user with mail: {}", date, email);
        ApplicationUser user = userRepository.findUserByEmail(email);
        Instant instant = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        return activityRepository.findTopByUserAndStartDateBeforeOrderByStartDateDesc(user, instant);
    }

    @Override
    public List<Activity> getLastActivities(String email, int n) throws IllegalArgumentException {
        LOGGER.trace("Get last {} Strava activities for user with mail: {}", n, email);
        if (n <= 0) {
            throw new IllegalArgumentException("n must be greater than zero");
        }
        ApplicationUser user = userRepository.findUserByEmail(email);
        return activityRepository.findByUserOrderByStartDateDesc(user, PageRequest.of(0, n));
    }

    @Override
    public Optional<Activity> getLastRunningActivityBeforeDate(String email, LocalDate date) {
        LOGGER.trace("Get last running Strava activity before date {} for user with mail: {}", date, email);
        ApplicationUser user = userRepository.findUserByEmail(email);
        Instant instant = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        return activityRepository.findTopByUserAndWorkoutTypeInAndStartDateBeforeOrderByStartDateDesc(
                user,
                List.of(WorkoutType.EASY_RUN, WorkoutType.TEMPO_RUN, WorkoutType.INTERVAL_RUN, WorkoutType.LONG_RUN),
                instant
        );
    }

    /**
     * Spike detection algorithm.
     * Works for both HR spikes and pace spikes with appropriate configuration.
     */
    private List<Integer> detectSpikesWithTime(double[] time, double[] data, SpikeConfig config) {
        if (data == null || time == null || data.length != time.length || data.length < 5) {
            return null;
        }

        // Filter out single-point outliers
        double[] filtered = filterOutliers(data, config.noiseThreshold);

        List<Integer> spikeIndices = new ArrayList<>();
        int i = 0;

        while (i < filtered.length - 1) {
            // Calculate baseline from recent window
            double baseline = calculateBaseline(time, filtered, i, config.baselineWindow);

            // Look ahead for potential spike
            SpikeCandidate candidate = findSpikeCandidate(
                    time, filtered, i, baseline, config
            );

            if (candidate != null) {
                // Verify the spike is sustained
                int endIndex = getSustainEndIndex(time, filtered, candidate, config, baseline);
                if (endIndex >= 0) {
                    spikeIndices.add(candidate.peakIndex);
                    i = endIndex; // Skip past this spike
                    continue;
                }
            }

            i++;
        }

        return spikeIndices;
    }

    /**
     * Filter out single-point outliers using median-based approach.
     */
    private double[] filterOutliers(double[] data, double threshold) {
        double[] filtered = new double[data.length];

        for (int i = 0; i < data.length; i++) {
            if (i < 2 || i >= data.length - 2) {
                filtered[i] = data[i];
                continue;
            }

            // Get 5-point window
            double[] window = {data[i - 2], data[i - 1], data[i], data[i + 1], data[i + 2]};
            double[] windowCopy = Arrays.copyOf(window, window.length);
            Arrays.sort(windowCopy);
            double median = windowCopy[2];

            // Calculate MAD (Median Absolute Deviation)
            double[] deviations = new double[5];
            for (int j = 0; j < 5; j++) {
                deviations[j] = Math.abs(window[j] - median);
            }
            Arrays.sort(deviations);
            double mad = deviations[2];

            // If point is too far from median, replace with median
            if (Math.abs(data[i] - median) > threshold * mad && mad > 0) {
                filtered[i] = median;
            } else {
                filtered[i] = data[i];
            }
        }

        return filtered;
    }

    /**
     * Calculate baseline from recent history using median.
     */
    private double calculateBaseline(double[] time, double[] data, int currentIndex, double windowSec) {
        if (currentIndex <= 0) {
            return data[0];
        }

        int start = currentIndex - 1; // Start from previous point
        while (start > 0 && time[currentIndex - 1] - time[start - 1] <= windowSec) {
            start--;
        }

        List<Double> values = new ArrayList<>();
        for (int i = start; i < currentIndex; i++) {
            values.add(data[i]);
        }

        if (values.isEmpty()) {
            return data[currentIndex - 1];
        }

        Collections.sort(values);
        int mid = values.size() / 2;
        return values.size() % 2 == 0
                ? (values.get(mid - 1) + values.get(mid)) / 2.0
                : values.get(mid);
    }

    /**
     * Find a potential spike candidate starting from index.
     */
    private SpikeCandidate findSpikeCandidate(
            double[] time, double[] data, int startIndex,
            double baseline, SpikeConfig config
    ) {
        double extremeValue = data[startIndex];
        int extremeIndex = startIndex;

        // Look ahead within max duration
        for (int i = startIndex + 1; i < data.length; i++) {
            if (time[i] - time[startIndex] > config.maxSpikeDuration) {
                break;
            }

            // Update extreme value based on detection direction
            if (config.detectIncreases) {
                if (data[i] > extremeValue) {
                    extremeValue = data[i];
                    extremeIndex = i;
                }
            } else {
                if (data[i] < extremeValue) {
                    extremeValue = data[i];
                    extremeIndex = i;
                }
            }

            // Check if we found a sufficient change
            double change = config.detectIncreases
                    ? (extremeValue - baseline)
                    : (baseline - extremeValue);

            if (change >= config.minChange) {
                // Calculate rate of change per second
                double duration = time[extremeIndex] - time[startIndex];
                double rate = duration > 0 ? change / duration : 0;

                // Must be steep enough
                if (rate >= config.minRateOfChange) {
                    return new SpikeCandidate(startIndex, extremeIndex, extremeValue, baseline, change);
                }
            }
        }

        return null;
    }

    /**
     * Verify that changed value is sustained and return the end index of the sustained period.
     * Returns -1 if not sustained.
     */
    private int getSustainEndIndex(
            double[] time, double[] data, SpikeCandidate spike,
            SpikeConfig config, double baseline
    ) {
        int sustainStart = spike.peakIndex;
        double threshold = config.detectIncreases
                ? baseline + (spike.change * config.minSustainThreshold)  // above baseline
                : baseline - (spike.change * config.minSustainThreshold); // below baseline

        int lastSustainedIndex = spike.peakIndex;
        double sustainedTime = 0;

        // Check how long value stays changed
        for (int i = spike.peakIndex; i < data.length; i++) {
            boolean isStillChanged = config.detectIncreases
                    ? (data[i] >= threshold)
                    : (data[i] <= threshold);

            if (isStillChanged) {
                sustainedTime = time[i] - time[sustainStart];
                lastSustainedIndex = i;
            } else {
                break;
            }
        }

        // Return end index if sustained long enough, otherwise -1
        return sustainedTime >= config.minSustainDuration ? lastSustainedIndex : -1;
    }

    @Override
    public int detectHeartRateSpikes(Activity activity) {
        ActivityStream stream = activity.getActivityStream();
        if (stream == null || stream.getHeartrateStream() == null || stream.getTimeStream() == null) {
            return -1;
        }

        double[] hrArray = Codec.decodeDoubleArray(stream.getHeartrateStream());
        double[] timeArray = Codec.decodeDoubleArray(stream.getTimeStream());

        if (hrArray.length != timeArray.length) {
            throw new IllegalStateException("HR and time arrays must match");
        }

        List<Integer> spikes = detectSpikesWithTime(timeArray, hrArray, SpikeConfig.forHeartRate());
        if (spikes == null) {
            return -1;
        }
        return spikes.size();
    }

    @Override
    public int detectPaceSpikes(Activity activity) {
        ActivityStream stream = activity.getActivityStream();
        if (stream == null || stream.getDistanceStream() == null || stream.getTimeStream() == null) {
            return -1;
        }

        double[] distanceArray = Codec.decodeDoubleArray(stream.getDistanceStream());
        double[] timeArray = Codec.decodeDoubleArray(stream.getTimeStream());

        if (distanceArray.length != timeArray.length) {
            throw new IllegalStateException("Distance and time arrays must match");
        }

        // Compute smoothed speed (m/s) using rolling average to reduce GPS noise
        double[] speed = computeSmoothedSpeed(distanceArray, timeArray, 5); // 5-point smoothing

        List<Integer> spikes = detectSpikesWithTime(timeArray, speed, SpikeConfig.forPace());
        if (spikes == null) {
            return -1;
        }
        return spikes.size();
    }

    /**
     * Compute smoothed speed to reduce GPS noise.
     * Uses rolling average over windowSize points.
     */
    private double[] computeSmoothedSpeed(double[] distance, double[] time, int windowSize) {
        double[] speed = new double[distance.length];

        for (int i = 0; i < distance.length; i++) {
            // Determine window bounds
            int start = Math.max(0, i - windowSize / 2);
            int end = Math.min(distance.length - 1, i + windowSize / 2);

            // Calculate speed over window
            double distDiff = distance[end] - distance[start];
            double timeDiff = time[end] - time[start];

            speed[i] = timeDiff > 0 ? distDiff / timeDiff : 0;
        }

        return speed;
    }

    /**
     * Configuration for spike detection behavior.
     */
    private static class SpikeConfig {
        final double minChange;           // Minimum change to consider (bpm or m/s)
        final double maxSpikeDuration;    // Max seconds for the change to occur
        final double minSustainDuration;  // Must stay changed for at least this long
        final double minSustainThreshold;  // Below which value relative to baseline must a spike drop before it is not sustained
        final double baselineWindow;      // Seconds to calculate baseline
        final double noiseThreshold;      // Std devs for outlier filtering
        final double minRateOfChange;     // Minimum rate of change per second
        final boolean detectIncreases;    // true = detect increases, false = detect decreases

        SpikeConfig(double minChange, double maxSpikeDuration, double minSustainDuration, double minSustainThreshold,
                    double baselineWindow, double noiseThreshold, double minRateOfChange,
                    boolean detectIncreases) {
            this.minChange = minChange;
            this.maxSpikeDuration = maxSpikeDuration;
            this.minSustainDuration = minSustainDuration;
            this.minSustainThreshold = minSustainThreshold;
            this.baselineWindow = baselineWindow;
            this.noiseThreshold = noiseThreshold;
            this.minRateOfChange = minRateOfChange;
            this.detectIncreases = detectIncreases;
        }

        // Preset for HR spikes (sudden increases)
        static SpikeConfig forHeartRate() {
            return new SpikeConfig(
                    14.0,   // 14 bpm increase
                    15.0,   // within 15 seconds
                    3.0,    // sustained for 3 seconds
                    0.7,    // 70% of hr before spike is not sustained
                    30.0,   // 30 second baseline
                    2.5,    // noise threshold
                    2,    // 2 bpm/second minimum
                    true    // detect increases
            );
        }

        // Preset for pace spikes (sudden accelerations = speed increases)
        static SpikeConfig forPace() {
            return new SpikeConfig(
                    0.95,    // 0.9 m/s increase in speed
                    15.0,    // within 15 seconds
                    10.0,    // sustained for 10 seconds
                    0.90,    // 90% of pace before spike is not sustained
                    25.0,   // 25 second baseline
                    2.5,    // noise threshold
                    0.25,    // 0.25 m/s per second minimum
                    true    // detect increases
            );
        }
    }

    /**
     * Data class to hold spike candidate information.
     */
    private static class SpikeCandidate {
        int startIndex;
        int peakIndex;
        double peakValue;
        double baseline;
        double change;

        SpikeCandidate(int startIndex, int peakIndex, double peakValue, double baseline, double change) {
            this.startIndex = startIndex;
            this.peakIndex = peakIndex;
            this.peakValue = peakValue;
            this.baseline = baseline;
            this.change = change;
        }
    }
}
