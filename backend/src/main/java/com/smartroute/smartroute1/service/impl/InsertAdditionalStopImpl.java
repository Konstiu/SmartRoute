package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.service.InsertAdditionalStop;
import com.smartroute.smartroute1.service.validators.InsertAdditionalStopValidator;
import com.smartroute.smartroute1.util.Coordinate;
import io.jenetics.jpx.GPX;
import io.jenetics.jpx.WayPoint;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InsertAdditionalStopImpl implements InsertAdditionalStop {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final int ANCHOR_WINDOW_POINTS = 15;
    private static final double EARTH_RADIUS_METERS = 6371000.0;

    private final InsertAdditionalStopValidator validator;

    private record ClosestPointResult(int segmentIndex, Coordinate closestPoint, double distanceMeters, double totalLengthRoute) {
    }

    private record AnchorPoint(int startIndex, int endIndex, Coordinate startCoordinate, Coordinate endCoordinate) {
    }

    @Override
    public List<Coordinate> routeThroughPoint(Coordinate start, Coordinate via, Coordinate end) {

        List<Coordinate> result = new ArrayList<>();
        result.add(start);
        result.add(via);
        result.add(end);
        return result;
    }

    @Override
    public List<Coordinate> addWaypoint(List<Coordinate> originalRoute, Coordinate newPoint) throws ValidationException {
        validator.validateRouteLength(originalRoute);

        ClosestPointResult closest = findClosestPoint(originalRoute, newPoint);
        AnchorPoint anchors = chooseAnchorPoints(originalRoute, closest);

        List<Coordinate> detour = routeThroughPoint(anchors.startCoordinate, newPoint, anchors.endCoordinate);

        // Original before startIndex
        List<Coordinate> finalPoints = new ArrayList<>(originalRoute.subList(0, anchors.startIndex + 1));

        List<Coordinate> trimmedDetour = trimDetour(detour, anchors.startCoordinate, anchors.endCoordinate);
        finalPoints.addAll(trimmedDetour);

        // Add original after endIndex
        finalPoints.addAll(originalRoute.subList(anchors.endIndex, originalRoute.size()));

        validator.validateSameEndpoints(originalRoute, finalPoints);

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
    private ClosestPointResult findClosestPoint(List<Coordinate> polyline, Coordinate newPoint) throws ValidationException {
        validator.validateRouteLength(polyline);

        int bestIndex = -1;
        Coordinate bestPoint = null;
        double bestDist = Double.MAX_VALUE;
        double routeLength = 0.0;

        for (int i = 0; i < polyline.size() - 1; i++) {
            Coordinate a = polyline.get(i);
            Coordinate b = polyline.get(i + 1);
            routeLength += haversine(a, b);

            Coordinate proj = projectPointToSegment(newPoint, a, b);
            double dist = haversine(newPoint, proj);

            if (dist < bestDist) {
                bestDist = dist;
                bestIndex = i;
                bestPoint = proj;
            }
        }

        return new ClosestPointResult(bestIndex, bestPoint, bestDist, routeLength);
    }

    /**
     * Picks anchor points where the route should leave and rejoin.
     * Uses curvature + minimum distance to select natural anchor points.
     */
    private AnchorPoint chooseAnchorPoints(List<Coordinate> route, ClosestPointResult closest) {
        int routeSize = route.size();
        int index = closest.segmentIndex;
        double routeLength = closest.totalLengthRoute;
        double minAnchorDistance = Math.min(closest.distanceMeters, routeLength / 6); // @TODO tweak this value

        // 1) Move backward until distance >= min and curvature small
        int startIndex = walkUntilStable(route, index, - 1, minAnchorDistance);

        // 2) Move forward until distance >= min and curvature small
        int endIndex = walkUntilStable(route, index + 1, + 1, minAnchorDistance);

        // 3) Clamp so first/last route points are never removed
        startIndex = Math.max(1, startIndex);
        endIndex = Math.min(routeSize - 2, endIndex);

        Coordinate startCoordinate = route.get(startIndex);
        Coordinate endCoordinate = route.get(endIndex);

        return new AnchorPoint(startIndex, endIndex, startCoordinate, endCoordinate);
    }

    /**
     * Walks along the route in the specified direction (step = ±1)
     * until we have travelled MIN_ANCHOR_DISTANCE_METERS and
     * the local curvature (angle between segments) is below threshold.
     */
    private int walkUntilStable(List<Coordinate> route, int startIndex, int step, double minAnchorDistance) {
        int index = startIndex;
        int size = route.size();
        final double maxTurnAngle = 20.0;

        double totalDist = 0.0;

        // Calculate initial direction
        Coordinate previous = route.get(Math.max(0, Math.min(size - 1, index)));
        Coordinate current = route.get(Math.max(0, Math.min(size - 1, index + step)));

        while (index + step >= 0 && index + step < size - 1) {
            // Move one step
            Coordinate next = route.get(index + step);
            totalDist += haversine(current, next);

            // Check curvature
            double angle = turnAngle(previous, current, next); // in degrees

            if (totalDist >= minAnchorDistance && angle <= maxTurnAngle) {
                break;
            }

            // Continue walking
            previous = current;
            current = next;
            index += step;
        }

        return index;
    }

    /**
     * Computes the angle between segments (prev->curr) and (curr->next).
     * Returns angle in degrees.
     */
    private double turnAngle(Coordinate prev, Coordinate curr, Coordinate next) {
        // Convert to vectors in meters (local projection)
        double[] v1 = vectorMeters(prev, curr);
        double[] v2 = vectorMeters(curr, next);

        double dot = v1[0] * v2[0] + v1[1] * v2[1];
        double mag1 = Math.hypot(v1[0], v1[1]);
        double mag2 = Math.hypot(v2[0], v2[1]);

        if (mag1 == 0 || mag2 == 0) return 0;

        double cos = dot / (mag1 * mag2);
        cos = Math.max(-1, Math.min(1, cos)); // clamp
        return Math.toDegrees(Math.acos(cos));
    }

    /**
     * Convert lat/lon delta to local meter coordinates for vector math.
     */
    private double[] vectorMeters(Coordinate a, Coordinate b) {
        double phi = Math.toRadians((a.getLatitude() + b.getLatitude()) / 2.0);
        double dx = EARTH_RADIUS_METERS * Math.toRadians(b.getLongitude() - a.getLongitude()) * Math.cos(phi);
        double dy = EARTH_RADIUS_METERS * Math.toRadians(b.getLatitude() - a.getLatitude());
        return new double[]{dx, dy};
    }

    /**
     * Removes duplicate endpoints from the detour segment if they coincide with
     * the anchor points (within ~1m).
     */
    private List<Coordinate> trimDetour(List<Coordinate> detour, Coordinate anchorStart, Coordinate anchorEnd) throws ValidationException {
        validator.validateRouteLength(detour);

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


    public List<Coordinate> gpxToPolyline(String pathname) throws IOException {
        GPX gpx = GPX.read(Path.of(pathname));
        List<WayPoint> points = gpx.tracks().flatMap(t -> t.segments()).flatMap(s -> s.points()).toList();
        List<Coordinate> coordinates = points.stream().map(p -> new Coordinate(p.getLatitude().doubleValue(), p.getLongitude().doubleValue())).toList();
        LOGGER.trace("first coordinate: {}", coordinates.getFirst());
        return coordinates;
    }


    public void createGpx(List<Coordinate> coordinates, String pathname) throws IOException {
        GPX gpx = GPX.builder().addTrack(track -> track.addSegment(segment -> coordinates.forEach(c -> segment.addPoint(WayPoint.of(c.getLatitude(), c.getLongitude()))))).build();

        GPX.write(gpx, Path.of(pathname));
    }
}


