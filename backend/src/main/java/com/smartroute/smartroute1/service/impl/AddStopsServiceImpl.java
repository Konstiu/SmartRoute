package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonDto;
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
    private static final double MAX_DISTANCE_FROM_ROUTE_METERS = 2000.0; // 1 km

    private final AddStopsValidator validator;
    private final OpenRouteServiceService orsService;

    // Protected waypoint indices for the current operation. Always contains the original endpoints and all inserted points.
    private final NavigableSet<Integer> protectedIndices = new TreeSet<>();

    private record ClosestPointResult(int segmentIndex, GeoJsonPosition closestPoint, double distanceMeters,
                                      double totalLengthRoute) {
    }

    private record AnchorPoint(int startIndex, int endIndex, GeoJsonPosition startCoordinate,
                               GeoJsonPosition endCoordinate) {
    }

    @Override
    public List<GeoJsonPosition> routeThroughPoint(GeoJsonPosition start, GeoJsonPosition via, GeoJsonPosition end) throws ValidationException {

        validator.validateCoordinates(start.getLatitude(), start.getLongitude());
        validator.validateCoordinates(via.getLatitude(), via.getLongitude());
        validator.validateCoordinates(end.getLatitude(), end.getLongitude());

        // Compute the forward path start to via
        GeoJsonDto forwardDto = orsService.generateRoute(List.of(start, via));
        List<GeoJsonPosition> forwardPath = extractPolyline(forwardDto);

        if (forwardPath.isEmpty()) {
            throw new ValidationException("ORS failed to generate forward path for detour.");
        }

        // Build a narrow avoid polygon around the forward path
        List<List<Double>> avoidPolygon = buildAvoidPolygon(forwardPath, 12.0); // 12m buffer

        // Compute the return path via to end, avoiding the forward corridor
        GeoJsonDto returnDto = orsService.generateRouteAvoidingPolygon(List.of(via, end), avoidPolygon);
        List<GeoJsonPosition> returnPath = extractPolyline(returnDto);

        // fallback: allow ORS to route without avoidance
        if (returnPath.isEmpty()) {
            returnPath = extractPolyline(orsService.generateRoute(List.of(via, end)));
        }

        // Combine: forwardPath + returnPath (trim duplicate via point)
        List<GeoJsonPosition> result = new ArrayList<>(forwardPath);
        result.addAll(returnPath.subList(1, returnPath.size()));

        return result;
    }

    private List<List<Double>> buildAvoidPolygon(List<GeoJsonPosition> path, double bufferMeters) {
        List<List<Double>> poly = new ArrayList<>();

        for (GeoJsonPosition p : path) {
            double lat = p.getLatitude();
            double lon = p.getLongitude();

            double dlat = bufferMeters / 111_320.0;
            double dlon = bufferMeters / (111_320.0 * Math.cos(Math.toRadians(lat)));

            // Upper offset
            poly.add(List.of(lon + dlon, lat + dlat));
            // Lower offset
            poly.add(List.of(lon - dlon, lat - dlat));
        }

        // Close polygon
        poly.add(poly.getFirst());
        return poly;
    }

    private List<GeoJsonPosition> cleanRoute(List<GeoJsonPosition> route) {
        List<GeoJsonPosition> cleaned = removeStubs(route);
        cleaned = removeUturns(cleaned);
        cleaned = removeMicroLoops(cleaned);
        cleaned = removeZigZags(cleaned);

        return cleaned;
    }

    private List<GeoJsonPosition> removeStubs(List<GeoJsonPosition> route) {
        if (route.size() < 3) {
            return route;
        }

        List<GeoJsonPosition> cleaned = new ArrayList<>();
        cleaned.add(route.getFirst());

        for (int i = 1; i < route.size() - 1; i++) {
            GeoJsonPosition prev = cleaned.getLast();
            GeoJsonPosition curr = route.get(i);
            GeoJsonPosition next = route.get(i + 1);

            double straight = haversine(prev, next);
            double detour = haversine(prev, curr);

            // stub = far from prev but ends up at same place
            if (straight < 12 && detour > 20) {
                continue; // remove the stub midpoint
            }

            cleaned.add(curr);
        }

        cleaned.add(route.getLast());
        return cleaned;
    }

    private List<GeoJsonPosition> removeMicroLoops(List<GeoJsonPosition> route) {
        if (route.size() < 4) {
            return route;
        }

        List<GeoJsonPosition> cleaned = new ArrayList<>();
        cleaned.add(route.getFirst());

        for (int i = 1; i < route.size() - 2; i++) {
            GeoJsonPosition a = cleaned.getLast();
            GeoJsonPosition b = route.get(i);
            GeoJsonPosition c = route.get(i + 1);

            // If c nearly equals a, then b is a pointless micro-loop
            if (haversine(a, c) < 15 && haversine(a, b) > 10) {
                continue; // remove b
            }

            cleaned.add(b);
        }

        cleaned.add(route.getLast());
        return cleaned;
    }

    private List<GeoJsonPosition> removeUturns(List<GeoJsonPosition> route) {
        if (route.size() < 3) {
            return route;
        }

        List<GeoJsonPosition> cleaned = new ArrayList<>();
        cleaned.add(route.getFirst());

        for (int i = 1; i < route.size() - 1; i++) {
            GeoJsonPosition prev = cleaned.getLast();
            GeoJsonPosition curr = route.get(i);
            GeoJsonPosition next = route.get(i + 1);

            double angle = turnAngle(prev, curr, next);

            if (angle > 150) {
                // sharp U-turn -> remove curr
                continue;
            }

            cleaned.add(curr);
        }

        cleaned.add(route.getLast());
        return cleaned;
    }

    private List<GeoJsonPosition> removeZigZags(List<GeoJsonPosition> route) {
        if (route.size() < 3) {
            return route;
        }

        List<GeoJsonPosition> cleaned = new ArrayList<>();
        cleaned.add(route.getFirst());

        for (int i = 1; i < route.size() - 1; i++) {
            GeoJsonPosition prev = cleaned.getLast();
            GeoJsonPosition curr = route.get(i);
            GeoJsonPosition next = route.get(i + 1);

            double angle = turnAngle(prev, curr, next);

            if (angle < 15) {
                // very sharp turns -> remove curr
                continue;
            }

            cleaned.add(curr);
        }

        cleaned.add(route.getLast());
        return cleaned;
    }

    private List<GeoJsonPosition> extractPolyline(GeoJsonDto dto) {
        if (dto == null || dto.getFeatures() == null || dto.getFeatures().isEmpty()) {
            return List.of();
        }

        List<GeoJsonPosition> coords = dto.getFeatures().getFirst().getGeometry().getCoordinates();

        List<GeoJsonPosition> result = new ArrayList<>();

        for (GeoJsonPosition c : coords) {
            double lon = c.getLatitude(); // ORS format: [lon, lat]
            double lat = c.getLongitude();

            // Convert to lat long format
            result.add(new GeoJsonPosition(lat, lon, c.getAltitude()));
        }

        return result;
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

        ClosestPointResult closest = findClosestPoint(route, newPoint);
        validator.validateMaxDetourDistance(MAX_DISTANCE_FROM_ROUTE_METERS, closest.distanceMeters);

        // Find protected boundaries for this insertion
        int prevProtected = findPreviousProtectedIndex(closest.segmentIndex);
        int nextProtected = findNextProtectedIndex(closest.segmentIndex);

        AnchorPoint anchors = chooseAnchorPoints(route, closest, prevProtected, nextProtected);

        List<GeoJsonPosition> detour = routeThroughPoint(anchors.startCoordinate, newPoint, anchors.endCoordinate);

        // Before start anchor
        List<GeoJsonPosition> result = new ArrayList<>(route.subList(0, anchors.startIndex + 1));

        // Insert trimmed detour
        List<GeoJsonPosition> trimmedDetour = trimDetour(detour, anchors.startCoordinate, anchors.endCoordinate);
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

    // Picks anchor points where the route should leave and rejoin. * Uses curvature + minimum distance to select natural anchor points.
    private AnchorPoint chooseAnchorPoints(List<GeoJsonPosition> route, ClosestPointResult closest, int prevProtectedIndex, int nextProtectedIndex) {
        int index = closest.segmentIndex;
        double routeLength = closest.totalLengthRoute;

        // Reasonable bound: allow larger detours only for long routes
        double minAnchorDistance = Math.min(closest.distanceMeters, routeLength / 4); // limit to half the original route
        //double minAnchorDistance = routeLength / 4;

        // Walk outward respecting curvature
        int startIndex = walkUntilStable(route, index, -1, minAnchorDistance);
        startIndex = Math.max(prevProtectedIndex, startIndex);

        int endIndex = walkUntilStable(route, index + 1, +1, minAnchorDistance);
        endIndex = Math.min(nextProtectedIndex, endIndex);

        // Clamp further to ensure endpoints are never removed
        startIndex = Math.max(1, startIndex);
        endIndex = Math.min(route.size() - 2, endIndex);

        return new AnchorPoint(startIndex, endIndex, route.get(startIndex), route.get(endIndex));
    }

    // Walks along the route in the specified direction (step = +/-1) * until we have travelled a minium distance and the local curvature (angle between segments) is below threshold.
    private int walkUntilStable(List<GeoJsonPosition> route, int startIndex, int step, double minAnchorDistance) {
        int index = startIndex;
        int size = route.size();
        final double maxTurnAngle = 20.0;

        double totalDist = 0.0;

        GeoJsonPosition previous = route.get(Math.max(0, Math.min(size - 1, index)));
        GeoJsonPosition current = route.get(Math.max(0, Math.min(size - 1, index + step)));

        while (index + step >= 0 && index + step < size - 1) {
            GeoJsonPosition next = route.get(index + step);
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
    private double turnAngle(GeoJsonPosition prev, GeoJsonPosition curr, GeoJsonPosition next) {
        Coordinate prevCord = new Coordinate(prev.getLatitude(), prev.getLongitude());
        Coordinate currentCord = new Coordinate(curr.getLatitude(), curr.getLongitude());
        Coordinate nextCord = new Coordinate(next.getLatitude(), next.getLongitude());

        double[] v1 = vectorMeters(prevCord, currentCord);
        double[] v2 = vectorMeters(currentCord, nextCord);

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
    private List<GeoJsonPosition> trimDetour(List<GeoJsonPosition> detour, GeoJsonPosition anchorStart, GeoJsonPosition anchorEnd)
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

    // Haversine distance in meters between two lat/lon points.
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

    // Project point P onto segment AB (in lat/lon space, approximated as 2D).
    private static GeoJsonPosition projectPointToSegment(GeoJsonPosition p, GeoJsonPosition a, GeoJsonPosition b) {
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

        return new GeoJsonPosition(Math.toDegrees(latProj), Math.toDegrees(lonProj), 0.0); // altitude is not important here
    }

    public List<GeoJsonPosition> reshape(List<GeoJsonPosition> originalRoute, List<GeoJsonPosition> newPoints, double toleranceFactor) throws ValidationException {
        if (originalRoute == null || originalRoute.size() < 2) {
            throw new ValidationException("Original route must have at least 2 points.");
        }

        final int roundness = 10;
        final int seed = 1;

        List<GeoJsonPosition> required = new ArrayList<>();
        required.add(originalRoute.getFirst());
        required.addAll(newPoints);
        required.add(originalRoute.getLast());

        GeoJsonDto baselineDto = orsService.generateRoute(required);
        List<GeoJsonPosition> baseline = extractPolyline(baselineDto);

        double baselineLength = computeLength(baseline);
        double originalLength = computeLength(originalRoute);
        double baseStitchedLength = computeLength(addWaypoints(originalRoute, newPoints));
        double diff = baseStitchedLength - originalLength;

        final double minAllowed = originalLength * (1 - toleranceFactor);
        final double maxAllowed = originalLength * (1 + toleranceFactor);

        if (baselineLength > maxAllowed) {
            LOGGER.warn(
                    "Via points are too far apart or too far from the original route. "
                            + "Baseline length {} exceeds allowed maximum {}. Falling back to simple stitched route.",
                    baselineLength, maxAllowed
            );

            // Fallback: route with points inserted but no reshape
            return addWaypoints(originalRoute, newPoints);
        }

        final double minLoop = 400; // needed for ORS stability
        GeoJsonPosition loopCenter = computeCentroid(required);

        // binary search
        double targetMin = minAllowed;
        double targetMax = maxAllowed;

        double low = Math.max(minLoop, (originalLength - diff) * 0.5);
        double high = (originalLength - diff) * (1 + toleranceFactor) * 2;

        final int maxIterations = 3;

        List<GeoJsonPosition> bestRoute = null;
        double bestError = Double.MAX_VALUE;

        List<GeoJsonPosition> candidate = new ArrayList<>();

        for (int i = 0; i < maxIterations; i++) {
            double requested = (low + high) / 2.0;

            GeoJsonDto dto = orsService.generateRoundTrip(List.of(loopCenter), (int) requested, roundness, seed);

            List<GeoJsonPosition> loop = extractPolyline(dto);
            candidate = addWaypoints(loop, required);
            double length = computeLength(candidate);

            double error = Math.abs(length - originalLength);

            // keep best attempt
            if (error < bestError) {
                bestError = error;
                bestRoute = candidate;
            }

            if (length < targetMin) {
                low = requested; // need longer route
            } else if (length > targetMax) {
                high = requested; // need shorter route
            } else {
                break; // success
            }
        }
        return cleanRoute(candidate);
    }

    private GeoJsonPosition midpointOfRoute(List<GeoJsonPosition> route) {
        int midIndex = route.size() / 2;
        return route.get(midIndex);
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

}
