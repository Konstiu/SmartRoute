package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.entity.ViennaPoint;
import com.smartroute.smartroute1.entity.enums.Sanitary;
import com.smartroute.smartroute1.util.Coordinate;

import java.util.List;

public interface ViennaPointService {

    List<GeoJsonPosition> findFacilitiesAlongRoute(List<GeoJsonPosition> routeCoords, Sanitary sanitary, int toiletIntervalMeters, int maxFacilityDistance);
}
