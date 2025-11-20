package com.smartroute.smartroute1.endpoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartroute.smartroute1.service.GarminImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.invoke.MethodHandles;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/garmin")
@RequiredArgsConstructor
public class GarminConnectEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final GarminImportService garminImportService;

    /**
     * triggers the python script and returns all fetched activities.
     */
    @PostMapping("/sync")
    @Secured("ROLE_USER")
    public ResponseEntity<List<JsonNode>> syncActivities() {
        LOGGER.info("GET /api/v1/garmin/sync");
        // TODO : here wer have to do the actual computation
        // we have to fetch from the database if there is already a token, then we don't need a password and the email only if there is no token or the token is too old, we may want to ask for the credentials
        // currently it only returns the raw json in format of {token:..., activities:[]}
        List<JsonNode> activities = garminImportService.syncActivities(1L, 1, "dummyemail", "dummypassword");

        return ResponseEntity.ok(activities);
    }
}
