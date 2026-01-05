package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.RouteDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.WorkoutType;

public interface RouteGenerationService {

    RouteDto generateRouteDetails(ApplicationUser user, WorkoutType workoutType, double readinessScore);
}
