package com.smartroute.smartroute1.endpoint.dto.statistics;

import com.smartroute.smartroute1.entity.GymWorkout;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class GymHistoryDto {
    int noOfGymRecommendations;

    List<GymWorkout> gymWorkouts;
}
