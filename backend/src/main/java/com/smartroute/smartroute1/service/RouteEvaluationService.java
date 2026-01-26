package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;

import java.util.List;

public interface RouteEvaluationService {

    /**
     * Evaluates a route regarding its elevation profile. It calculates the equivalent distance if it was equally
     * difficult but on flat terrain.
     *
     * @param route route to evaluate
     * @return equivalent distance in meters.
     */
    double evaluateRoute(List<GeoJsonPosition> route);
}
