package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.SaveRouteDto;
import com.smartroute.smartroute1.endpoint.dto.ShareRouteDto;
import com.smartroute.smartroute1.endpoint.dto.ViewRouteDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Route;
import com.smartroute.smartroute1.exception.NotFoundException;
import com.smartroute.smartroute1.repository.RouteRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.RouteService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RouteServiceImpl implements RouteService {

    private final RouteRepository routeRepository;
    private final UserRepository userRepository;

    public RouteServiceImpl(RouteRepository routeRepository, UserRepository userRepository) {
        this.routeRepository = routeRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void saveRoute(SaveRouteDto dto, ApplicationUser user) {
        Route route = new Route();
        route.setPace(dto.getPace());
        route.setName(dto.getName());
        route.setDistance(dto.getDistance());
        route.setUser(user);
        route.setElevation(dto.getElevation());
        route.setRoute(dto.getRoute());
        route.setCreationDate(LocalDate.now());

        routeRepository.save(route);
    }

    @Override
    public List<ViewRouteDto> getRoutes(ApplicationUser user) {

        List<Route> routes = routeRepository.findRoutesByUserIdOrderByCreationDateDesc(user.getId());
        return listOfRoutesToDtoList(routes);
    }

    @Override
    public ViewRouteDto getRoute(Long id, String email) {

        Optional<Route> opt = routeRepository.findById(id);
        Route route;
        if (opt.isPresent()) {
            route = opt.get();
        } else {
            throw new NotFoundException("Route with id + " + id + " not found");
        }
        if (!route.getUser().getEmail().equals(email)) {
            throw new HttpClientErrorException(HttpStatus.UNAUTHORIZED);
        }
        return routeToDto(route);
    }

    @Override
    public void deleteRoute(Long id, String email) {
        Optional<Route> opt = routeRepository.findById(id);
        Route route;
        if (opt.isPresent()) {
            route = opt.get();
        } else {
            return;
        }
        if (route.getUser().getEmail().equals(email)) {
            throw new HttpClientErrorException(HttpStatus.UNAUTHORIZED);
        }
        routeRepository.deleteById(id);
    }

    @Override
    public void addShare(Long id, String email, ShareRouteDto email2) {
        Optional<Route> opt = routeRepository.findById(id);
        Route route;
        if (opt.isPresent()) {
            route = opt.get();
        } else {
            return;
        }
        if (route.getUser().getEmail().equals(email)) {
            throw new HttpClientErrorException(HttpStatus.UNAUTHORIZED);
        }
        for (String targetEmail : email2.getFriends()) {
            if (targetEmail == null) continue;

            String e = targetEmail.trim();
            if (e.isEmpty()) continue;

            // optional: prevent sharing with yourself
            if (e.equalsIgnoreCase(email)) continue;

            ApplicationUser userToShareWith = userRepository.findUserByEmail(e);
            if (userToShareWith != null) {
                route.getShared().add(userToShareWith);
            }
        }
        routeRepository.save(route);
    }

    private ViewRouteDto routeToDto(Route route) {
        return new ViewRouteDto(
                route.getId(),
                route.getName(),
                route.getDistance(),
                route.getPace(),
                route.getElevation(),
                route.getRoute(),
                route.getCreationDate()
        );
    }

    private List<ViewRouteDto> listOfRoutesToDtoList(List<Route> routes) {
        if (routes == null || routes.isEmpty()) {
            return List.of();
        }

        List<ViewRouteDto> dtos = new ArrayList<>();
        for (Route route : routes) {
            dtos.add(routeToDto(route));
        }
        return dtos;
    }
}
