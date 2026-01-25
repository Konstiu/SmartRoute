package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;

import java.util.List;

public interface OpenRouteServiceService {

    /**
     * Uses OpenRouteService to generate a route through two or more points.
     *
     * @param coordinates coordinates through which the route should go.
     * @return A route that satisfies the conditions mentioned above.
     */
    GeoJsonDto requestRoute(List<GeoJsonPosition> coordinates);

    /**
     * Uses OpenRouteService to sample the elevation at the coordinates.
     *
     * @param coordinates at which the elevation is requested.
     * @return points with elevation attached.
     */
    List<GeoJsonPosition> requestElevation(List<GeoJsonPosition> coordinates);


//    /**
//     * Uses OpenRouteService to generate a round trip that visits the specified coordinates.
//     *
//     * @param coordinates coordinates through which the route should go.
//     * @param length length of the round trip in m.
//     * @param points number of points ORS selects automatically to generate the route. More points make the route
//     *               rounder.
//     * @param seed a seed. Different numbers generate different routes with the same parameters.
//     * @return A route that satisfies the conditions mentioned above.
//     */
//    GeoJsonDto generateRoundTrip(List<GeoJsonPosition> coordinates, int length, int points, int seed);
}
