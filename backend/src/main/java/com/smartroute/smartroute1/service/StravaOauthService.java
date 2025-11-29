package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.StravaAccountConnectionStateDto;
import com.smartroute.smartroute1.endpoint.dto.StravaTokenResponseDto;
import com.smartroute.smartroute1.entity.StravaAccount;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service for handling Strava OAuth2 operations.
 *
 * <p>
 * Provides methods to exchange an authorization code for an access token,
 * and to ensure that an access token is valid (refreshing it if necessary).
 * </p>
 */
public interface StravaOauthService {

    class StravaOauthState {
        public String email;
        public String origin;
    }

    /**
     * Creates and stores a unique state to trace the Strava OAuth request back to a user
     * and a frontend origin.
     *
     * @param email  the user's email
     * @param origin the frontend origin
     * @return a unique state identifier
     */
    String createState(String email, String origin);

    /**
     * Retrieves an OAuth state and deletes corresponding entry from the state map.
     *
     * @param state the state identifier
     * @return a StravaOauthState containing the user's email and the frontend origin
     */
    StravaOauthState getState(String state);

    /**
     * Disconnects a Strava Account from the application.
     *
     * <p>
     * Revokes access to the athletes data at the Strava API and
     * removes the corresponding StravaAccount entry in the database.
     * </p>
     *
     * @param email the user's email
     * @return the Strava Account connection state
     */
    StravaAccountConnectionStateDto disconnectStravaAccount(String email);

    /**
     * Returns the Strava connection state for the specified user.
     *
     * @param email the user's email
     * @return a StravaAccountConnectionDto containing connection state information
     */
    StravaAccountConnectionStateDto getConnectionState(String email);

    /**
     * Exchanges the authorization code received from Strava for an access token and refresh token.
     *
     * <p>
     * This method also saves the access credentials to the database for the specified user.
     * </p>
     *
     * @param code  the authorization code received from Strava OAuth2 callback
     * @param scope the requested scopes for the access token
     * @param email the email of the user
     * @return a {@link StravaTokenResponseDto} containing the access token, refresh token, and related metadata
     * @throws ResponseStatusException if the exchange fails or user is not found
     */
    StravaTokenResponseDto exchangeCodeForToken(String code, String scope, String email);

    /**
     * Ensures that the access token of the given Strava account is valid.
     *
     * <p>
     * If the token is expired, it is refreshed automatically.
     * Returns a token that can be used for API requests.
     * </p>
     *
     * @param account the {@link StravaAccount} for which to ensure a valid access token
     * @return a valid access token string
     */
    String ensureValidAccessToken(StravaAccount account);
}
