package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.AddStopsDto;
import com.smartroute.smartroute1.endpoint.dto.RouteWithFacilitiesDto;
import com.smartroute.smartroute1.endpoint.dto.ViennaPointDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonGeometryLineString;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.endpoint.mapper.PolyLineMapper;
import com.smartroute.smartroute1.entity.ViennaPoint;
import com.smartroute.smartroute1.entity.enums.Sanitary;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.repository.ViennaPointRepository;
import com.smartroute.smartroute1.service.AddStopsService;
import com.smartroute.smartroute1.service.ViennaPointService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;


import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1/stops")
@RequiredArgsConstructor
public class AddStopsEndpoint {
    private final AddStopsService service;
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final PolyLineMapper mapper;
    private final ViennaPointService viennaPointService;
    private final ViennaPointRepository viennaPointRepository;

    @PostMapping("/insert")
    @PermitAll
    @Operation(summary = "Insert additional stops.",
            description = "Edit a given route to include multiple coordinates the user sets.")
    public ResponseEntity<List<GeoJsonPosition>> addWaypoints(@RequestBody AddStopsDto addStopsDto) throws ValidationException {
        List<GeoJsonPosition> editedRoute = service.addWaypoints(addStopsDto.getOriginalRoute(), addStopsDto.getNewPoint());
        return ResponseEntity.ok(editedRoute);
    }


    @PostMapping("/with-facilities")
    @PermitAll
    public ResponseEntity<String> generateRouteWithFacilities(@RequestBody RouteWithFacilitiesDto addFacilitiesDto) {

        try {
            // 1. Decode the polyline to coordinates
            List<GeoJsonPosition> routeCoords = mapper.decodePolylineToPoints(addFacilitiesDto.getOriginalRoute());
            LOGGER.info("Original route: {} points, {}m total",
                    routeCoords.size(), service.calculateTotalDistance(routeCoords));

            // 2. Collect all facility stops
            List<GeoJsonPosition> facilityStops = new ArrayList<>();

            if (addFacilitiesDto.isIncludeToilets()) {
                List<GeoJsonPosition> toilets = viennaPointService.findFacilitiesAlongRoute(
                        routeCoords,
                        Sanitary.Toilet,
                        addFacilitiesDto.getToiletIntervalMeters(),
                        addFacilitiesDto.getMaxFacilityDistance()
                );
                facilityStops.addAll(toilets);
                LOGGER.info("Found {} toilet stops", toilets.size());
            }

            if (addFacilitiesDto.isIncludeFountains()) {
                List<GeoJsonPosition> fountains = viennaPointService.findFacilitiesAlongRoute(
                        routeCoords,
                        Sanitary.Fountain,
                        addFacilitiesDto.getFountainIntervalMeters(),
                        addFacilitiesDto.getMaxFacilityDistance()
                );
                facilityStops.addAll(fountains);
                LOGGER.info("Found {} fountain stops", fountains.size());
            }

            // Store original distance for comparison
            double originalDistance = service.calculateTotalDistance(routeCoords);

            if (facilityStops.isEmpty()) {
                LOGGER.info("No facilities found or requested, returning original route");
            } else {
                // 3. Insert all facility stops with detours
                LOGGER.info("Adding {} facility stops to route...", facilityStops.size());
                routeCoords = service.addWaypoints(routeCoords, facilityStops);

                double newDistance = service.calculateTotalDistance(routeCoords);
                double addedDistance = newDistance - originalDistance;

                LOGGER.info("Route updated: {} points, {}m total (added {}m for facilities)",
                        routeCoords.size(), newDistance, addedDistance);
            }

            // 4. Build response
            String polyline = mapper.geoJsonGeometryLineStringToPolyline(
                    service.createGeometryFromCoords(routeCoords)
            ).replace("\\", "\\\\");

            double finalDistance = service.calculateTotalDistance(routeCoords);
            double distanceAdded = finalDistance - originalDistance;

            String response = "{"
                    + "\"polyline\":\"" + polyline + "\""
                    + ",\"distance\":" + finalDistance
                    + ",\"originalDistance\":" + originalDistance
                    + ",\"distanceAdded\":" + distanceAdded
                    + ",\"facilitiesAdded\":" + facilityStops.size()
                    + ",\"totalPoints\":" + routeCoords.size()
                    + "}";

            return ResponseEntity.ok(response);

        } catch (ValidationException e) {
            LOGGER.warn("Validation error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body("{\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}");
        } catch (Exception e) {
            LOGGER.error("Error generating route with facilities", e);
            return ResponseEntity.internalServerError()
                    .body("{\"error\":\"Internal server error: " + e.getMessage().replace("\"", "\\\"") + "\"}");
        }
    }

    @GetMapping("/facilities")
    @PermitAll
    public List<ViennaPointDto> getFacilitiesInBounds() {
        return Stream.concat(
                viennaPointRepository.findAllByType(Sanitary.Fountain).stream(),
                viennaPointRepository.findAllByType(Sanitary.Toilet).stream()
        ).map(this::mapToDto).toList();
    }

    private ViennaPointDto mapToDto(ViennaPoint entity) {
        ViennaPointDto dto = new ViennaPointDto();
        dto.setId(entity.getId());
        dto.setCoordinate(entity.getCoordinate());
        dto.setType(entity.getType());
        return dto;
    }


}
