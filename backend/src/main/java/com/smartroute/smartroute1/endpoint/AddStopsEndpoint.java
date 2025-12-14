package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.AddStopsDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.service.AddStopsService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stops")
@RequiredArgsConstructor
public class AddStopsEndpoint {
    private final AddStopsService service;

    @PostMapping("/insert")
    @PermitAll
    @Operation(summary = "Insert additional stops.",
            description = "Edit a given route to include multiple coordinates the user sets.")
    public ResponseEntity<List<GeoJsonPosition>> addWaypoints(@RequestBody AddStopsDto addStopsDto) throws ValidationException {
        List<GeoJsonPosition> editedRoute  = service.addWaypoints(addStopsDto.getOriginalRoute(), addStopsDto.getNewPoint());
        return ResponseEntity.ok(editedRoute);
    }
}
