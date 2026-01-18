package com.smartroute.smartroute1.service.validators;

import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.util.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class AddStopsValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public void validateRouteLength(List<GeoJsonPosition> route) throws ValidationException {
        List<String> errors = new ArrayList<>();
        LOGGER.trace("Validation of route length: {}", route);

        if (route == null) {
            errors.add("Route is null");
            throw new ValidationException("Errors while validating route length:", errors);
        }

        if (route.size() <= 2) {
            errors.add("Route is too short");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Errors while validating route length:", errors);
        }
    }

    private boolean checkCoordinateEquality(GeoJsonPosition coordinate1, GeoJsonPosition coordinate2) {
        return (Objects.equals(coordinate1.getLongitude(), coordinate2.getLongitude()) && Objects.equals(coordinate1.getLatitude(), coordinate2.getLatitude()));
    }

    public void validateSameEndpoints(List<GeoJsonPosition> oldRoute, List<GeoJsonPosition> newRoute) throws ValidationException {
        LOGGER.trace("Validating if new and old route have the same endpoints: oldRoute {}, newRoute {}", oldRoute, newRoute);

        validateRouteLength(oldRoute);
        validateRouteLength(newRoute);
        List<String> errors = new ArrayList<>();

        if (!checkCoordinateEquality(oldRoute.getFirst(), newRoute.getFirst())) {
            errors.add("Start points are not equal");
        }

        if (!checkCoordinateEquality(oldRoute.getLast(), newRoute.getLast())) {
            errors.add("End points are not equal");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Errors while validating route length:", errors);
        }
    }

    public void validateCoordinates(double latitude, double longitude) throws ValidationException {
        List<String> errors = new ArrayList<>();
        LOGGER.trace("Validation of latitude {} and longitude: {}", latitude, longitude);

        if (latitude < -90) {
            errors.add("latitude is smaller than -90");
        }

        if (latitude > 90) {
            errors.add("latitude is larger than 90");
        }

        if (longitude < -180) {
            errors.add("longitude is smaller than -180");
        }

        if (longitude > 180) {
            errors.add("longitude is larger than 180");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Errors while verifying lat. and long.:", errors);
        }
    }
}
