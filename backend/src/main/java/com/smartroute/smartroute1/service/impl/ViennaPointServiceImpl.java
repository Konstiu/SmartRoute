package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.entity.ViennaPoint;
import com.smartroute.smartroute1.entity.enums.Sanitary;
import com.smartroute.smartroute1.repository.ViennaPointRepository;
import com.smartroute.smartroute1.service.ViennaPointService;
import com.smartroute.smartroute1.util.Coordinate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Service
@RequiredArgsConstructor
public class ViennaPointServiceImpl implements ViennaPointService {

    private final ViennaPointRepository viennaPointRepository;
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final double EARTH_RADIUS_METERS = 6371000.0;

    @Override
    public List<GeoJsonPosition> findFacilitiesAlongRoute(List<GeoJsonPosition> routeCoords, Sanitary sanitary, int toiletIntervalMeters, int maxFacilityDistance) {
        List<GeoJsonPosition> facilities = new ArrayList<>();
        double accumulatedDistance = 0;
        double nextFacilityAt = maxFacilityDistance;

        // Track which facilities we've already added to avoid duplicates
        Set<String> addedFacilityIds = new HashSet<>();

        for (int i = 0; i < routeCoords.size() - 1; i++) {
            GeoJsonPosition current = routeCoords.get(i);
            GeoJsonPosition next = routeCoords.get(i + 1);

            double segmentDistance = haversine(current, next);
            accumulatedDistance += segmentDistance;

            // Check if we've reached the next interval
            if (accumulatedDistance >= nextFacilityAt) {
                // Find nearest facility to this point
                ViennaPoint facility = findNearestFacility(
                        new Coordinate(current.getLatitude(), current.getLongitude()),
                        sanitary,
                        maxFacilityDistance,
                        addedFacilityIds
                );

                if (facility != null) {
                    facilities.add(new GeoJsonPosition(
                            facility.getCoordinate().getLatitude(),
                            facility.getCoordinate().getLongitude(),
                            null
                    ));

                    addedFacilityIds.add(facility.getId());

                    // Set next interval
                    nextFacilityAt = accumulatedDistance + toiletIntervalMeters;

                    LOGGER.debug("Added {} at {}m from start", sanitary, (int) accumulatedDistance);
                } else {
                    LOGGER.debug("No {} found within {}m at {}m mark",
                            sanitary, maxFacilityDistance, (int) accumulatedDistance);
                    // Still advance the interval to avoid checking same spot repeatedly
                    nextFacilityAt = accumulatedDistance + toiletIntervalMeters;
                }
            }
        }

        return facilities;
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


    private ViennaPoint findNearestFacility(Coordinate location, Sanitary type, int maxDistanceMeters, Set<String> excludeIds) {
        List<ViennaPoint> allFacilities = viennaPointRepository.findAllByType(type);

        ViennaPoint nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (ViennaPoint facility : allFacilities) {
            // Skip if already used
            if (excludeIds.contains(facility.getId())) {
                continue;
            }

            double distance = calculateDistance(location, facility.getCoordinate());

            if (distance < minDistance && distance <= maxDistanceMeters) {
                minDistance = distance;
                nearest = facility;
            }
        }

        return nearest;
    }

    private double calculateDistance(Coordinate c1, Coordinate c2) {
        return haversine(
                new GeoJsonPosition(c1.getLatitude(), c1.getLongitude(), null),
                new GeoJsonPosition(c2.getLatitude(), c2.getLongitude(), null)
        );
    }
}
