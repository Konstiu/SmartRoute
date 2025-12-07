package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.service.FitnessScoreService;
import com.smartroute.smartroute1.service.GpxService;
import com.smartroute.smartroute1.service.UserService;
import io.jenetics.jpx.Length;
import io.jenetics.jpx.Metadata;
import io.jenetics.jpx.WayPoint;
import io.jenetics.jpx.geom.Geoid;
import io.leonard.PolylineUtils;
import io.leonard.Position;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import io.jenetics.jpx.GPX;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GpxServiceImpl implements GpxService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final UserService userService;
    private final ActivityRepository activityRepository;
    private final FitnessScoreService fitnessScoreService;

    @Override
    @Transactional
    public Activity importStravaGpxFile(InputStream gpxStream, String email) throws ValidationException {
        LOGGER.trace("importStravaGpxFile({}, {})", gpxStream, email);
        try {
            Activity activity = new Activity();

            // set user
            ApplicationUser user = userService.findApplicationUserByEmail(email);
            activity.setUser(user);

            // parse gpx file
            GPX gpx = GPX.Reader.DEFAULT.read(gpxStream);

            // set metadata, i.e. name, type and start date
            gpx.tracks().findFirst().ifPresent(track -> {
                activity.setName(track.getName().orElse("Unnamed Activity"));

                String type = track.getType().orElse(null);
                String normalized = type == null ? "" : type.trim().toLowerCase();
                Set<String> runningTypes = Set.of(
                        "run", "running", "jogging", "trail run", "trail running",
                        "fell running", "track run", "treadmill", "indoor running",
                        "virtual run"
                );
                if (normalized.isEmpty()) {
                    activity.setSportType("Other");
                } else if (runningTypes.contains(normalized)) {
                    activity.setSportType("Run");
                } else {
                    // keep original GPX type
                    activity.setSportType(type);
                }
            });
            gpx.getMetadata().flatMap(Metadata::getTime).ifPresent(startDate -> {
                ZoneId sys = ZoneId.systemDefault();
                ZoneOffset offset = sys.getRules().getOffset(startDate);
                activity.setStartDateLocal(startDate.plusSeconds(offset.getTotalSeconds()));
                activity.setStartDate(startDate);
            });

            // extract all waypoints from all segments of all tracks
            List<WayPoint> allPoints = new ArrayList<>();
            gpx.tracks().forEach(track -> {
                track.segments().forEach(segment ->
                    allPoints.addAll(segment.getPoints())
                );
            });

            double totalDistance = 0.0;
            double movingTime = 0.0;
            double totalElevationGain = 0.0;
            double maxHeartRate = 0.0;
            double averageHeartRateSum = 0.0;
            int heartRateCount = 0;
            List<Float> heartRates = new ArrayList<>();
            List<Float> timestamps = new ArrayList<>();
            List<Double> segDurations = new ArrayList<>();
            List<Double> segDistances = new ArrayList<>();

            for (int i = 1; i < allPoints.size(); i++) {
                WayPoint p1 = allPoints.get(i - 1);
                WayPoint p2 = allPoints.get(i);

                Instant t1 = p1.getTime().orElseThrow();
                Instant t2 = p2.getTime().orElseThrow();

                // Calculate distance in meters
                double distance = Geoid.WGS84.distance(p1, p2).doubleValue();

                // Calculate elevation gain in meters
                Length e1 = p1.getElevation().orElseThrow();
                Length e2 = p2.getElevation().orElseThrow();
                double elevationDiff = e2.doubleValue() - e1.doubleValue();

                if (elevationDiff > 0) {
                    totalElevationGain += elevationDiff;
                }

                // Calculate time difference in seconds
                double seconds = Duration.between(t1, t2).toSeconds();

                // Get heart rate if available
                Optional<Double> hr = extractHeartRateFromWayPoint(p1);
                if (hr.isPresent()) {
                    averageHeartRateSum += hr.get();
                    heartRateCount++;
                    if (hr.get() > maxHeartRate) {
                        maxHeartRate = hr.get();
                    }
                    heartRates.add(hr.get().floatValue());
                    timestamps.add(t1.toEpochMilli() / 1000.0f);
                }

                // distance and moving time
                if (seconds > 0) {
                    segDurations.add(seconds);
                    segDistances.add(distance);
                    double speed = distance / seconds;
                    // Consider moving if speed > 0.3 m/s
                    if (speed > 0.3) {
                        movingTime += seconds;
                        totalDistance += distance;
                    }
                }
            }

            // calculate max speed separately (to avoid spikes from GPS errors)
            double maxSpeed = calculateMaxSpeed(segDurations, segDistances);

            Instant startTime = allPoints.getFirst().getTime().orElseThrow();
            Instant endTime = allPoints.getLast().getTime().orElseThrow();
            double durationSeconds = Duration.between(startTime, endTime).toSeconds();
            double averageSpeed = durationSeconds > 0 ? totalDistance / durationSeconds : 0.0;
            double averageHeartRate = heartRateCount > 0 ? averageHeartRateSum / heartRateCount : 0.0;

            activity.setDistance((float) totalDistance);
            activity.setMovingTime((int) movingTime);
            activity.setElapsedTime((int) durationSeconds);
            activity.setTotalElevationGain((float) totalElevationGain);
            activity.setAverageSpeed((float) averageSpeed);
            activity.setMaxSpeed((float) maxSpeed);
            activity.setAverageHeartrate((float) averageHeartRate);
            activity.setMaxHeartrate((float) maxHeartRate);

            // calculate summary polyline
            final List<Position> path = allPoints
                .stream()
                .map(wp -> Position.fromLngLat(
                    wp.getLongitude().doubleValue(),
                    wp.getLatitude().doubleValue()
                )).toList();
            String polyline = PolylineUtils.encode(path, 5);
            activity.setSummaryPolyline(polyline);

            // we do not have a suffer score from GPX, we also have no information about power or energy
            // therefore we can only use the heart-rate based method TRIMP to calculate the sessionLoad
            // if also no heart rates are given in GPX, the distance/moving time based method will be used
            // see FitnessScoreServiceImpl.calculateSessionLoad for details
            int sessionLoad;
            if (maxHeartRate > 0) {
                sessionLoad = fitnessScoreService.calculateSessionLoad(heartRates, timestamps, activity);
            } else {
                sessionLoad = fitnessScoreService.calculateSessionLoad(activity.getDistance(), activity.getMovingTime(), activity.getTotalElevationGain());
            }
            activity.setSessionLoad(sessionLoad);


            List<Activity> storedActivities = activityRepository.findAllByUserAndStartDate(user, activity.getStartDate());
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
            if (storedActivity == null) {
                return activityRepository.save(activity);
            } else {
                storedActivity.setTotalElevationGain(activity.getTotalElevationGain());
                storedActivity.setAverageSpeed(activity.getAverageSpeed());
                storedActivity.setMaxSpeed(activity.getMaxSpeed());
                storedActivity.setAverageHeartrate(activity.getAverageHeartrate());

                // only update session load if strava suffer score was not set before
                if (storedActivity.getSufferScore() == null) {
                    storedActivity.setSessionLoad(activity.getSessionLoad());
                }
                storedActivity.setStartDate(activity.getStartDate());
                storedActivity.setElapsedTime(activity.getElapsedTime());
                storedActivity.setMovingTime(activity.getMovingTime());
                storedActivity.setMaxHeartrate(activity.getMaxHeartrate());
                storedActivity.setExternalId(activity.getExternalId());
                storedActivity.setSummaryPolyline(storedActivity.getSummaryPolyline());
                storedActivity.setAverageWatts(storedActivity.getAverageWatts());
                storedActivity.setKilojoules(storedActivity.getKilojoules());
                storedActivity.setStravaId(storedActivity.getStravaId());
                storedActivity.setSufferScore(storedActivity.getSufferScore());
                storedActivity.setSportType(storedActivity.getSportType());
                // always the first name is going to be the new name of the Activity
                //storedActivity.setName(entity.getName());
                return activityRepository.save(storedActivity);
            }
        } catch (IOException | NoSuchElementException e) {
            throw new ValidationException("Failed to read GPX file", List.of("GPX file could not be processed"));
        }
    }

    /** Helper method to extract heart rate from WayPoint extensions.
     * The XML looks like this:
     * <extensions>
     *   <gpxtpx:TrackPointExtension>
     *     <gpxtpx:hr>91</gpxtpx:hr>
     *     <gpxtpx:cad>51</gpxtpx:cad>
     *   </gpxtpx:TrackPointExtension>
     * </extensions>
     *
     * @param wayPoint the WayPoint to extract heart rate from
     * @return an Optional containing the heart rate if present, otherwise an empty Optional
     */
    private Optional<Double> extractHeartRateFromWayPoint(WayPoint wayPoint) {
        return wayPoint.getExtensions()
            .map(Document::getDocumentElement)
            .map(ext -> {
                NodeList tpxList = ext.getElementsByTagNameNS("*", "TrackPointExtension");
                return tpxList.getLength() > 0 ? (Element) tpxList.item(0) : null;
            })
            .map(tpx -> {
                NodeList hrList = tpx.getElementsByTagNameNS("*", "hr");
                return hrList.getLength() > 0 ? hrList.item(0).getTextContent() : null;
            })
            .flatMap(str -> Optional.of(str).map(Double::valueOf));
    }

    public double calculateMaxSpeed(List<Double> segDurations, List<Double> segDistances) {
        // if no segments, return 0
        if (segDurations.isEmpty()) {
            return 0.0;
        }

        double windowsSeconds = 20.0;
        double maxAllowedSpeed = 10.0; // m/s (36 km/h), to filter out GPS spikes

        // use sliding window approach to find max speed over WINDOW_SECONDS
        double maxSpeed = 0.0;
        double windowTime = 0.0;
        double windowDist = 0.0;
        int start = 0;

        for (int end = 0; end < segDurations.size(); end++) {
            windowTime += segDurations.get(end);
            windowDist += segDistances.get(end);

            // îf the window time exceeds WINDOW_SECONDS, trim from the start
            while (windowTime >= windowsSeconds && start <= end) {
                // calculate time that must be removed from the windows
                double timeToRemove = windowTime - windowsSeconds;

                double distanceToRemove = 0.0;
                int idx = start;
                while (timeToRemove > 0 && idx <= end) {
                    double segT = segDurations.get(idx);
                    double segD = segDistances.get(idx);
                    if (timeToRemove >= segT) {
                        // remove whole segment
                        distanceToRemove += segD;
                        timeToRemove -= segT;
                        idx++;
                    } else {
                        // remove fraction of segment
                        double frac = timeToRemove / segT;
                        distanceToRemove += segD * frac;
                        timeToRemove = 0;
                    }
                }

                // check if the current window has the new max speed
                double trimmedDist = windowDist - distanceToRemove;
                double speedForWindow = trimmedDist / windowsSeconds; // m/s

                if (speedForWindow <= maxAllowedSpeed) {
                    maxSpeed = Math.max(maxSpeed, speedForWindow);
                }

                // move the whole window one to the right by removing the start/left most segment
                windowTime -= segDurations.get(start);
                windowDist -= segDistances.get(start);
                start++;
            }
        }

        return maxSpeed;
    }

}
