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
import jakarta.annotation.security.PermitAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/route")
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
        return "{\"bbox\":" + route.getBbox()
                + ",\"polyline\":\"" + polyLineMapper.geoJsonGeometryLineStringToPolyline(route.getFeatures().getFirst().getGeometry()).replace("\\", "\\\\") + "\""
                + ",\"distance\":" + route.getFeatures().getFirst().getProperties().getDistance() * 1000
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
        return routeService.getRoute(id);
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

        routeService.deleteRoute(id);
    }


}