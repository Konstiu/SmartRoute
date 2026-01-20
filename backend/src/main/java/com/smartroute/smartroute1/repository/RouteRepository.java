package com.smartroute.smartroute1.repository;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RouteRepository extends JpaRepository<Route, Long> {

    List<Route> findRoutesByUserIdOrderByCreationDateDesc(Long userId);

    List<Route> findByShared_IdOrderByCreationDateDesc(Long id);
}
