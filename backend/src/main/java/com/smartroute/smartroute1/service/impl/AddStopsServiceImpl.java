package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonGeometryLineString;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.service.AddStopsService;
import com.smartroute.smartroute1.service.OpenRouteServiceService;
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
    private static final double MAX_DISTANCE_FROM_ROUTE_METERS = 2000.0;

    private final AddStopsValidator validator;
    private final OpenRouteServiceService orsService;

    // Protected waypoint indices for the current operation
    private final NavigableSet<Integer> protectedIndices = new TreeSet<>();

    private record ClosestPointResult(int segmentIndex, GeoJsonPosition closestPoint, double distanceMeters,
                                      double totalLengthRoute) {
    }

    private record AnchorPoint(int startIndex, int endIndex, GeoJsonPosition startCoordinate,
                               GeoJsonPosition endCoordinate) {
    }

    @Override
    public List<GeoJsonPosition> routeThroughPoint(GeoJsonPosition start, GeoJsonPosition via, GeoJsonPosition end)
            throws ValidationException {

        validator.validateCoordinates(start.getLatitude(), start.getLongitude());
        validator.validateCoordinates(via.getLatitude(), via.getLongitude());
        validator.validateCoordinates(end.getLatitude(), end.getLongitude());

        // Compute the forward path start to via
        GeoJsonDto forwardDto = orsService.generateRoute(List.of(start, via));
        List<GeoJsonPosition> forwardPath = forwardDto.getFeatures().getFirst().getGeometry().getCoordinates();

        if (forwardPath.isEmpty()) {
            throw new ValidationException("ORS failed to generate forward path for detour.");
        }

        List<GeoJsonPosition> returnPath = null;

        // Try with avoid polygon first (narrower buffer)
        try {
            List<List<Double>> avoidPolygon = buildAvoidPolygon(forwardPath, 12.0);
            GeoJsonDto returnDto = orsService.generateRouteAvoidingPolygon(List.of(via, end), avoidPolygon);
            returnPath = returnDto.getFeatures().getFirst().getGeometry().getCoordinates();
            LOGGER.debug("Successfully routed return path with avoid polygon");
        } catch (Exception e) {
            LOGGER.info("Avoid polygon too restrictive, falling back to direct route: {}", e.getMessage());
        }

        // Fallback: route without avoidance
        if (returnPath == null || returnPath.isEmpty()) {
            LOGGER.info("Using direct route for return path (no avoidance)");
            GeoJsonDto fallbackDto = orsService.generateRoute(List.of(via, end));
            returnPath = fallbackDto.getFeatures().getFirst().getGeometry().getCoordinates();
        }

        // Combine: forwardPath + returnPath (trim duplicate via point)
        List<GeoJsonPosition> result = new ArrayList<>(forwardPath);
        result.addAll(returnPath.subList(1, returnPath.size()));

        LOGGER.debug("Combined detour: {} points total", result.size());
        return result;
    }

    private List<List<Double>> buildAvoidPolygon(List<GeoJsonPosition> path, double bufferMeters) {
        List<List<Double>> poly = new ArrayList<>();

        for (GeoJsonPosition p : path) {
            double lat = p.getLatitude();
            double lon = p.getLongitude();

            double dlat = bufferMeters / 111_320.0;
            double dlon = bufferMeters / (111_320.0 * Math.cos(Math.toRadians(lat)));

            poly.add(List.of(lon + dlon, lat + dlat));
            poly.add(List.of(lon - dlon, lat - dlat));
        }

        poly.add(poly.getFirst());
        return poly;
    }

    @Override
    public List<GeoJsonPosition> addWaypoints(List<GeoJsonPosition> originalRoute, List<GeoJsonPosition> newPoints)
            throws ValidationException {

        validator.validateRouteLength(originalRoute);

        if (newPoints == null || newPoints.isEmpty()) {
            return originalRoute;
        }

        // Reset + initialize protected indices
        protectedIndices.clear();
        protectedIndices.add(0);
        protectedIndices.add(originalRoute.size() - 1);

        List<GeoJsonPosition> updatedRoute = new ArrayList<>(originalRoute);

        for (GeoJsonPosition newPoint : newPoints) {
            validator.validateCoordinates(newPoint.getLatitude(), newPoint.getLongitude());
            updatedRoute = addSingleWaypoint(updatedRoute, newPoint);
        }

        validator.validateSameEndpoints(originalRoute, updatedRoute);

        return updatedRoute;
    }

    private List<GeoJsonPosition> addSingleWaypoint(List<GeoJsonPosition> route, GeoJsonPosition newPoint)
            throws ValidationException {

        // Find the closest point on the route to the waypoint
        ClosestPointResult closest = findClosestPoint(route, newPoint);
        validator.validateMaxDetourDistance(MAX_DISTANCE_FROM_ROUTE_METERS, closest.distanceMeters);

        LOGGER.info("Adding waypoint at lat:{}, lon:{} ({}m from route at segment {})",
                newPoint.getLatitude(), newPoint.getLongitude(),
                closest.distanceMeters, closest.segmentIndex);

        // Find protected boundaries
        int prevProtected = findPreviousProtectedIndex(closest.segmentIndex);
        int nextProtected = findNextProtectedIndex(closest.segmentIndex);

        // Find exit and rejoin points
        AnchorPoint anchors = findExitAndRejoinPoints(route, closest.segmentIndex,
                prevProtected, nextProtected);

        double skippedDistance = calculateSegmentDistance(route, anchors.startIndex, anchors.endIndex);
        LOGGER.info("Exit at index {} (lat:{}, lon:{})",
                anchors.startIndex, anchors.startCoordinate.getLatitude(),
                anchors.startCoordinate.getLongitude());
        LOGGER.info("Rejoin at index {} (lat:{}, lon:{}) - skipping {}m of original route",
                anchors.endIndex, anchors.endCoordinate.getLatitude(),
                anchors.endCoordinate.getLongitude(), skippedDistance);

        // Generate detour: exit → waypoint → rejoin
        List<GeoJsonPosition> detour = routeThroughPoint(
                anchors.startCoordinate,
                newPoint,
                anchors.endCoordinate
        );

        double detourDist = computeLength(detour);
        LOGGER.info("Detour route: {} points, {}m total (vs {}m skipped, net change: {}m)",
                detour.size(), detourDist, skippedDistance, detourDist - skippedDistance);

        // Sanity check: if detour is more than 3x the skipped distance, something is wrong
        if (detourDist > skippedDistance * 3.0 && skippedDistance > 100) {
            LOGGER.warn("Detour is {}x longer than skipped segment! This seems wrong.", detourDist / skippedDistance);
            LOGGER.warn("Waypoint: lat={}, lon={}", newPoint.getLatitude(), newPoint.getLongitude());
            LOGGER.warn("Exit: lat={}, lon={}", anchors.startCoordinate.getLatitude(), anchors.startCoordinate.getLongitude());
            LOGGER.warn("Rejoin: lat={}, lon={}", anchors.endCoordinate.getLatitude(), anchors.endCoordinate.getLongitude());
        }

        // Build new route by replacing the skipped segment with the detour
        List<GeoJsonPosition> result = new ArrayList<>();

        // Part 1: Original route UP TO the exit point (not including it)
        for (int i = 0; i < anchors.startIndex; i++) {
            result.add(route.get(i));
        }
        int beforeSize = result.size();

        // Part 2: Detour (exit → waypoint → rejoin)
        result.addAll(detour);

        // Part 3: Original route AFTER the rejoin point (not including it)
        for (int i = anchors.endIndex + 1; i < route.size(); i++) {
            result.add(route.get(i));
        }

        LOGGER.info("New route composition: {} (before exit) + {} (detour) + {} (after rejoin) = {} total (was {})",
                beforeSize, detour.size(), route.size() - anchors.endIndex - 1,
                result.size(), route.size());

        // Verify splice points are continuous
        checkSpliceContinuity(result, beforeSize, beforeSize + detour.size());

        // Update protected indices
        updateProtectedIndices(anchors, detour.size(), newPoint, detour);

        return result;
    }

    private AnchorPoint findExitAndRejoinPoints(List<GeoJsonPosition> route, int closestSegment,
                                                int prevProtected, int nextProtected) {
        // Exit point: try to go ~100m BEFORE the closest point
        int exitIdx = closestSegment;
        double distBack = 0.0;
        double targetBackDist = 100.0;

        while (exitIdx > prevProtected && distBack < targetBackDist && exitIdx > 0) {
            distBack += haversine(route.get(exitIdx), route.get(exitIdx - 1));
            exitIdx--;
        }

        // Rejoin point: go ~500m FORWARD from exit point
        int rejoinIdx = exitIdx;
        double distForward = 0.0;
        double targetForwardDist = 500.0;

        while (rejoinIdx < nextProtected && distForward < targetForwardDist && rejoinIdx < route.size() - 1) {
            distForward += haversine(route.get(rejoinIdx), route.get(rejoinIdx + 1));
            rejoinIdx++;
        }

        LOGGER.info("Exit/Rejoin points: {}m back to index {}, then {}m forward to index {}",
                distBack, exitIdx, distForward, rejoinIdx);

        return new AnchorPoint(
                exitIdx,
                rejoinIdx,
                route.get(exitIdx),
                route.get(rejoinIdx)
        );
    }

    private void updateProtectedIndices(AnchorPoint anchors, int detourSize,
                                        GeoJsonPosition newPoint, List<GeoJsonPosition> detour) {
        int removedCount = anchors.endIndex - anchors.startIndex + 1;
        int delta = detourSize - removedCount;

        LOGGER.info("Index update: removed {} points, added {} points, delta: {}",
                removedCount, detourSize, delta);

        NavigableSet<Integer> updatedIndices = new TreeSet<>();
        for (Integer idx : protectedIndices) {
            if (idx < anchors.startIndex) {
                updatedIndices.add(idx);
            } else if (idx > anchors.endIndex) {
                updatedIndices.add(idx + delta);
            }
        }
        protectedIndices.clear();
        protectedIndices.addAll(updatedIndices);

        int waypointIdx = findClosestIndexInRoute(detour, newPoint);
        int newProtectedIdx = anchors.startIndex + waypointIdx;
        protectedIndices.add(newProtectedIdx);

        LOGGER.info("Protected waypoint at index {}, all protected: {}",
                newProtectedIdx, protectedIndices);
    }

    private double calculateSegmentDistance(List<GeoJsonPosition> route, int start, int end) {
        double total = 0;
        for (int i = start; i < Math.min(end, route.size() - 1); i++) {
            total += haversine(route.get(i), route.get(i + 1));
        }
        return total;
    }

    private void checkSpliceContinuity(List<GeoJsonPosition> route, int splice1, int splice2) {
        if (splice1 > 0 && splice1 < route.size()) {
            double gap = haversine(route.get(splice1 - 1), route.get(splice1));
            LOGGER.info("Gap at exit point: {}m", gap);
            if (gap > 500) {
                LOGGER.warn("LARGE GAP at exit: {}m", gap);
            }
        }

        if (splice2 > 0 && splice2 < route.size()) {
            double gap = haversine(route.get(splice2 - 1), route.get(splice2));
            LOGGER.info("Gap at rejoin point: {}m", gap);
            if (gap > 500) {
                LOGGER.warn("LARGE GAP at rejoin: {}m", gap);
            }
        }
    }

    private int findClosestIndexInRoute(List<GeoJsonPosition> route, GeoJsonPosition point) {
        int bestIdx = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            double dist = haversine(route.get(i), point);
            if (dist < bestDist) {
                bestDist = dist;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private int findPreviousProtectedIndex(int index) {
        Integer floor = protectedIndices.floor(index);
        return floor != null ? floor : 0;
    }

    private int findNextProtectedIndex(int index) {
        Integer ceil = protectedIndices.ceiling(index + 1);
        return ceil != null ? ceil : index + 1;
    }

    private ClosestPointResult findClosestPoint(List<GeoJsonPosition> polyline, GeoJsonPosition newPoint)
            throws ValidationException {

        validator.validateRouteLength(polyline);

        int bestIndex = -1;
        GeoJsonPosition bestPoint = null;
        double bestDist = Double.MAX_VALUE;
        double routeLength = 0.0;

        for (int i = 0; i < polyline.size() - 1; i++) {
            GeoJsonPosition a = polyline.get(i);
            GeoJsonPosition b = polyline.get(i + 1);

            routeLength += haversine(a, b);

            GeoJsonPosition projection = projectPointToSegment(newPoint, a, b);
            double dist = haversine(newPoint, projection);

            if (dist < bestDist) {
                bestDist = dist;
                bestIndex = i;
                bestPoint = projection;
            }
        }

        return new ClosestPointResult(bestIndex, bestPoint, bestDist, routeLength);
    }

    @Override
    public List<GeoJsonPosition> gpxToPolyline(String pathname) throws IOException {
        GPX gpx = GPX.read(Path.of(pathname));
        List<WayPoint> points = gpx.tracks()
                .flatMap(t -> t.segments())
                .flatMap(s -> s.points())
                .toList();

        List<GeoJsonPosition> coords = points.stream()
                .map(p -> new GeoJsonPosition(p.getLatitude().doubleValue(), p.getLongitude().doubleValue(), 0.0))
                .toList();

        LOGGER.trace("first coordinate: {}", coords.getFirst());
        return coords;
    }

    @Override
    public void createGpx(List<GeoJsonPosition> coordinates, String pathname) throws IOException {
        GPX gpx = GPX.builder()
                .addTrack(track -> track.addSegment(seg ->
                        coordinates.forEach(c -> seg.addPoint(WayPoint.of(c.getLatitude(), c.getLongitude())))
                ))
                .build();

        GPX.write(gpx, Path.of(pathname));
    }

    @Override
    public GeoJsonGeometryLineString createGeometryFromCoords(List<GeoJsonPosition> routeCoords) {
        GeoJsonGeometryLineString geometry = new GeoJsonGeometryLineString();
        geometry.setType("LineString");
        geometry.setCoordinates(routeCoords);
        return geometry;
    }

    @Override
    public double calculateTotalDistance(List<GeoJsonPosition> routeCoords) {
        return computeLength(routeCoords);
    }

    @Override
    public List<GeoJsonPosition> reshape(List<GeoJsonPosition> originalRoute, List<GeoJsonPosition> newPoints,
                                         double toleranceFactor) throws ValidationException {
        if (originalRoute == null || originalRoute.size() < 2) {
            throw new ValidationException("Original route must have at least 2 points.");
        }

        validator.validateToleranceFactor(toleranceFactor);

        List<GeoJsonPosition> required = new ArrayList<>();
        required.add(originalRoute.getFirst());
        required.addAll(newPoints);
        required.add(originalRoute.getLast());

        GeoJsonDto baselineDto = orsService.generateRoute(required);
        List<GeoJsonPosition> baseline = baselineDto.getFeatures().getFirst().getGeometry().getCoordinates();

        double baselineLength = computeLength(baseline);
        double originalLength = computeLength(originalRoute);

        final double minAllowed = originalLength * (1 - toleranceFactor);
        final double maxAllowed = originalLength * (1 + toleranceFactor);

        if (baselineLength > maxAllowed) {
            LOGGER.warn("Via points too far, using stitched route");
            return addWaypoints(originalRoute, newPoints);
        }

        return addWaypoints(originalRoute, newPoints);
    }

    private double computeLength(List<GeoJsonPosition> route) {
        if (route == null || route.size() < 2) {
            return 0.0;
        }

        double sum = 0.0;
        for (int i = 0; i < route.size() - 1; i++) {
            sum += haversine(route.get(i), route.get(i + 1));
        }
        return sum;
    }

    private GeoJsonPosition computeCentroid(List<GeoJsonPosition> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("Point list must not be empty");
        }

        double sumLat = 0.0;
        double sumLon = 0.0;

        for (GeoJsonPosition c : points) {
            sumLat += c.getLatitude();
            sumLon += c.getLongitude();
        }

        double latMid = sumLat / points.size();
        double lonMid = sumLon / points.size();

        return new GeoJsonPosition(latMid, lonMid, 0.0);
    }

    private static double haversine(GeoJsonPosition a, GeoJsonPosition b) {
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

    private static GeoJsonPosition projectPointToSegment(GeoJsonPosition p, GeoJsonPosition a, GeoJsonPosition b) {
        double lat1 = Math.toRadians(a.getLatitude());
        double lon1 = Math.toRadians(a.getLongitude());
        double lat2 = Math.toRadians(b.getLatitude());
        double lon2 = Math.toRadians(b.getLongitude());
        double latP = Math.toRadians(p.getLatitude());
        double lonP = Math.toRadians(p.getLongitude());

        double phi0 = (lat1 + lat2) / 2.0;

        double x1 = EARTH_RADIUS_METERS * lon1 * Math.cos(phi0);
        double y1 = EARTH_RADIUS_METERS * lat1;
        double x2 = EARTH_RADIUS_METERS * lon2 * Math.cos(phi0);
        double y2 = EARTH_RADIUS_METERS * lat2;
        double x3 = EARTH_RADIUS_METERS * lonP * Math.cos(phi0);
        double y3 = EARTH_RADIUS_METERS * latP;

        double dx = x2 - x1;
        double dy = y2 - y1;

        if (dx == 0 && dy == 0) {
            return a;
        }

        double t = ((x3 - x1) * dx + (y3 - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));

        double projX = x1 + t * dx;
        double projY = y1 + t * dy;

        double latProj = projY / EARTH_RADIUS_METERS;
        double lonProj = projX / (EARTH_RADIUS_METERS * Math.cos(phi0));

        return new GeoJsonPosition(Math.toDegrees(latProj), Math.toDegrees(lonProj), 0.0);
    }
}