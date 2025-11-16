# SmartRoute Backend

## How to connect your Strava Account Without a Frontend

Strava uses an OAuth2 Authorization Code Flow that requires browser redirects.
Because Swagger or direct API calls cannot handle this flow, you need to perform the connection manually.

---

### Requirements 

Ensure the following values are correctly configured in `application-secrets.properties`:

- `strava.client.id`
- `strava.client.secret`

---

### Connect a Strava account to a user

1. Authenticate your API user
    Obtain a Bearer token (JWT) by sending:
    
    ```
    POST {backendBaseUrl}/api/v1/authentication
    ```
    
    with a valid request body.
    
    Use any tool like **Postman**, **Insomnia**, or **cURL**.

2. Open the following URL in your browser:
   `GET /api/v1/strava/connect`
    You will be redirected to Strava's authorization screen.

3. Log into your Strava account and approve permissions.

4. After approval, Strava redirects back to:

   ```
   {backendBaseUrl}/api/v1/strava/callback?code=...&scope=...
   ```

   However, this request **fails authentication** because the browser does not
   include your Bearer token.

5. Make a manual GET request to the /callback URL.
   Copy the `code` and `scope` parameters from the redirect URL and include the previously generated Bearer token
   in your request:

    ```
    Authorization: Bearer <jwt>
    ```

    ```
    GET {backendBaseUrl}/api/v1/strava/callback?code=XXX&scope=YYY
    ```

6. The backend processes the code, exchanges it for tokens, links your 
   Strava account, and imports:
   - Athlete Profile  
   - Activities  
   - Heart Rate Zones  
    
   If no frontend exists, the redirect will simply show a blank page.  
   Connection still succeeds.