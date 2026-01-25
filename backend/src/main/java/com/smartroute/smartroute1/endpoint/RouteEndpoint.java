package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.endpoint.mapper.PolyLineMapper;
import com.smartroute.smartroute1.service.OpenRouteServiceService;
import com.smartroute.smartroute1.service.RouteGenerationService;
import jakarta.annotation.security.PermitAll;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/route")
public class RouteEndpoint {

    private final RouteGenerationService routeGenerationService;
    private final PolyLineMapper polyLineMapper;

    public RouteEndpoint(RouteGenerationService routeGenerationService, PolyLineMapper polyLineMapper) {
        this.routeGenerationService = routeGenerationService;
        this.polyLineMapper = polyLineMapper;
    }

    @PermitAll
    @GetMapping
    public String generateRoute(@RequestParam("lat") double latitude, @RequestParam("long") double longitude,
                                @RequestParam("length") double length) {
        GeoJsonDto route = routeGenerationService.generateRoundTrip(
                new GeoJsonPosition(latitude, longitude, null), (int) length);
        return "{\"bbox\":" + route.getBbox()
                + ",\"polyline\":\"" + polyLineMapper.geoJsonGeometryLineStringToPolyline(route.getFeatures().getFirst().getGeometry()).replace("\\", "\\\\") + "\""
                + ",\"distance\":" + route.getFeatures().getFirst().getProperties().getDistance() * 1000
                + ",\"elevation\":" + route.getFeatures().getFirst().getProperties().getAscent() + "}";
    }
}