package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.StravaStreamDto;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;

import java.time.Instant;
import java.util.List;

public interface FitnessScoreService {
    /**
     * Calculates the fitnessScore for a user on a given day by calculating
     * the sum of all sessionLoads from activities from that day.
     *
     * @param day the day to calculate the fitnessScore for
     * @param user the user to calculate the fitnessScore for
     * @return the fitnessScore
     */
    int calculateFitnessScore(Instant day, ApplicationUser user);

    /**
     * Calculates the sessionLoad from a Strava sufferScore.
     *
     * @param sufferScore the Strava suffer score
     * @param totalElevationGain the total elevation gain for the activity
     * @return the calculated sessionLoad
     */
    Integer calculateSessionLoad(int sufferScore, float totalElevationGain);

    /**
     * Calculates the sessionLoad from a heartRateStream and an activity.
     *
     * @param heartRateStream the heartRate data used to calculate the sessionLoad
     * @param activity the activity to calculate the sessionLoad for
     * @return the calculated sessionLoad
     */
    Integer calculateSessionLoad(List<StravaStreamDto> heartRateStream, Activity activity);

    /**
     * Calculates the sessionLoad from power and FTP.
     *
     * @param ftp the users FTP
     * @param movingTime the moving time of the activity
     * @param averageWatts the average power for the activity
     * @param totalElevationGain the total elevation gain for the activity
     * @return the calculated sessionLoad
     */
    Integer calculateSessionLoad(int ftp, int movingTime, float averageWatts, float totalElevationGain);

    /**
     * Calculates the sessionLoad from energy and weight.
     *
     * @param kilojoules the energy of the activity
     * @param weight the users FTP
     * @param totalElevationGain the total elevation gain for the activity
     * @return the calculated sessionLoad
     */
    Integer calculateSessionLoad(float kilojoules, float weight, float totalElevationGain);

    /**
     * Calculates the sessionLoad from distance and moving time.
     *
     * @param distance the distance of the activity
     * @param movingTime the moving time of the activity
     * @param totalElevationGain the total elevation gain for the activity
     * @return the calculated sessionLoad
     */
    Integer calculateSessionLoad(float distance, int movingTime, float totalElevationGain);
}
