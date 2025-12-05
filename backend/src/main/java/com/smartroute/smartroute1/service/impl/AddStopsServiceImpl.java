package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.service.AddStopsService;
import com.smartroute.smartroute1.service.validators.AddStopsValidator;
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
import java.util.NavigableSet;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class AddStopsServiceImpl implements AddStopsService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final double EARTH_RADIUS_METERS = 6371000.0;

    private final AddStopsValidator validator;

    // Protected waypoint indices for the current operation. Always contains the original endpoints and all inserted points.
    private final NavigableSet<Integer> protectedIndices = new TreeSet<>();

    private record ClosestPointResult(int segmentIndex, Coordinate closestPoint, double distanceMeters,
                                      double totalLengthRoute) {
    }

    private record AnchorPoint(int startIndex, int endIndex, Coordinate startCoordinate, Coordinate endCoordinate) {
    }

    @Override
    public List<Coordinate> routeThroughPoint(Coordinate start, Coordinate via, Coordinate end) throws ValidationException {
        validator.validateCoordinates(start.getLatitude(), start.getLongitude());
        validator.validateCoordinates(via.getLatitude(), via.getLongitude());
        validator.validateCoordinates(end.getLatitude(), end.getLongitude());

        List<Coordinate> result = new ArrayList<>();
        result.add(start);
        result.add(via);
        result.add(end);
        return result;
    }

    @Override
    public List<Coordinate> addWaypoints(List<Coordinate> originalRoute, List<Coordinate> newPoints)
            throws ValidationException {

        validator.validateRouteLength(originalRoute);

        if (newPoints == null || newPoints.isEmpty()) {
            return originalRoute;
        }

        // Reset + initialize protected indices
        protectedIndices.clear();
        protectedIndices.add(0);
        protectedIndices.add(originalRoute.size() - 1);

        List<Coordinate> updatedRoute = new ArrayList<>(originalRoute);

        for (Coordinate newPoint : newPoints) {
            validator.validateCoordinates(newPoint.getLatitude(), newPoint.getLongitude());
            updatedRoute = addSingleWaypoint(updatedRoute, newPoint);
        }

        validator.validateSameEndpoints(originalRoute, updatedRoute);

        return updatedRoute;
    }

    private List<Coordinate> addSingleWaypoint(List<Coordinate> route, Coordinate newPoint)
            throws ValidationException {

        ClosestPointResult closest = findClosestPoint(route, newPoint);

        // Find protected boundaries for this insertion
        int prevProtected = findPreviousProtectedIndex(closest.segmentIndex);
        int nextProtected = findNextProtectedIndex(closest.segmentIndex);

        AnchorPoint anchors = chooseAnchorPoints(route, closest, prevProtected, nextProtected);

        List<Coordinate> detour = routeThroughPoint(
                anchors.startCoordinate,
                newPoint,
                anchors.endCoordinate
        );

        List<Coordinate> result = new ArrayList<>();

        // Before start anchor
        result.addAll(route.subList(0, anchors.startIndex + 1));

        // Insert trimmed detour
        List<Coordinate> trimmedDetour = trimDetour(detour, anchors.startCoordinate, anchors.endCoordinate);
        result.addAll(trimmedDetour);

        // After end anchor
        result.addAll(route.subList(anchors.endIndex, route.size()));

        // new protected waypoint
        // Its new index = startIndex + size of trimmed detour
        int newIndex = anchors.startIndex + trimmedDetour.size();
        protectedIndices.add(newIndex);

        return result;
    }

    private int findPreviousProtectedIndex(int index) {
        Integer floor = protectedIndices.floor(index);
        return floor != null ? floor : 0;
    }

    private int findNextProtectedIndex(int index) {
        Integer ceil = protectedIndices.ceiling(index + 1);
        return ceil != null ? ceil : index + 1;
    }

    // Find the closest segment of the polyline to the new point and derive anchor points where the old route should "exit" and "rejoin".
    private ClosestPointResult findClosestPoint(List<Coordinate> polyline, Coordinate newPoint)
            throws ValidationException {

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

    // Picks anchor points where the route should leave and rejoin. * Uses curvature + minimum distance to select natural anchor points.
    private AnchorPoint chooseAnchorPoints(List<Coordinate> route, ClosestPointResult closest, int prevProtectedIndex, int nextProtectedIndex) {
        int index = closest.segmentIndex;
        double routeLength = closest.totalLengthRoute;

        // Reasonable bound: allow larger detours only for long routes
        double minAnchorDistance = Math.min(closest.distanceMeters / 2, routeLength / 4);

        // Walk outward respecting curvature
        int startIndex = walkUntilStable(route, index, -1, minAnchorDistance);
        startIndex = Math.max(prevProtectedIndex, startIndex);

        int endIndex = walkUntilStable(route, index + 1, +1, minAnchorDistance);
        endIndex = Math.min(nextProtectedIndex, endIndex);

        // Clamp further to ensure endpoints are never removed
        startIndex = Math.max(1, startIndex);
        endIndex = Math.min(route.size() - 2, endIndex);

        return new AnchorPoint(
                startIndex,
                endIndex,
                route.get(startIndex),
                route.get(endIndex)
        );
    }

    // Walks along the route in the specified direction (step = +/-1) * until we have travelled a minium distance and the local curvature (angle between segments) is below threshold.
    private int walkUntilStable(List<Coordinate> route, int startIndex, int step, double minAnchorDistance) {
        int index = startIndex;
        int size = route.size();
        final double maxTurnAngle = 20.0;

        double totalDist = 0.0;

        Coordinate previous = route.get(Math.max(0, Math.min(size - 1, index)));
        Coordinate current = route.get(Math.max(0, Math.min(size - 1, index + step)));

        while (index + step >= 0 && index + step < size - 1) {
            Coordinate next = route.get(index + step);
            totalDist += haversine(current, next);

            double angle = turnAngle(previous, current, next);

            if (totalDist >= minAnchorDistance && angle <= maxTurnAngle) {
                break;
            }

            previous = current;
            current = next;
            index += step;
        }

        return index;
    }

    //Computes the angle between segments (prev->curr) and (curr->next). Returns angle in degrees.
    private double turnAngle(Coordinate prev, Coordinate curr, Coordinate next) {
        double[] v1 = vectorMeters(prev, curr);
        double[] v2 = vectorMeters(curr, next);

        double dot = v1[0] * v2[0] + v1[1] * v2[1];
        double m1 = Math.hypot(v1[0], v1[1]);
        double m2 = Math.hypot(v2[0], v2[1]);

        if (m1 == 0 || m2 == 0) {
            return 0;
        }

        double cos = dot / (m1 * m2);
        cos = Math.max(-1, Math.min(1, cos));

        return Math.toDegrees(Math.acos(cos));
    }

    // Convert lat/lon delta to local meter coordinates for vector math.
    private double[] vectorMeters(Coordinate a, Coordinate b) {
        double phi = Math.toRadians((a.getLatitude() + b.getLatitude()) / 2.0);
        double dx = EARTH_RADIUS_METERS * Math.toRadians(b.getLongitude() - a.getLongitude()) * Math.cos(phi);
        double dy = EARTH_RADIUS_METERS * Math.toRadians(b.getLatitude() - a.getLatitude());
        return new double[]{dx, dy};
    }

    // Removes duplicate endpoints from the detour segment if they coincide with the anchor points (within 1m).
    private List<Coordinate> trimDetour(List<Coordinate> detour, Coordinate anchorStart, Coordinate anchorEnd)
            throws ValidationException {

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
            return detour;
        }

        return new ArrayList<>(detour.subList(startIdx, endIdx + 1));
    }

    @Override
    public List<Coordinate> gpxToPolyline(String pathname) throws IOException {
        GPX gpx = GPX.read(Path.of(pathname));
        List<WayPoint> points = gpx.tracks()
                .flatMap(t -> t.segments())
                .flatMap(s -> s.points())
                .toList();

        List<Coordinate> coords = points.stream()
                .map(p -> new Coordinate(p.getLatitude().doubleValue(), p.getLongitude().doubleValue()))
                .toList();

        LOGGER.trace("first coordinate: {}", coords.getFirst());
        return coords;
    }

    @Override
    public void createGpx(List<Coordinate> coordinates, String pathname) throws IOException {
        GPX gpx = GPX.builder()
                .addTrack(track -> track.addSegment(seg ->
                        coordinates.forEach(c -> seg.addPoint(WayPoint.of(c.getLatitude(), c.getLongitude())))
                ))
                .build();

        GPX.write(gpx, Path.of(pathname));
    }

    // Haversine distance in meters between two lat/lon points.
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

    // Project point P onto segment AB (in lat/lon space, approximated as 2D).
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
        t = Math.max(0, Math.min(1, t));

        double projX = x1 + t * dx;
        double projY = y1 + t * dy;

        double latProj = projY / EARTH_RADIUS_METERS;
        double lonProj = projX / (EARTH_RADIUS_METERS * Math.cos(phi0));

        return new Coordinate(Math.toDegrees(latProj), Math.toDegrees(lonProj));
    }
}
