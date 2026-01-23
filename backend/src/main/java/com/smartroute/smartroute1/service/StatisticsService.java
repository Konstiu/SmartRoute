package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.statistics.ConsistencyHistoryDto;
import com.smartroute.smartroute1.endpoint.dto.statistics.GymHistoryDto;
import com.smartroute.smartroute1.endpoint.dto.statistics.InjuryHistoryDto;
import com.smartroute.smartroute1.endpoint.dto.statistics.RunHistoryDto;
import com.smartroute.smartroute1.entity.ApplicationUser;

import java.util.concurrent.CompletableFuture;

public interface StatisticsService {
    /**
     * A statistics function that returns all of the following stats for the last 365 days.
     * Total Number of Injuries
     * A list of all Injuries
     *
     * @param user the user to create statistics for
     * @return a InjuryHistoryDto including all data
     */
    InjuryHistoryDto getInjuryHistory(ApplicationUser user);

    /**
     * A statistics function that returns all of the following stats for the last 365 days.
     * Total Number of Runs
     * Total Running distance
     * Total Running time
     * A list of all Runs
     *
     * @param user the user to create statistics for
     * @return a RunHistoryDto including all data
     */
    RunHistoryDto getRunHistory(ApplicationUser user);

    /**
     * A statistics function that returns all of the following stats for the last 365 days.
     * A Map to show the Consistency Score for every day of the year
     *
     * @param user the user to create statistics for
     * @return a ConsistencyHistoryDto including all data
     */
    ConsistencyHistoryDto getConsistencyHistory(ApplicationUser user);

    /**
     * A statistics function that returns all of the following stats for the last 365 days.
     * Total Number of gymWorkouts
     * All gym workouts for the year
     *
     * @param user the user to create statistics for
     * @return a GymHistoryDto including all data
     */
    GymHistoryDto getGymHistory(ApplicationUser user);

    /**
     * delete the old Consistency Scores and recalculate them.
     * This is used after new activites are loaded in and is async
     *
     * @param user the user to reset for
     * @return A completeable future that equals null, meaning the thread is finished
     */
    CompletableFuture<Void> preLoadConsistencyHistory(ApplicationUser user);
}
