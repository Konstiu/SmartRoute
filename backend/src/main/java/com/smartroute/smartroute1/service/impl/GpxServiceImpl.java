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
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

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

            // set metadata, i.e. name and start date
            gpx.tracks().findFirst().ifPresent(track -> {
                activity.setName(track.getName().orElse("Unnamed Activity"));
            });
            gpx.getMetadata().flatMap(Metadata::getTime).ifPresent(activity::setStartDate);

            // extract all waypoints from all segments of all tracks
            List<WayPoint> allPoints = new ArrayList<>();
            gpx.tracks().forEach(track -> {
                track.segments().forEach(segment ->
                    allPoints.addAll(segment.getPoints())
                );
            });

            double totalDistance = 0.0;
            double maxSpeed = 0.0;
            double movingTime = 0.0;
            double totalElevationGain = 0.0;
            double maxHeartRate = 0.0;
            double averageHeartRateSum = 0.0;
            int heartRateCount = 0;
            List<Float> heartRates = new ArrayList<>();
            List<Float> timestamps = new ArrayList<>();

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

                if (seconds > 0) {
                    double speed = distance / seconds;
                    if (speed > maxSpeed) {
                        maxSpeed = speed;
                    }
                    // Consider moving if speed > 0.3 m/s
                    if (speed > 0.3) {
                        movingTime += seconds;
                        totalDistance += distance;
                    }
                }
            }

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
                    wp.getLatitude().doubleValue(),
                    wp.getLongitude().doubleValue()
                )).toList();
            String polyline = PolylineUtils.encode(path, 5);
            activity.setSummaryPolyline(polyline);

            // we do not have a suffer score from GPX, we also have no information about power or energy
            // therefore we can only use the heart-rate based method TRIMP to calculate the sessionLoad
            // if also no heart rates are given in GPX, the distance/moving time based method will be used
            // see FitnessScoreServiceImpl.calculateSessionLoad for details
            int sessionLoad;
            if (maxHeartRate > 0) {
                sessionLoad = fitnessScoreService.calculateSessionLoad(heartRates, timestamps, (float) maxHeartRate, activity);
            } else {
                sessionLoad = fitnessScoreService.calculateSessionLoad(activity.getDistance(), activity.getMovingTime(), activity.getTotalElevationGain());
            }
            activity.setSessionLoad(sessionLoad);


            return activityRepository.save(activity);
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

}
