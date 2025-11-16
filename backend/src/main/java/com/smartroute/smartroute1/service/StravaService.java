package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.AthleteDetailDto;
import com.smartroute.smartroute1.endpoint.dto.StravaActivityDto;
import com.smartroute.smartroute1.endpoint.dto.ZoneDataDto;

import java.util.List;


/**
 * Interface for handling Strava-related operations.
 * Provides methods to import user activities and heart rate zone data
 * from Strava for a given user identified by email.
 */
public interface StravaService {
    /**
     * Imports a list of Strava activities for the user with the specified email.
     *
     * @param email the email of the user whose Strava activities should be imported
     * @return a list of {@link StravaActivityDto} representing the user's activities
     * @throws RuntimeException if the Strava account is not linked or API call fails
     */
    List<StravaActivityDto> importStravaActivities(String email);

    /**
     * Imports the (heart rate) zone data for the user with the specified email from Strava.
     *
     * @param email the email of the user whose heart rate zone data should be imported
     * @return a {@link ZoneDataDto} containing the user's heart rate zones
     * @throws RuntimeException if the Strava account is not linked or API call fails
     */
    ZoneDataDto importStravaZoneData(String email);

    /**
     * Imports the athlete detail data for the user with the specified email from Strava.
     *
     * @param email the email of the user whose athlete data should be imported
     * @return a {@link AthleteDetailDto} containing the athlete data
     * @throws RuntimeException if the Strava account is not linked or API call fails
     */
    AthleteDetailDto importStravaAthlete(String email);
}
