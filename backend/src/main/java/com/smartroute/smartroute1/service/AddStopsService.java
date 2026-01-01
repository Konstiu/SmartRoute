package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.AddStopsDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.util.Coordinate;

import java.io.IOException;
import java.util.List;

public interface AddStopsService {

    /**
     * Request a route from start -> via -> end and return polyline as list of Coordinates.
     *
     * @param start the {@link GeoJsonPosition} where the new segment should begin
     * @param via   the user-defined {@link GeoJsonPosition} that the route must visit
     * @param end   the {@link GeoJsonPosition} where the new segment should end
     * @return an ordered list of {@link GeoJsonPosition} representing the computed route from start through via to end
     * @throws ValidationException if the routing provider returns an invalid or empty response
     */
    List<GeoJsonPosition> routeThroughPoint(GeoJsonPosition start, GeoJsonPosition via, GeoJsonPosition end) throws ValidationException;


    /**
     * Computes optimal endpoints on original route, generates new route segments and stitches them together.
     *
     * @param addStopsDto representing the original route and the new points to be added.
     * @return a new list of {@link GeoJsonPosition} representing the updated route including the waypoint
     * @throws ValidationException if the input route is null, too short, or contains invalid data
     */
    GeoJsonDto addWaypoints(AddStopsDto addStopsDto) throws ValidationException;

    /**
     * Extracts a list of coordinates from a given .gpx file.
     *
     * @param pathname name of the path where the gpx file should be read from
     * @return list of {@link GeoJsonPosition}, the extracted polyline
     * @throws IOException if the file cannot be opened, read, or parsed successfully
     */
    List<GeoJsonPosition> gpxToPolyline(String pathname) throws IOException;

    /**
     * Build a .gpx file from a given list of coordinates.
     *
     * @param coordinates list of {@link GeoJsonPosition} that should be included in the .gpx file
     * @param pathname    name of the path where the gpx file should be saved in
     * @throws IOException if the file cannot be created, written to, or closed properly
     */
    void createGpx(List<GeoJsonPosition> coordinates, String pathname) throws IOException;


    /**
     * Tries to reshape a given route to include newly chosen points, while trying to maintain the length of the original route.
     *
     * @param originalRoute the full list of {@link GeoJsonPosition} representing the existing route; must contain at least two points
     * @param newPoints list of {@link GeoJsonPosition} that should be added to the rout
     * @param toleranceFactor double governing how closely the new length should match the old length. Factor of 0.1 gives a route with a max length of 1.1 * original route length.
     * @return @return a new list of {@link GeoJsonPosition} representing the updated route including the waypoints.
     * @throws ValidationException if the input route is null, too short, or contains invalid data
     */
    List<GeoJsonPosition> reshape(List<GeoJsonPosition> originalRoute, List<GeoJsonPosition> newPoints, double toleranceFactor) throws ValidationException;
}