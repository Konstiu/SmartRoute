package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.util.Coordinate;

import java.util.List;

public interface InsertAdditionalStops {

    /**
     *  Request a route from start -> via -> end and return polyline as list of Coordinates.
     *
     * @param start the coordinate where the new segment should begin
     * @param via the user-defined coordinate that the route must visit
     * @param end the coordinate where the new segment should end
     * @return an ordered list of {@link Coordinate} representing the computed route from start through via to end
     * @throws IllegalStateException if the routing provider returns an invalid or empty response
     */
    public List<Coordinate> routeThroughPoint(Coordinate start, Coordinate via, Coordinate end) throws ValidationException;


    /**
     * Computes optimal endpoints on original route, generates new route segments and stitches them together.
     *
     * @param originalRoute the full list of coordinates representing the existing route; must contain at least two points
     * @param newPoint the coordinate that should be added to the route
     * @return a new list of {@link Coordinate} representing the updated route including the waypoint
     * @throws IllegalArgumentException if the input route is null, too short, or contains invalid data
     */
    public List<Coordinate> addWaypoint(List<Coordinate> originalRoute, Coordinate newPoint) throws ValidationException;
}