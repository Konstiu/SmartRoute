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
    GeoJsonDto generateRoute(List<GeoJsonPosition> coordinates, boolean vienna);

    /**
     * Uses OpenRouteService to generate a round trip that visits the specified coordinates.
     *
     * @param coordinates coordinates through which the route should go.
     * @param length length of the round trip in m.
     * @param points number of points ORS selects automatically to generate the route. More points make the route
     *               rounder.
     * @param seed a seed. Different numbers generate different routes with the same parameters.
     * @return A route that satisfies the conditions mentioned above.
     */
    GeoJsonDto generateRoundTrip(List<GeoJsonPosition> coordinates, int length, int points, int seed);


    /**
     * Generates a foot-walking route using OpenRouteService, while instructing the routing engine to avoid a specified polygonal area.
     *
     * @param positions Ordered list of {@link GeoJsonPosition} representing the waypoint sequence through which the route should be generated.
     *
     * @param avoidPolygon ORS will avoid routing through any part of this polygon.
     *
     * @return A {@link GeoJsonDto} containing the generated route in GeoJSON format.
     */
    GeoJsonDto generateRouteAvoidingPolygon(List<GeoJsonPosition> positions, List<List<Double>> avoidPolygon, boolean facilities);
}
