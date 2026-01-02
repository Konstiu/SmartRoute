package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.AddStopsDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.endpoint.mapper.PolyLineMapper;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.service.AddStopsService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.PermitAll;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stops")
public class AddStopsEndpoint {
    private final AddStopsService service;
    private final PolyLineMapper polyLineMapper;

    public AddStopsEndpoint(AddStopsService service, PolyLineMapper polyLineMapper) {
        this.service = service;
        this.polyLineMapper = polyLineMapper;
    }

    @PostMapping("/insert")
    @PermitAll
    @Operation(summary = "Insert additional stops.",
            description = "Edit a given route to include multiple coordinates the user sets.")
    public ResponseEntity<String> addWaypoints(@RequestBody AddStopsDto addStopsDto) throws ValidationException {
        GeoJsonDto editedRoute = service.addWaypoints(addStopsDto);
        String response = "{\"bbox\":" + editedRoute.getBbox()
                + ",\"polyline\":\"" + polyLineMapper.geoJsonGeometryLineStringToPolyline(editedRoute.getFeatures().getFirst().getGeometry()).replace("\\", "\\\\") + "\""
                + ",\"distance\":" + editedRoute.getFeatures().getFirst().getProperties().getDistance()
                + ",\"elevation\":" + editedRoute.getFeatures().getFirst().getProperties().getAscent() + "}";
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reshape")
    @PermitAll
    @Operation(summary = "Reshape the route to include additional stops.",
            description = "Reshape a given route to include multiple coordinates, without changing the original length too much.")
    public ResponseEntity<String> reshape(@RequestBody AddStopsDto addStopsDto) throws ValidationException {
        GeoJsonDto editedRoute = service.reshape(addStopsDto);
        String response = "{\"bbox\":" + editedRoute.getBbox()
                + ",\"polyline\":\"" + polyLineMapper.geoJsonGeometryLineStringToPolyline(editedRoute.getFeatures().getFirst().getGeometry()).replace("\\", "\\\\") + "\""
                + ",\"distance\":" + editedRoute.getFeatures().getFirst().getProperties().getDistance()
                + ",\"elevation\":" + editedRoute.getFeatures().getFirst().getProperties().getAscent() + "}";
        return ResponseEntity.ok(response);
    }
}
