package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.util.Coordinate;

import java.io.IOException;
import java.util.List;

public interface AddStopsService {

    /**
     * Request a route from start -> via -> end and return polyline as list of Coordinates.
     *
     * @param start the coordinate where the new segment should begin
     * @param via   the user-defined coordinate that the route must visit
     * @param end   the coordinate where the new segment should end
     * @return an ordered list of {@link Coordinate} representing the computed route from start through via to end
     * @throws IllegalStateException if the routing provider returns an invalid or empty response
     */
    List<Coordinate> routeThroughPoint(Coordinate start, Coordinate via, Coordinate end) throws ValidationException;


    /**
     * Computes optimal endpoints on original route, generates new route segments and stitches them together.
     *
     * @param originalRoute the full list of coordinates representing the existing route; must contain at least two points
     * @param newPoints     list of {@link Coordinate} that should be added to the rout
     * @return a new list of {@link Coordinate} representing the updated route including the waypoint
     * @throws IllegalArgumentException if the input route is null, too short, or contains invalid data
     */
    List<Coordinate> addWaypoints(List<Coordinate> originalRoute, List<Coordinate> newPoints) throws ValidationException;

    /**
     * Extracts a list of coordinates from a given .gpx file.
     *
     * @param pathname name of the path where the gpx file should be read from
     * @return list of {@link Coordinate}, the extracted polyline
     * @throws IOException if the file cannot be opened, read, or parsed successfully
     */
    List<Coordinate> gpxToPolyline(String pathname) throws IOException;

    /**
     * Build a .gpx file from a given list of coordinates.
     *
     * @param coordinates list of {@link Coordinate} that should be included in the .gpx file
     * @param pathname    name of the path where the gpx file should be saved in
     * @throws IOException if the file cannot be created, written to, or closed properly
     */
    void createGpx(List<Coordinate> coordinates, String pathname) throws IOException;
}