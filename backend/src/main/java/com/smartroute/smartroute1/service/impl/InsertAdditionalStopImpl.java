package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.AnchorPointDto;
import com.smartroute.smartroute1.endpoint.dto.ClosestPointResultDto;
import com.smartroute.smartroute1.service.InsertAdditionalStop;
import com.smartroute.smartroute1.util.Coordinate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.lang.invoke.MethodHandles;
import java.net.http.HttpHeaders;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InsertAdditionalStopImpl implements InsertAdditionalStop {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private RestTemplate restTemplate;
    private String baseUrl; // e.g. "https://api.openrouteservice.org/v2/directions/foot-walking"
    private String apiKey;
    private static final int ANCHOR_WINDOW_POINTS = 15;

    @Override
    public List<Coordinate> routeThroughPoint(Coordinate start, Coordinate via, Coordinate end) {

        List<Coordinate> result = new ArrayList<>();
        return result;
    }

    @Override
    public List<Coordinate> addWaypoint(List<Coordinate> originalRoute, Coordinate newPoint) {

        if (originalRoute.size() < 2) {
            throw new IllegalArgumentException("Route must contain at least 2 points");
        }

        ClosestPointResultDto closest = findClosestPoint(originalRoute, newPoint);
        AnchorPointDto anchors = chooseAnchorPoints(originalRoute, closest);

        List<Coordinate> detour = routeThroughPoint(
                anchors.startCoord,
                newPoint,
                anchors.endCoord
        );

        List<Coordinate> finalPoints = new ArrayList<>(originalRoute.subList(0, anchors.startIndex + 1));

        if (!detour.isEmpty()) {
            Coordinate lastOld = finalPoints.getLast();
            for (int i = 0; i < detour.size(); i++) {
                Coordinate c = detour.get(i);
                if (i == 0 && haversine(lastOld, c) < 1.0) {
                    // skip almost identical point
                    continue;
                }
                finalPoints.add(c);
            }
        }

        finalPoints.addAll(originalRoute.subList(anchors.endIndex, originalRoute.size()));

        return finalPoints;
    }


    /**
     * Haversine distance in meters between two lat/lon points.
     */
    private static double haversine(Coordinate a, Coordinate b) {
        final double R = 6371000.0; // Earth radius in m
        double lat1 = Math.toRadians(a.getLatitude());
        double lat2 = Math.toRadians(b.getLatitude());
        double dlat = lat2 - lat1;
        double dlon = Math.toRadians(b.getLongitude() - a.getLongitude());

        double sinLat = Math.sin(dlat / 2);
        double sinLon = Math.sin(dlon / 2);

        double aa = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        double c = 2 * Math.atan2(Math.sqrt(aa), Math.sqrt(1 - aa));

        return R * c;
    }

    /**
     * Project point P onto segment AB (in lat/lon space, approximated as 2D).
     */
    private static Coordinate projectPointToSegment(Coordinate p, Coordinate a, Coordinate b) {
        double ax = a.getLongitude();
        double ay = a.getLatitude();
        double bx = b.getLongitude();
        double by = b.getLatitude();
        double px = p.getLongitude();
        double py = p.getLatitude();

        double abx = bx - ax;
        double aby = by - ay;
        double apx = px - ax;
        double apy = py - ay;

        double abLen2 = abx * abx + aby * aby;
        if (abLen2 == 0) {
            // A and B are the same point
            return a;
        }

        double t = (apx * abx + apy * aby) / abLen2;
        t = Math.max(0, Math.min(1, t)); // clamp to segment

        double projX = ax + t * abx;
        double projY = ay + t * aby;

        return new Coordinate(projY, projX); // (lat, lon)
    }

    /**
     * Find closest segment of the polyline to the new point and derive anchor points where the old route should "exit" and "rejoin".
     */
    private ClosestPointResultDto findClosestPoint(List<Coordinate> polyline, Coordinate newPoint) {
        ClosestPointResultDto best = new ClosestPointResultDto();
        best.distanceMeters = Double.MAX_VALUE;

        for (int i = 0; i < polyline.size() - 1; i++) {
            Coordinate a = polyline.get(i);
            Coordinate b = polyline.get(i + 1);

            Coordinate proj = projectPointToSegment(newPoint, a, b);
            double dist = haversine(newPoint, proj);

            if (dist < best.distanceMeters) {
                best.distanceMeters = dist;
                best.segmentIndex = i;
                best.closestPoint = proj;
            }
        }

        return best;
    }

    /**
     * Choose the indices where the original route should stop (startIndex, endIndex).
     * Very simple heuristic: take a window around the closest segment.
     */
    private AnchorPointDto chooseAnchorPoints(List<Coordinate> polyline, ClosestPointResultDto closest) {
        int n = polyline.size();
        int startIndex = Math.max(0, closest.segmentIndex - ANCHOR_WINDOW_POINTS);
        int endIndex = Math.min(n - 1, closest.segmentIndex + ANCHOR_WINDOW_POINTS);

        AnchorPointDto anchors = new AnchorPointDto();
        anchors.startIndex = startIndex;
        anchors.endIndex = endIndex;
        anchors.startCoord = polyline.get(startIndex);
        anchors.endCoord = polyline.get(endIndex);

        return anchors;
    }
}


