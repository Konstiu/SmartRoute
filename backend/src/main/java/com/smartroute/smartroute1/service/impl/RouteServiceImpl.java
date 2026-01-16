package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.SaveRouteDto;
import com.smartroute.smartroute1.endpoint.dto.ViewRouteDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Route;
import com.smartroute.smartroute1.exception.NotFoundException;
import com.smartroute.smartroute1.repository.RouteRepository;
import com.smartroute.smartroute1.service.RouteService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RouteServiceImpl implements RouteService {

    private final RouteRepository routeRepository;

    public RouteServiceImpl(RouteRepository routeRepository) {
        this.routeRepository = routeRepository;
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
    public ViewRouteDto getRoute(Long id) {

        Optional<Route> opt = routeRepository.findById(id);
        Route route;
        if (opt.isPresent()) {
            route = opt.get();
        } else {
            throw new NotFoundException("Route with id + " + id + " not found");
        }
        return routeToDto(route);
    }

    @Override
    public void deleteRoute(Long id) {
        routeRepository.deleteById(id);
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
