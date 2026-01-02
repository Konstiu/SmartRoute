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
}