package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.endpoint.mapper.PolyLineMapper;
import com.smartroute.smartroute1.service.OpenRouteServiceService;
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

    private final OpenRouteServiceService openRouteServiceService;
    private final PolyLineMapper polyLineMapper;

    public RouteEndpoint(OpenRouteServiceService openRouteServiceService, PolyLineMapper polyLineMapper) {
        this.openRouteServiceService = openRouteServiceService;
        this.polyLineMapper = polyLineMapper;
    }

    @PermitAll
    @GetMapping
    public String generateRoute(@RequestParam("lat") double latitude, @RequestParam("long") double longitude,
                                @RequestParam("length") double length) {
        List<GeoJsonPosition> coordinates = new ArrayList<>();
        coordinates.add(new GeoJsonPosition(latitude, longitude, null));
        GeoJsonDto route = openRouteServiceService.generateRoundTrip(coordinates, (int) length, 7, 0);
        return "{\"bbox\":" + route.getBbox() + ",\"polyline\":\"" + polyLineMapper.geoJsonGeometryLineStringToPolyline(route.getFeatures().getFirst().getGeometry()).replace("\\", "\\\\") + "\"}";
    }
}