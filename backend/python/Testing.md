## Manual Integration Tests for Garmin Import

In addition to the automated tests, the interaction between the backend, the Python script and (in the real setup) Garmin was verified by manual end-to-end tests. The following scenarios were executed via the `/api/v1/garmin/sync` endpoint:

1. **First login with valid Garmin credentials**

    - Call `POST /api/v1/garmin/sync` with a valid Garmin email, password and a positive `count`.
    - Observed:
        - A `GarminAccount` is created/updated for the logged-in user.
        - The `tokenJson` field in the database contains a valid token object.
        - The response body contains the requested number of activities in the expected JSON structure (including `activityId`, `activityName`, `summary`, `details`).

2. **Subsequent sync using only the stored token**

    - First, perform a successful sync as in test case 1 to ensure a token is stored.
    - Then call `POST /api/v1/garmin/sync` **without** sending `garminEmail` or `garminPassword`, only `count`.
    - Observed:
        - The service reuses the existing token from the database (no new credentials required).
        - The Python script is invoked in “token mode”.
        - The response again returns activities in the expected format with HTTP status `200 OK`.

3. **Expired token (simulated) without credentials**

    - After a successful initial sync, the `tokenJson` in the database is manually modified so that the refresh token expiry (`refresh_token_expires_at`) lies in the past.
    - Then call `POST /api/v1/garmin/sync` without email/password.
    - Observed:
        - The service detects that the stored token is no longer valid.
        - A `GarminAuthenticationException` is raised and returned as an appropriate HTTP error.
        - The response indicates that credentials are required for a new login.

4. **Invalid or missing credentials**

    - Call `POST /api/v1/garmin/sync` with deliberately wrong or incomplete credentials (e.g. wrong password, empty email, missing password).
    - Observed:
        - The Python script reports a login error.
        - The service maps this to an authentication error.
        - The endpoint returns an HTTP `401 Unauthorized` status.

5. **Security / authorization behaviour**

    - Call `POST /api/v1/garmin/sync`:
        - without any authentication header, and
        - with a logged-in user that does not have the required `USER` role.
    - Observed:
        - In both cases the request is rejected with HTTP `403 Forbidden`.
        - Only authenticated users with the correct role can access the endpoint.

These manual integration tests confirm that the overall flow - from HTTP endpoint, through the Java service, to the Python script and back - behaves correctly for successful logins, token reuse, expired tokens, invalid credentials and security-related edge cases.
