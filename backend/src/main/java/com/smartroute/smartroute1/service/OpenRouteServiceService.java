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
    GeoJsonDto requestRoute(List<GeoJsonPosition> coordinates, boolean vienna);

    /**
     * Uses OpenRouteService to sample the elevation at the coordinates.
     *
     * @param coordinates at which the elevation is requested.
     * @return points with elevation attached.
     */
    List<GeoJsonPosition> requestElevation(List<GeoJsonPosition> coordinates);

    /**
     * Uses OpenRouteService generate a roundtrip.
     *
     * @param coordinates one coordinate which is start and endpoint.
     * @return generated route.
     */
    GeoJsonDto generateRoundTrip(List<GeoJsonPosition> coordinates, int length, int points, int seed);

    /**
     * Generates a foot-walking route using OpenRouteService, while instructing the routing engine to avoid a specified polygonal area.
     *
     * @param positions    Ordered list of {@link GeoJsonPosition} representing the waypoint sequence through which the route should be generated.
     * @param avoidPolygon ORS will avoid routing through any part of this polygon.
     * @return A {@link GeoJsonDto} containing the generated route in GeoJSON format.
     */
    GeoJsonDto generateRouteAvoidingPolygon(List<GeoJsonPosition> positions, List<List<Double>> avoidPolygon, boolean facilities);
}
