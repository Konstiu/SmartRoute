package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.RouteDto;
import com.smartroute.smartroute1.endpoint.dto.SaveRouteDto;
import com.smartroute.smartroute1.endpoint.dto.ShareRouteDto;
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
     * Get one route by id.
     *
     * @param id    the id
     * @param email to verify email
     * @return the ViewRouteDto
     */
    ViewRouteDto getRoute(Long id, String email);

    /**
     * Delete one by id.
     *
     * @param id    the id
     * @param email email of user this belongs to
     */
    void deleteRoute(Long id, String email);


    /**
     * Shares a route with other users.
     *
     * @param id the id of the route
     * @param email1 the email of the owner of the route
     * @param firends the emails of the friends we share the route with
     */
    void addShare(Long id, String email1, ShareRouteDto firends);
}
