package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.exception.RouteEditingException;
import com.smartroute.smartroute1.service.InsertAdditionalStop;
import com.smartroute.smartroute1.util.Coordinate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InsertAdditionalStopImpl implements InsertAdditionalStop {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final int ANCHOR_WINDOW_POINTS = 15;
    private static final double EARTH_RADIUS_METERS = 6371000.0;

    private record ClosestPointResult(int segmentIndex, Coordinate closestPoint, double distanceMeters) {
    }

    private record AnchorPoint(int startIndex, int endIndex, Coordinate startCoordinate, Coordinate endCoordinate) {
    }

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

        ClosestPointResult closest = findClosestPoint(originalRoute, newPoint);
        AnchorPoint anchors = chooseAnchorPoints(originalRoute, closest);

        List<Coordinate> detour = routeThroughPoint(anchors.startCoordinate, newPoint, anchors.endCoordinate);

        // Original before startIndex
        List<Coordinate> finalPoints = new ArrayList<>(originalRoute.subList(0, anchors.startIndex + 1));

        List<Coordinate> trimmedDetour = trimDetour(detour, anchors.startCoordinate, anchors.endCoordinate);
        finalPoints.addAll(trimmedDetour);

        // Add original after endIndex
        finalPoints.addAll(originalRoute.subList(anchors.endIndex, originalRoute.size()));

        return finalPoints;
    }


    /**
     * Haversine distance in meters between two lat/lon points.
     */
    private static double haversine(Coordinate a, Coordinate b) {
        double lat1 = Math.toRadians(a.getLatitude());
        double lat2 = Math.toRadians(b.getLatitude());
        double dlat = lat2 - lat1;
        double dlon = Math.toRadians(b.getLongitude() - a.getLongitude());

        double sinLat = Math.sin(dlat / 2);
        double sinLon = Math.sin(dlon / 2);

        double aa = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        double c = 2 * Math.atan2(Math.sqrt(aa), Math.sqrt(1 - aa));

        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Project point P onto segment AB (in lat/lon space, approximated as 2D).
     */
    private static Coordinate projectPointToSegment(Coordinate p, Coordinate a, Coordinate b) {
        double lat1 = Math.toRadians(a.getLatitude());
        double lon1 = Math.toRadians(a.getLongitude());
        double lat2 = Math.toRadians(b.getLatitude());
        double lon2 = Math.toRadians(b.getLongitude());
        double latP = Math.toRadians(p.getLatitude());
        double lonP = Math.toRadians(p.getLongitude());

        // Approximate mapping to a local 2D plane.
        double phi0 = (lat1 + lat2) / 2.0;

        double x1 = EARTH_RADIUS_METERS * lon1 * Math.cos(phi0);
        double y1 = EARTH_RADIUS_METERS * lat1;
        double x2 = EARTH_RADIUS_METERS * lon2 * Math.cos(phi0);
        double y2 = EARTH_RADIUS_METERS * lat2;
        double x3 = EARTH_RADIUS_METERS * lonP * Math.cos(phi0);
        double y3 = EARTH_RADIUS_METERS * latP;

        double dx = x2 - x1;
        double dy = y2 - y1;

        // Degenerate segment
        if (dx == 0 && dy == 0) {
            return a;
        }

        double t = ((x3 - x1) * dx + (y3 - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0.0, Math.min(1.0, t));

        double projX = x1 + t * dx;
        double projY = y1 + t * dy;

        double latProj = projY / EARTH_RADIUS_METERS;
        double lonProj = projX / (EARTH_RADIUS_METERS * Math.cos(phi0));

        return new Coordinate(Math.toDegrees(latProj), Math.toDegrees(lonProj));
    }

    /**
     * Find closest segment of the polyline to the new point and derive anchor points where the old route should "exit" and "rejoin".
     */
    private ClosestPointResult findClosestPoint(List<Coordinate> polyline, Coordinate newPoint) {
        if (polyline == null || polyline.size() < 2) {
            throw new IllegalArgumentException("Polyline must contain at least 2 points");
        }

        int bestIndex = -1;
        Coordinate bestPoint = null;
        double bestDist = Double.MAX_VALUE;

        for (int i = 0; i < polyline.size() - 1; i++) {
            Coordinate a = polyline.get(i);
            Coordinate b = polyline.get(i + 1);

            Coordinate proj = projectPointToSegment(newPoint, a, b);
            double dist = haversine(newPoint, proj);

            if (dist < bestDist) {
                bestDist = dist;
                bestIndex = i;
                bestPoint = proj;
            }
        }

        return new ClosestPointResult(bestIndex, bestPoint, bestDist);
    }

    /**
     * Choose the indices where the original route should stop (startIndex, endIndex).
     * Very simple heuristic: take a window around the closest segment.
     */
    private AnchorPoint chooseAnchorPoints(List<Coordinate> polyline, ClosestPointResult closest) {
        int n = polyline.size();
        int startIndex = Math.max(0, closest.segmentIndex - ANCHOR_WINDOW_POINTS);
        int endIndex = Math.min(n - 1, closest.segmentIndex + ANCHOR_WINDOW_POINTS);

        Coordinate startCoordinate = polyline.get(startIndex);
        Coordinate endCoordinate = polyline.get(endIndex);

        return new AnchorPoint(startIndex, endIndex, startCoordinate, endCoordinate);
    }

    /**
     * Removes duplicate endpoints from the detour segment if they coincide with
     * the anchor points (within ~1m).
     */
    private List<Coordinate> trimDetour(List<Coordinate> detour, Coordinate anchorStart, Coordinate anchorEnd) {
        if (detour == null || detour.isEmpty()) {
            throw new RouteEditingException("Empty detour returned from routing service");
        }

        int startIdx = 0;
        int endIdx = detour.size() - 1;

        if (haversine(detour.getFirst(), anchorStart) < 1.0) {
            startIdx = 1;
        }
        if (haversine(detour.get(endIdx), anchorEnd) < 1.0) {
            endIdx -= 1;
        }

        if (startIdx > endIdx) {
            // irregular: just return the original detour.
            return detour;
        }

        return new ArrayList<>(detour.subList(startIdx, endIdx + 1));
    }
}


