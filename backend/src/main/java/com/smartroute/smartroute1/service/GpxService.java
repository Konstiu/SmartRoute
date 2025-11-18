package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.entity.StravaActivity;
import com.smartroute.smartroute1.exception.ValidationException;

import java.io.InputStream;

public interface GpxService {

    /**
     * Imports a GPX file exported from Strava.
     * All the necessary data is calculated and stored in the database.
     *
     * @param gpxStream InputStream of the GPX file to be imported.
     * @param email The email of the user importing the GPX file.
     * @return The imported StravaActivity entity.
     * @throws ValidationException if the GPX file is invalid or any validation error occurs.
     */
    StravaActivity importStravaGpxFile(InputStream gpxStream, String email) throws ValidationException;

}
