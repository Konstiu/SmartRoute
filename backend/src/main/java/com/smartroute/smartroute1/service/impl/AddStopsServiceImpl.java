package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.AddStopsDto;
import com.smartroute.smartroute1.endpoint.dto.StopPointDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonFeature;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonGeometryLineString;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonProperties;
import com.smartroute.smartroute1.exception.RouteNotFoundException;
import com.smartroute.smartroute1.exception.StopTooFarFromRouteException;
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
    private static final double MAX_DISTANCE_FROM_ROUTE_METERS = 1000.0; // 1 km

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

    public List<GeoJsonPosition> routeThroughPoint(GeoJsonPosition start, GeoJsonPosition via, GeoJsonPosition end) throws ValidationException {

        validator.validateCoordinates(start.getLatitude(), start.getLongitude());
        validator.validateCoordinates(via.getLatitude(), via.getLongitude());
        validator.validateCoordinates(end.getLatitude(), end.getLongitude());

        // Compute the forward path start to via
        GeoJsonDto forwardDto = orsService.generateRoute(List.of(start, via));
        if (forwardDto == null
                || forwardDto.getFeatures() == null
                || forwardDto.getFeatures().isEmpty()
                || forwardDto.getFeatures().getFirst().getGeometry() == null
                || forwardDto.getFeatures().getFirst().getGeometry().getCoordinates() == null) {
            throw new RouteNotFoundException("No route could be generated for the selected location");
        }

        List<GeoJsonPosition> forwardPath = forwardDto.getFeatures().getFirst().getGeometry().getCoordinates();

        if (forwardPath.size() < 2) {
            throw new ValidationException("ORS returned forward path too short.");
        }

        // Build a narrow avoid polygon around the forward path
        List<List<Double>> avoidPolygon = buildAvoidPolygon(forwardPath, 12.0); // 12m buffer

        // Compute the return path via to end, avoiding the forward corridor
        GeoJsonDto returnDto = orsService.generateRouteAvoidingPolygon(List.of(via, end), avoidPolygon);
        // fallback
        if (returnDto == null) {
            returnDto = orsService.generateRoute(List.of(via, end));

            if (returnDto == null
                    || returnDto.getFeatures() == null
                    || returnDto.getFeatures().isEmpty()
                    || returnDto.getFeatures().getFirst().getGeometry() == null
                    || returnDto.getFeatures().getFirst().getGeometry().getCoordinates() == null) {
                throw new RouteNotFoundException("No route could be generated for the selected location");
            }
        }
        List<GeoJsonPosition> returnPath = returnDto.getFeatures().getFirst().getGeometry().getCoordinates();

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

    @Override
    public GeoJsonDto addWaypoints(AddStopsDto addStopsDto)
            throws ValidationException {

        List<GeoJsonPosition> originalRoute = toGeo(addStopsDto.getOriginalRoute());
        List<GeoJsonPosition> newPoints = toGeo(addStopsDto.getNewPoints());
        validator.validateRouteLength(originalRoute);

        if (newPoints == null || newPoints.isEmpty()) {
            return createGeoJsonDtoFromPolyline(originalRoute);
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

        return createGeoJsonDtoFromPolyline(updatedRoute);
    }

    private List<GeoJsonPosition> toGeo(List<StopPointDto> stopList) throws ValidationException {
        if (stopList == null || stopList.isEmpty()) {
            throw new ValidationException("StopList must have points.");
        }

        List<GeoJsonPosition> geoList = new ArrayList<>();
        for (StopPointDto p : stopList) {
            GeoJsonPosition g = new GeoJsonPosition(p.getLatitude(), p.getLongitude(), p.getAltitude());
            geoList.add(g);
        }

        return geoList;
    }

    public List<StopPointDto> toStopPoint(List<GeoJsonPosition> geoList) throws ValidationException {
        if (geoList == null || geoList.isEmpty()) {
            throw new ValidationException("GeoList must have points.");
        }

        List<StopPointDto> stopList = new ArrayList<>();
        for (GeoJsonPosition p : geoList) {
            StopPointDto g = new StopPointDto(p.getLatitude(), p.getLongitude(), p.getAltitude());
            stopList.add(g);
        }

        return stopList;
    }

    private GeoJsonDto createGeoJsonDtoFromPolyline(List<GeoJsonPosition> polyline) {
        GeoJsonDto dto = new GeoJsonDto();
        dto.setType("FeatureCollection");
        dto.setBbox(computeBbox(polyline));

        GeoJsonGeometryLineString geom = new GeoJsonGeometryLineString();
        geom.setType("LineString");
        geom.setCoordinates(polyline);

        GeoJsonProperties props = new GeoJsonProperties();
        props.setDistance(computeLength(polyline));
        double[] ad = computeAscentDescent(polyline);
        props.setAscent(ad[0]);
        props.setDescent(ad[1]);

        GeoJsonFeature feature = new GeoJsonFeature();
        feature.setType("Feature");
        feature.setGeometry(geom);
        feature.setProperties(props);
        feature.setBbox(dto.getBbox());

        dto.setFeatures(List.of(feature));
        return dto;
    }


    private List<Double> computeBbox(List<GeoJsonPosition> coords) {
        double minLat = Double.POSITIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;

        for (GeoJsonPosition p : coords) {
            double lat = p.getLatitude();
            double lon = p.getLongitude();

            minLat = Math.min(minLat, lat);
            minLon = Math.min(minLon, lon);
            maxLat = Math.max(maxLat, lat);
            maxLon = Math.max(maxLon, lon);
        }

        return List.of(minLon, minLat, maxLon, maxLat);
    }

    private double[] computeAscentDescent(List<GeoJsonPosition> coords) {
        double ascent = 0;
        double descent = 0;

        for (int i = 1; i < coords.size(); i++) {
            Double a1 = coords.get(i - 1).getAltitude();
            Double a2 = coords.get(i).getAltitude();
            if (a1 == null || a2 == null) {
                continue;
            }

            double diff = a2 - a1;
            if (diff > 0) {
                ascent += diff;
            } else {
                descent += -diff;
            }
        }
        return new double[]{ascent, descent};
    }

    private List<GeoJsonPosition> addSingleWaypoint(List<GeoJsonPosition> route, GeoJsonPosition newPoint)
            throws ValidationException {

        ClosestPointResult closest = findClosestPoint(route, newPoint);
        if (closest.distanceMeters > MAX_DISTANCE_FROM_ROUTE_METERS) {
            throw new StopTooFarFromRouteException(MAX_DISTANCE_FROM_ROUTE_METERS, closest.distanceMeters);
        }

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
        double minAnchorDistance = Math.min(closest.distanceMeters * 2, routeLength / 4); // limit to half the original route
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

        return new GeoJsonPosition(Math.toDegrees(latProj), Math.toDegrees(lonProj), null); // altitude is not important here
    }

    @Override
    public GeoJsonDto reshape(AddStopsDto addStopsDto) throws ValidationException {
        List<GeoJsonPosition> originalRoute = toGeo(addStopsDto.getOriginalRoute());
        List<GeoJsonPosition> newPoints = toGeo(addStopsDto.getNewPoints());

        final double toleranceFactor = 0.1;
        final int roundness = 10;
        final int seed = 1;

        List<GeoJsonPosition> required = new ArrayList<>();
        required.add(originalRoute.getFirst());
        required.addAll(newPoints);
        required.add(originalRoute.getLast());

        GeoJsonDto baselineDto = orsService.generateRoute(required);
        if (baselineDto == null
                || baselineDto.getFeatures() == null
                || baselineDto.getFeatures().isEmpty()
                || baselineDto.getFeatures().getFirst().getGeometry() == null
                || baselineDto.getFeatures().getFirst().getGeometry().getCoordinates() == null) {
            throw new RouteNotFoundException("No route could be generated for the selected location");
        }
        List<GeoJsonPosition> baseline = baselineDto.getFeatures().getFirst().getGeometry().getCoordinates();

        double baselineLength = computeLength(baseline);
        double originalLength = computeLength(originalRoute);
        double baseStitchedLength = computeLength(addWaypoints(addStopsDto).getFeatures().getFirst().getGeometry().getCoordinates());
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
            return addWaypoints(addStopsDto);
        }

        final double minLoop = 50; // needed for ORS stability
        GeoJsonPosition loopCenter = computeCentroid(required);

        // binary search
        double targetMin = minAllowed;
        double targetMax = maxAllowed;

        double low = Math.max(minLoop, (originalLength - diff) * 0.5);
        double high = (originalLength - diff) * (1 + toleranceFactor) * 2;

        final int maxIterations = 2;

        List<GeoJsonPosition> bestRoute = null;
        double bestError = Double.MAX_VALUE;

        GeoJsonDto candidate = new GeoJsonDto();

        List<GeoJsonPosition> requiredWithoutLast = new ArrayList<>();
        requiredWithoutLast.add(originalRoute.getFirst());
        requiredWithoutLast.addAll(newPoints);

        for (int i = 0; i < maxIterations; i++) {
            double requested = (low + high) / 2.5;

            //GeoJsonDto dto = orsService.generateRoundTrip(List.of(originalRoute.getFirst()), (int) requested, roundness, seed);
            GeoJsonDto dto = orsService.generateRoundTrip(List.of(loopCenter), (int) requested, roundness, seed);
            if (dto == null
                    || dto.getFeatures() == null
                    || dto.getFeatures().isEmpty()
                    || dto.getFeatures().getFirst().getGeometry() == null
                    || dto.getFeatures().getFirst().getGeometry().getCoordinates() == null) {
                throw new RouteNotFoundException("No route could be generated for the selected location");
            }
            List<GeoJsonPosition> loop = dto.getFeatures().getFirst().getGeometry().getCoordinates();

            AddStopsDto candidateStopsDto = new AddStopsDto(
                    toStopPoint(loop),
                    toStopPoint(requiredWithoutLast)
            );

            candidate = addWaypoints(candidateStopsDto);
            loop = rotatePolylineToStart(loop, originalRoute.getFirst());
            double length = computeLength(candidate.getFeatures().getFirst().getGeometry().getCoordinates());

            double error = Math.abs(length - originalLength);

            // keep best attempt
            if (error < bestError) {
                bestError = error;
                bestRoute = candidate.getFeatures().getFirst().getGeometry().getCoordinates();
            }

            if (length < targetMin) {
                low = requested; // need longer route
            } else if (length > targetMax) {
                high = requested; // need shorter route
            } else {
                break; // success
            }
        }
        //return createGeoJsonDtoFromPolyline(cleanRoute(rotateToStart(bestRoute, originalRoute.getFirst())));
        return createGeoJsonDtoFromPolyline(cleanRoute(bestRoute));
    }

    private List<GeoJsonPosition> rotatePolylineToStart(
            List<GeoJsonPosition> poly,
            GeoJsonPosition start
    ) {
        int bestIdx = 0;
        double bestD = Double.MAX_VALUE;

        for (int i = 0; i < poly.size(); i++) {
            double d = haversine(poly.get(i), start);
            if (d < bestD) {
                bestD = d;
                bestIdx = i;
            }
        }

        List<GeoJsonPosition> out = new ArrayList<>(poly.size());
        out.addAll(poly.subList(bestIdx, poly.size()));
        out.addAll(poly.subList(0, bestIdx));

        // snap exact start
        out.set(0, start);
        return out;
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

        return new GeoJsonPosition(latMid, lonMid, null);
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
