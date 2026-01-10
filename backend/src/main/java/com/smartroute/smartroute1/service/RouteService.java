package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.RouteDto;
import com.smartroute.smartroute1.endpoint.dto.SaveRouteDto;
import com.smartroute.smartroute1.endpoint.dto.ViewRouteDto;
import com.smartroute.smartroute1.entity.ApplicationUser;

import java.util.List;

public interface RouteService {
    /**
     * Save route for the corresponding user.
     *
     * @param route the routedto to save
     * @param user  the user it belongs to
     */
    void saveRoute(SaveRouteDto route, ApplicationUser user);

    /**
     * Get all routes saved routes from a user.
     *
     * @param user the user to get the routes from
     * @return the view route dto
     */
    List<ViewRouteDto> getRoutes(ApplicationUser user);

    /**
     * Get one route by id
     *
     * @param id the id
     * @return the ViewRouteDto
     */
    ViewRouteDto getRoute(Long id);

    /**
     * Delete one by id
     *
     * @param id the id
     */
    void deleteRoute(Long id);

}
