package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.AddStopsDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonGeometryLineString;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.util.Coordinate;

import java.io.IOException;
import java.util.List;

public interface AddStopsService {
    /**
     * Computes optimal endpoints on original route, generates new route segments and stitches them together.
     *
     * @param addStopsDto representing the original route and the new points to be added.
     * @return a {@link GeoJsonDto} containing necessary information about the new route.
     * @throws ValidationException if the input route is null, too short, or contains invalid data.
     */
    GeoJsonDto addWaypoints(AddStopsDto addStopsDto) throws ValidationException;

    /**
     * Tries to reshape a given route to include newly chosen points, while trying to maintain the length of the original route.
     *
     * @param addStopsDto representing the original route and the new points to be added.
     * @return a {@link GeoJsonDto} containing necessary information about the new route.
     * @throws ValidationException if the input route is null, too short, or contains invalid data.
     */
    GeoJsonDto reshape(AddStopsDto addStopsDto) throws ValidationException;

    /**
     * Inserts additional waypoints into an existing route by temporarily exiting
     * the original route and rejoining it later.
     *
     * <p>The method takes the original route geometry, injects the given
     * {@code newPoints} at an appropriate position, and ensures that the resulting
     * route reconnects smoothly to the original path.</p>
     *
     * @param originalRoute the original route coordinates in correct order
     * @param newPoints     the waypoints to be inserted between exit and rejoin points
     * @param dist          the maximum allowed distance (in meters) for exit/rejoin matching
     * @return a new list of {@link GeoJsonPosition} representing the modified route
     * @throws ValidationException if the route cannot be exited or rejoined within the given distance constraint
     */
    List<GeoJsonPosition> addWaypointsExitAndRejoin(List<GeoJsonPosition> originalRoute, List<GeoJsonPosition> newPoints, double dist) throws ValidationException;

    /**
     * Creates a GeoJSON {@code LineString} geometry from a list of coordinates.
     *
     * <p>The input coordinates must be ordered and use the GeoJSON coordinate
     * format {@code [longitude, latitude]}.</p>
     *
     * @param routeCoords the route coordinates
     * @return a {@link GeoJsonGeometryLineString} representing the route geometry
     * @throws IllegalArgumentException if fewer than two coordinates are provided
     */
    GeoJsonGeometryLineString createGeometryFromCoords(List<GeoJsonPosition> routeCoords);

    /**
     * Calculates the total length of a route defined by a list of coordinates.
     *
     * <p>The distance is computed as the sum of the distances between consecutive
     * points along the route.</p>
     *
     * @param routeCoords the route coordinates in traversal order
     * @return the total route distance in meters
     * @throws IllegalArgumentException if the route contains fewer than two points
     */
    double calculateTotalDistance(List<GeoJsonPosition> routeCoords);
}