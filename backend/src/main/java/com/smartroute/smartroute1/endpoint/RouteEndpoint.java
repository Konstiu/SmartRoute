package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.SaveRouteDto;
import com.smartroute.smartroute1.endpoint.dto.ViewRouteDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.endpoint.mapper.PolyLineMapper;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.service.OpenRouteServiceService;
import com.smartroute.smartroute1.service.RouteService;
import com.smartroute.smartroute1.service.impl.CustomUserDetailService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/route")
@Tag(name = "Route Endpoint")
public class RouteEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final OpenRouteServiceService openRouteServiceService;
    private final RouteService routeService;
    private final CustomUserDetailService customUserDetailService;
    private final PolyLineMapper polyLineMapper;

    public RouteEndpoint(OpenRouteServiceService openRouteServiceService, RouteService routeService, CustomUserDetailService customUserDetailService, PolyLineMapper polyLineMapper) {
        this.openRouteServiceService = openRouteServiceService;
        this.routeService = routeService;
        this.customUserDetailService = customUserDetailService;
        this.polyLineMapper = polyLineMapper;
    }

    @PermitAll
    @GetMapping
    public String generateRoute(@RequestParam("lat") double latitude, @RequestParam("long") double longitude,
                                @RequestParam("length") double length) {
        List<GeoJsonPosition> coordinates = new ArrayList<>();
        coordinates.add(new GeoJsonPosition(latitude, longitude, null));
        GeoJsonDto route = openRouteServiceService.generateRoundTrip(coordinates, (int) length, 7, 0);

        var geom = route.getFeatures().getFirst().getGeometry();
        var props = route.getFeatures().getFirst().getProperties();

        // assuming geom.getCoordinates() returns List<GeoJsonPosition> (lat/lon/alt already mapped)
        List<List<Double>> coordinates3d = geom.getCoordinates().stream()
                .map(p -> List.of(
                        p.getLatitude(),
                        p.getLongitude(),
                        p.getAltitude() // may be null
                ))
                .toList();

        return "{\"bbox\":" + route.getBbox()
                + ",\"polyline\":\"" + polyLineMapper.geoJsonGeometryLineStringToPolyline(route.getFeatures().getFirst().getGeometry()).replace("\\", "\\\\") + "\""
                + ",\"coordinates3d\":" + coordinates3d
                + ",\"distance\":" + route.getFeatures().getFirst().getProperties().getDistance()
                + ",\"elevation\":" + route.getFeatures().getFirst().getProperties().getAscent() + "}";
    }

    @Secured("ROLE_USER")
    @PostMapping("/save")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public void saveRoute(@RequestBody SaveRouteDto route) {
        LOGGER.info("Saving route: {}", route);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        ApplicationUser user = customUserDetailService.findApplicationUserByEmail(email);

        routeService.saveRoute(route, user);
    }

    @Secured("ROLE_USER")
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public ViewRouteDto getRoute(@PathVariable("id") long id) {
        LOGGER.info("Getting route: {}", id);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();


        return routeService.getRoute(id, email);
    }

    @Secured("ROLE_USER")
    @GetMapping("/get")
    @ResponseStatus(HttpStatus.OK)
    public List<ViewRouteDto> getRoutes() {


        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        ApplicationUser user = customUserDetailService.findApplicationUserByEmail(email);
        LOGGER.info("Getting routes for user: {}", user.getEmail());
        return routeService.getRoutes(user);
    }

    @Secured("ROLE_USER")
    @DeleteMapping("{id}")
    @ResponseStatus(HttpStatus.OK)
    public void deleteRoute(@PathVariable("id") long id) {
        LOGGER.info("Deleting route: {}", id);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        routeService.deleteRoute(id, email);
    }


}