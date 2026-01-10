package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.endpoint.dto.SaveRouteDto;
import com.smartroute.smartroute1.endpoint.dto.ViewRouteDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Route;
import com.smartroute.smartroute1.exception.NotFoundException;
import com.smartroute.smartroute1.repository.RouteRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.impl.RouteServiceImpl;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles({"test", "generateData"})
@Transactional
public class RouteServiceTest {


    @Autowired
    private RouteServiceImpl routeService;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private UserRepository userRepository;

    private ApplicationUser user;

    @BeforeEach
    void setUp() {
        user = new ApplicationUser("test@email.com", "Password123!", "John", "Doe");
        user = userRepository.save(user);
    }

    @Test
    void test_WhenSaveRouteAndRetrieve_ThenReturnsCorrectly() {
        SaveRouteDto dto = new SaveRouteDto();
        dto.setName("Integration Test Route");
        dto.setDistance(12.0);
        dto.setPace(6.5);
        dto.setElevation(150.0);
        dto.setRoute("encodedPolyline");

        routeService.saveRoute(dto, user);


        List<Route> routes = routeRepository.findRoutesByUserIdOrderByCreationDateDesc(user.getId());
        assertEquals(1, routes.size());
        Route saved = routes.get(0);
        assertEquals("Integration Test Route", saved.getName());


        ViewRouteDto dtoReturned = routeService.getRoute(saved.getId());
        assertEquals("Integration Test Route", dtoReturned.getName());
        assertEquals(12.0, dtoReturned.getDistance());
        assertEquals(6.5, dtoReturned.getPace());
        assertEquals(150.0, dtoReturned.getElevation());
    }

    @Test
    void testGetRoutes_emptyList() {
        List<ViewRouteDto> dtos = routeService.getRoutes(user);
        assertNotNull(dtos);
        assertTrue(dtos.isEmpty());
    }

    @Test
    void testGetRoute_notFound() {
        assertThrows(NotFoundException.class, () -> {
            routeService.getRoute(999L);
        });

    }

    @Test
    void testDeleteRoute() {
        routeService.deleteRoute(-1L);
    }
}
