package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.StravaActivity;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.repository.StravaActivityRepository;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GpxServiceImpl implements GpxService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final UserService userService;
    private final StravaActivityRepository stravaActivityRepository;

    @Override
    @Transactional
    public StravaActivity importStravaGpxFile(InputStream gpxStream, String email) throws ValidationException {
        LOGGER.trace("importStravaGpxFile({}, {})", gpxStream, email);
        try {
            StravaActivity stravaActivity = new StravaActivity();

            // set user
            ApplicationUser user = userService.findApplicationUserByEmail(email);
            stravaActivity.setUser(user);

            // parse gpx file
            GPX gpx = GPX.Reader.DEFAULT.read(gpxStream);

            // set metadata, i.e. name and start date
            gpx.tracks().findFirst().ifPresent(track -> {
                stravaActivity.setName(track.getName().orElse("Unnamed Activity"));
            });
            gpx.getMetadata().flatMap(Metadata::getTime).ifPresent(time -> {
                stravaActivity.setStartDate(time.toString());
            });

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
                }

                if (seconds > 0) {
                    double speed = distance / seconds;
                    totalDistance += distance;
                    if (speed > maxSpeed) {
                        maxSpeed = speed;
                    }
                    // Consider moving if speed > 0.5 m/s
                    if (speed > 0.5) {
                        movingTime += seconds;
                    }
                    if (elevationDiff > 0) {
                        totalElevationGain += elevationDiff;
                    }
                }
            }

            Instant startTime = allPoints.getFirst().getTime().orElseThrow();
            Instant endTime = allPoints.getLast().getTime().orElseThrow();
            double durationSeconds = Duration.between(startTime, endTime).toSeconds();
            double averageSpeed = durationSeconds > 0 ? totalDistance / durationSeconds : 0.0;
            double averageHeartRate = heartRateCount > 0 ? averageHeartRateSum / heartRateCount : 0.0;

            stravaActivity.setDistance((float) totalDistance);
            stravaActivity.setMovingTime((int) movingTime);
            stravaActivity.setElapsedTime((int) durationSeconds);
            stravaActivity.setTotalElevationGain((float) totalElevationGain);
            stravaActivity.setAverageSpeed((float) averageSpeed);
            stravaActivity.setMaxSpeed((float) maxSpeed);
            stravaActivity.setAverageHeartrate((float) averageHeartRate);
            stravaActivity.setMaxHeartrate((float) maxHeartRate);

            // calculate summary polyline
            final List<Position> path = allPoints
                .stream()
                .map(wp -> Position.fromLngLat(
                    wp.getLatitude().doubleValue(),
                    wp.getLongitude().doubleValue()
                )).toList();
            String polyline = PolylineUtils.encode(path, 5);
            stravaActivity.setSummaryPolyline(polyline);

            return stravaActivityRepository.save(stravaActivity);
        } catch (IOException e) {
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
