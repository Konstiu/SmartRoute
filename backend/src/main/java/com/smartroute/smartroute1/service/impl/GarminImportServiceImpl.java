package com.smartroute.smartroute1.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ActivityStream;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.GarminAccount;
import com.smartroute.smartroute1.entity.enums.ActivityStreamSource;
import com.smartroute.smartroute1.exception.garmin.GarminAuthenticationException;
import com.smartroute.smartroute1.exception.garmin.GarminException;
import com.smartroute.smartroute1.exception.garmin.GarminNoDataException;
import com.smartroute.smartroute1.exception.garmin.GarminScriptException;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.ActivityStreamRepository;
import com.smartroute.smartroute1.repository.GarminAccountRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.ActivityProcessingService;
import com.smartroute.smartroute1.service.FitnessScoreService;
import com.smartroute.smartroute1.service.GarminImportService;
import com.smartroute.smartroute1.service.GpxService;
import com.smartroute.smartroute1.service.StatisticsService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GarminImportServiceImpl implements GarminImportService {

    private static final Map<Integer, String> ACTIVITY_TYPE_NAMES = Map.ofEntries(
            // Running
            Map.entry(1, "Run"), //Running
            Map.entry(49, "Run"), // Trail Running
            Map.entry(50, "Run"), //Treadmill Running

            // Cycling
            Map.entry(2, "Ride"), //Cycling
            Map.entry(10, "Ride"), //Mountain Biking
            Map.entry(11, "Ride"), //Road Cycling
            Map.entry(17, "Ride"), //Indoor Cycling

            // Swimming
            Map.entry(5, "Swim"), //Swimming
            Map.entry(28, "Swim"), //Open Water Swimming
            Map.entry(31, "Swim"), //Lap Swimming

            // Walking/Hiking
            Map.entry(3, "Walk"), //Hiking
            Map.entry(4, "Walk"), //Walking
            Map.entry(9, "Walk"), //Other

            // Fitness
            Map.entry(13, "Strength Training"),
            Map.entry(15, "Cardio Training"),
            Map.entry(26, "Yoga"),
            Map.entry(27, "Pilates"),
            Map.entry(29, "Stand Up Paddleboarding"),

            // Winter Sports
            Map.entry(6, "Cross Country Skiing"),
            Map.entry(7, "Alpine Skiing"),
            Map.entry(8, "Snowboarding"),

            // Water Sports
            Map.entry(14, "Rowing"),
            Map.entry(19, "Kayaking"),
            Map.entry(37, "Sailing"),
            Map.entry(39, "Surfing"),

            // Other
            Map.entry(12, "Transition"), // Triathlon transition
            Map.entry(16, "Elliptical"),
            Map.entry(18, "Golf"),
            Map.entry(20, "Inline Skating"),
            Map.entry(21, "Rock Climbing"),
            Map.entry(22, "Hang Gliding"),
            Map.entry(23, "Horseback Riding"),
            Map.entry(24, "Driving"),
            Map.entry(25, "Flying"),
            Map.entry(30, "Motorcycling"),
            Map.entry(32, "Mountaineering"),
            Map.entry(33, "Multisport"),
            Map.entry(34, "Paddling"),
            Map.entry(35, "Diving"),
            Map.entry(36, "Wakeboarding"),
            Map.entry(38, "Windsurfing"),
            Map.entry(40, "Fishing")
    );
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final GarminAccountRepository garminAccountRepository;
    private final ActivityRepository activityRepository;
    private final GpxService gpxService;
    private final FitnessScoreService fitnessScoreService;
    private final ActivityProcessingService activityProcessingService;
    private final ActivityStreamRepository activityStreamRepository;
    private final StatisticsService statisticsService;
    @Value("${garmin.python.script.path:${user.dir}/python/python_garmin_connect.py}")
    private String pythonScriptPath;
    @Value("#{T(java.lang.System).getProperty('os.name').toLowerCase().contains('win') ? "
            + "'${garmin.python.executable.windows:${user.dir}/python/.venv/Scripts/python.exe}' :"
            + "'${garmin.python.executable:${user.dir}/python/.venv/bin/python3.12}'}")
    private String pythonExecutable;

    // maps the error to a second thread, so we can read the errors form the garmin script
    private static Thread getThread(Process process, StringBuilder stderrOutput) {
        Thread stderrThread = new Thread(() -> {
            try (BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    if (stderrOutput.isEmpty()) {
                        log.error("Python stderr: {}", line);
                    } else {
                        log.debug("Python stderr: {}", line);
                    }
                    stderrOutput.append(line).append("\n");
                }
            } catch (IOException e) {
                log.warn("Failed to read python stderr", e);
            }
        }, "python-stderr-reader");
        stderrThread.setDaemon(true);
        stderrThread.start();
        return stderrThread;
    }

    @Override
    @Transactional
    public boolean isGarminConnected(String email) {
        log.trace("isGarminConnected({}) called", email);
        ApplicationUser user = userRepository.findUserByEmail(email);
        if (user == null) {
            throw new FatalBeanException("User not found: " + email);
        }

        GarminAccount garminAccount = garminAccountRepository.findByUser(user);
        if (garminAccount == null || garminAccount.getTokenJson() == null || garminAccount.getTokenJson().isBlank()) {
            log.info("No Garmin account or tokens found for user: {}", email);
            return false;
        }

        boolean valid = hasValidRefreshToken(garminAccount.getTokenJson());
        log.info("Garmin account connection status for user {}: {}", email, valid ? "connected" : "not connected");
        return valid;
    }

    @Override
    @Transactional
    public void disconnectGarminAccount(String email) {
        log.trace("disconnectGarminAccount({}) called", email);
        ApplicationUser user = userRepository.findUserByEmail(email);
        if (user == null) {
            throw new FatalBeanException("User not found: " + email);
        }
        GarminAccount garminAccount = garminAccountRepository.findByUser(user);
        if (garminAccount != null) {
            garminAccountRepository.delete(garminAccount);
            log.info("Garmin account disconnected for user: {}", email);
        }
    }

    /**
     * Sync activities for the given user.
     * First call (no tokens in DB yet):
     * - pass email and password
     * Subsequent calls:
     * - email and password can be null → uses tokens only
     */
    @Transactional
    public List<JsonNode> syncActivities(ApplicationUser user, int activityCount, String email, String password) throws GarminException {
        log.info("Starting Garmin activity sync for user {}", user);
        ApplicationUser managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("User not found: " + user.getId()));

        try {
            GarminAccount garminAccount = garminAccountRepository.findByUser(managedUser);
            boolean firstLogin = (garminAccount == null
                    || garminAccount.getTokenJson() == null
                    || garminAccount.getTokenJson().isBlank()
                    || !hasValidRefreshToken(garminAccount.getTokenJson()));


            if (firstLogin && (email == null || email.isBlank() || password == null || password.isBlank())) {
                throw new GarminAuthenticationException("Email and password are required for first-time Garmin login");
            }

            if (garminAccount == null) {
                garminAccount = new GarminAccount();
                garminAccount.setUser(managedUser);
            }

            Path result;

            if (firstLogin) {
                // for first login
                // Usage: script.py <email> <password> <activity_count>
                result = executePythonScript(email, password, activityCount);
            } else {
                // for token-based login
                // Usage: script.py  --token-base64 '<base64>' <activity_count>
                String base64Token = Base64.getEncoder().encodeToString(
                        garminAccount.getTokenJson().getBytes(StandardCharsets.UTF_8)
                );
                result = executePythonScript("--token-base64", base64Token, activityCount);
            }

            // Update tokens in DB (tokens may be refreshed on every run)
            String newTokenJson = extractTokensFromFile(result);
            garminAccount.setTokenJson(newTokenJson);
            garminAccountRepository.save(garminAccount);

            int count = processActivitiesFromFile(user, result);
            log.trace("Garmin activity sync for user {} synced {} activities", user.getEmail(), count);

            statisticsService.preLoadConsistencyHistory(user);

            return null; //result.activities;

        } catch (GarminException e) {
            // Re-throw Garmin exceptions as-is so the exception handler can catch them
            log.error("Garmin error for user {}: {}", user.getEmail(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to sync Garmin activities for user {}", user, e);
            throw new RuntimeException("Garmin sync failed: " + e.getMessage(), e);
        }
    }

    private int processActivitiesFromFile(ApplicationUser user, Path filePath) throws IOException {
        JsonFactory jsonFactory = new JsonFactory();
        int processedCount = 0;

        try (JsonParser parser = jsonFactory.createParser(filePath.toFile())) {

            // Find the "activities" array
            while (parser.nextToken() != null) {
                String fieldName = parser.currentName();

                if ("activities".equals(fieldName)) {
                    parser.nextToken(); // Move to START_ARRAY

                    // Process each activity ONE AT A TIME
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        // Read ONLY this one activity
                        JsonNode activity = objectMapper.readTree(parser);

                        processedCount++;

                        // Import it
                        importSingleGarminActivity(user, activity);

                        // At this point, 'activity' can be garbage collected
                        // The next iteration will load the NEXT activity

                        if (processedCount % 10 == 0) {
                            System.gc(); // Suggest cleanup
                        }
                    }
                }
            }
        }
        return processedCount;
    }

    private String extractTokensFromFile(Path filePath) throws IOException {
        JsonFactory jsonFactory = new JsonFactory();

        try (JsonParser parser = jsonFactory.createParser(filePath.toFile())) {
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();

                if ("tokens".equals(fieldName)) {
                    parser.nextToken(); // Move to the tokens object
                    JsonNode tokensNode = objectMapper.readTree(parser);

                    // Convert directly to JSON string
                    return objectMapper.writeValueAsString(tokensNode);
                }
            }
        }

        throw new GarminScriptException("No tokens found in result file");
    }

    /**
     * Executes the Python script with:.
     * 1) Legacy credential mode: script.py <\email> <\password> <\activity_count>
     * 2) Inline token JSON: script.py --token-json '<\json>' <\activity_count>
     * and expects JSON:
     * { "tokens": {...}, "activities": [...] }
     */
    private Path executePythonScript(String first, String second, int activityCount) throws IOException, InterruptedException, GarminScriptException {

        Process process = getProcess(first, second, activityCount);

        // Capture stderr with better error tracking
        StringBuilder stderrOutput = new StringBuilder();
        Thread stderrThread = getThread(process, stderrOutput);

        // stdout (JSON)
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }

        // ensure stderr thread finishes reading remaining data
        try {
            stderrThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Python script timed out");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String errorDetails = stderrOutput.toString();
            //log.error("Python script failed with exit code {}. Stderr output:\n{}", exitCode, errorDetails);

            String cleanError = extractErrorMessage(errorDetails);

            if (errorDetails.contains("GarminConnectAuthenticationError")
                    || errorDetails.contains("401")
                    || errorDetails.contains("Unauthorized")
                    || errorDetails.contains("authentication failed")
                    || errorDetails.contains("Login failed")) {
                throw new GarminAuthenticationException("Invalid Garmin credentials or authentication failed");
            } else if (errorDetails.contains("\"error\": \"No runs found\"")) {
                throw new GarminNoDataException("No running activities found in your Garmin account");
            } else {
                throw new GarminScriptException("Garmin sync failed: " + cleanError);
            }
        }

        log.info("Python script completed successfully");

        String json = output.toString().trim();
        JsonNode meta = objectMapper.readTree(json);

        if (!meta.has("result_file")) {
            throw new RuntimeException("Python script must return a result_file path for streaming.");
            //return objectMapper.treeToValue(meta, GarminScriptResult.class);
        }

        String resultFile = meta.path("result_file").asText(null);
        if (resultFile == null || resultFile.isBlank()) {
            throw new GarminScriptException("Python script did not return a valid result_file path");
        }

        return Path.of(resultFile);
    }

    // builds the args and executes the python script
    private Process getProcess(String first, String second, int activityCount) throws IOException {
        File scriptFile = new File(pythonScriptPath);
        if (!scriptFile.exists()) {
            throw new GarminScriptException("Python script not found at: " + pythonScriptPath);
        }

        List<String> command = new ArrayList<>();
        command.add(pythonExecutable);
        command.add(scriptFile.getAbsolutePath());
        command.add(first);
        command.add(second);
        command.add(String.valueOf(activityCount));

        //log.info("Full command: {}", String.join(" ", command));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(scriptFile.getParentFile());
        processBuilder.redirectErrorStream(false);

        Process process = processBuilder.start();
        return process;
    }


    // logs one activity on the console
    private void logActivities(List<JsonNode> activities) {
        if (activities == null) {
            log.info("No activities returned from Python.");
            return;
        }

        log.info("Total activities fetched: {}", activities.size());
        for (int i = 0; i < activities.size(); i++) {
            JsonNode act = activities.get(i);
            log.info("Activity {}: ID={}, Name={}, Start Time={}",
                    i + 1,
                    act.path("activityId").asText(),
                    act.path("activityName").asText(),
                    act.path("startTimeLocal").asText());
        }
    }

    // Extracts the error messages we get form the python script
    private String extractErrorMessage(String stderr) {
        String[] lines = stderr.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith("{") && line.contains("\"error\"")) {
                try {
                    JsonNode errorNode = objectMapper.readTree(line);
                    if (errorNode.has("error")) {
                        String error = errorNode.get("error").asText();
                        String type = errorNode.has("type") ? errorNode.get("type").asText() : "";

                        // Return a clean version without the full URL
                        if (error.contains("401") || error.contains("Unauthorized")) {
                            return "Invalid email or password";
                        }
                        return error;
                    }
                } catch (Exception ignored) {
                    // Not JSON, continue
                }
            }
        }

        for (String line : lines) {
            if (line.contains("Login failed:") || line.contains("Authentication failed:")) {
                return line.trim();
            }
            if (line.contains("\"error\":")) {
                try {
                    // Try to parse JSON error
                    JsonNode errorNode = objectMapper.readTree(line);
                    if (errorNode.has("error")) {
                        return errorNode.get("error").asText();
                    }
                } catch (Exception ignored) {
                    // Not JSON, continue
                }
            }
        }

        // If no specific message found, return first non-empty line
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("Traceback")) {
                return line;
            }
        }

        return "Unknown error occurred";
    }


    // Checks if refresh token is still valid, if now return false
    private boolean hasValidRefreshToken(String tokenJson) {
        if (tokenJson == null || tokenJson.isBlank()) {
            return false;
        }

        try {
            JsonNode node = objectMapper.readTree(tokenJson);
            if (node.has("oauth2_token.json")) {
                node = node.get("oauth2_token.json");
            }
            long refreshExpiresAt = node.path("refresh_token_expires_at").asLong(0L);
            if (refreshExpiresAt == 0L) {
                return false;
            }

            long now = Instant.now().getEpochSecond();
            // small safety margin (60s) to avoid race conditions
            return now < (refreshExpiresAt - 60);
        } catch (Exception e) {
            log.warn("Failed to parse Garmin token JSON, treating as invalid", e);
            return false;
        }
    }

    // stores only a single Garmin Activity. Merges it if the same activity is already persisted in the DB
    private void importSingleGarminActivity(ApplicationUser user, JsonNode activity) {
        JsonNode summary = activity.get("summary");
        JsonNode details = activity.get("details");

        String activityId = summary.path("activityId").asText(null);
        String activityName = summary.path("activityName").asText("Unnamed");

        boolean hasPolyline = summary.path("hasPolyline").asBoolean(false);
        if (!hasPolyline || details == null || details.get("activityDetailMetrics") == null) {
            log.info("Importing activity {} ({}) without GPS data (indoor activity)",
                    activityId, activityName);
            importActivityFromSummary(user, summary);
            return;
        }


        // Index lookup
        Map<String, Integer> idx = new HashMap<>();
        for (JsonNode d : details.get("metricDescriptors")) {
            idx.put(d.get("key").asText(), d.get("metricsIndex").asInt());
        }

        Integer latIdx = idx.get("directLatitude");
        Integer lonIdx = idx.get("directLongitude");
        Integer tsIdx = idx.get("directTimestamp");

        // Validate we have the essential data
        if (latIdx == null || lonIdx == null || tsIdx == null) {
            log.error("Missing essential metrics (lat/lon/timestamp) for activity {}",
                    summary.path("activityId").asLong());
            throw new RuntimeException("Activity missing required GPS data");
        }


        StringBuilder gpx = new StringBuilder();
        gpx.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        gpx.append("<gpx version=\"1.1\" creator=\"SmartRoute\" ");
        gpx.append("xmlns=\"http://www.topografix.com/GPX/1/1\" ");
        gpx.append("xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\">\n");
        gpx.append("  <metadata>\n");

        long startTimestamp = summary.path("startTimeLocal").asLong();
        Instant startInstant = Instant.ofEpochMilli(startTimestamp);
        gpx.append("    <time>").append(startInstant.toString()).append("</time>\n");
        gpx.append("  </metadata>\n");
        gpx.append("  <trk>\n");
        gpx.append("    <name>").append(escapeXml(summary.path("activityName").asText("Unnamed"))).append("</name>\n");
        gpx.append("    <trkseg>\n");

        int pointCount = 0;
        JsonNode metricsArray = details.get("activityDetailMetrics");
        Integer eleIdx = idx.get("directElevation");
        Integer hrIdx = idx.get("directHeartRate"); // can be null
        for (JsonNode metricPoint : metricsArray) {
            JsonNode metrics = metricPoint.get("metrics");

            // Extract values using the indices
            JsonNode latNode = metrics.get(latIdx);
            JsonNode lonNode = metrics.get(lonIdx);

            // Skip if lat/lon is null or missing
            if (latNode == null || lonNode == null
                    || latNode.isNull() || lonNode.isNull()) {
                continue;
            }

            double lat = latNode.asDouble();
            double lon = lonNode.asDouble();

            // Skip invalid points (lat/lon = 0 usually means no GPS signal)
            if (lat == 0.0 && lon == 0.0) {
                continue;
            }

            gpx.append("      <trkpt lat=\"").append(lat).append("\" lon=\"").append(lon).append("\">\n");

            // Add elevation if available
            if (eleIdx != null && !metrics.get(eleIdx).isNull()) {
                double elevation = metrics.get(eleIdx).asDouble();
                gpx.append("        <ele>").append(elevation).append("</ele>\n");
            }

            // Add timestamp
            long timestamp = metrics.get(tsIdx).asLong();
            Instant pointTime = Instant.ofEpochMilli(timestamp);
            gpx.append("        <time>").append(pointTime.toString()).append("</time>\n");

            // Add heart rate extension if available
            if (hrIdx != null && !metrics.get(hrIdx).isNull()) {
                int hr = metrics.get(hrIdx).asInt();
                if (hr > 0) {
                    gpx.append("        <extensions>\n");
                    gpx.append("          <gpxtpx:TrackPointExtension>\n");
                    gpx.append("            <gpxtpx:hr>").append(hr).append("</gpxtpx:hr>\n");
                    gpx.append("          </gpxtpx:TrackPointExtension>\n");
                    gpx.append("        </extensions>\n");
                }
            }

            gpx.append("      </trkpt>\n");
            pointCount++;
        }

        gpx.append("    </trkseg>\n");
        gpx.append("  </trk>\n");
        gpx.append("</gpx>\n");


        // Check if we have enough points
        if (pointCount < 2) {
            log.warn("Activity has insufficient GPS points ({}), may not be valid", pointCount);
        }

        // since the id is not the same as the one strava provides
        //activityId = "garmin_ping_" + summary.path("activityId").asText(null);

        log.debug("Generated GPX for Garmin activity {} ({} points)", activityId, pointCount);

        if (pointCount == 0) {
            log.warn("No valid GPS points found for activity {}", activityId);
            throw new RuntimeException("No valid GPS data in activity");
        }

        // Convert to InputStream
        ByteArrayInputStream gpxStream = new ByteArrayInputStream(
                gpx.toString().getBytes(StandardCharsets.UTF_8)
        );


        DateTimeFormatter garminFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try {
            List<Activity> storedActivities = activityRepository.findAllByUserAndStartDate(user, LocalDateTime.parse(summary.path("startTimeGMT").asText(), garminFormatter).atZone(ZoneId.of("UTC")).toInstant());
            Activity storedActivity = null;
            if (storedActivities.size() > 1) {
                float newDistance = (float) summary.path("distance").asDouble(0.0);

                for (Activity stored : storedActivities) {
                    float storedDistance = stored.getDistance();
                    float distanceDiff = Math.abs(storedDistance - newDistance);

                    if (distanceDiff <= 1000) {
                        storedActivity = stored;
                        break;
                    }
                }
            } else if (storedActivities.size() == 1) {
                storedActivity = storedActivities.get(0);
            }
            Activity toSave;

            if (storedActivity != null) {
                importActivityFromSummary(user, summary);
                return;

            } else {
                Activity imported = gpxService.importStravaGpxFile(gpxStream, user.getEmail());
                int typeId = summary.path("activityType").path("typeId").asInt();
                String activityTypeName = ACTIVITY_TYPE_NAMES.getOrDefault(typeId, "Activity");
                imported.setType(activityTypeName);
                imported.setSportType(activityTypeName);

                String startTimeGmt = summary.path("startTimeGMT").asText();
                imported.setStartDate(LocalDateTime.parse(startTimeGmt, garminFormatter).atZone(ZoneId.of("UTC")).toInstant());

                String startTimeLocal = summary.path("startTimeLocal").asText();
                imported.setStartDateLocal(LocalDateTime.parse(startTimeLocal, garminFormatter).atZone(ZoneId.of("UTC")).toInstant());
                imported.setExternalId(imported.getExternalId());

                // No existing activity → just save the imported one
                toSave = imported;
            }

            //toSave.setExternalId(activityId);
            toSave.setGarminActivityTrainingsLoad(summary.path("activityTrainingLoad").asDouble(0.0));

            activityRepository.save(toSave);
            log.info("Successfully imported Garmin activity {} ({} points) for user {}",
                    activityId, pointCount, user.getEmail());
        } catch (Exception e) {
            log.error("Failed to import Garmin activity {}", activityId, e);
            throw new RuntimeException("Failed to import activity: " + e.getMessage(), e);
        }
    }

    /**
     * Import activity directly from Garmin summary data (for activities without GPS).
     */
    private void importActivityFromSummary(ApplicationUser user, JsonNode summary) {
        Activity activity = new Activity();
        activity.setUser(user);

        // Basic info
        activity.setName(summary.path("activityName").asText("Unnamed Activity"));
        int typeId = summary.path("activityType").path("typeId").asInt();
        String activityTypeName = ACTIVITY_TYPE_NAMES.getOrDefault(typeId, "Activity");
        activity.setType(activityTypeName);
        activity.setSportType(activityTypeName);


        DateTimeFormatter garminFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String startTimeGmt = summary.path("startTimeGMT").asText();
        activity.setStartDate(LocalDateTime.parse(startTimeGmt, garminFormatter).atZone(ZoneId.of("UTC")).toInstant());
        String startTimeLocal = summary.path("startTimeLocal").asText();
        activity.setStartDateLocal(LocalDateTime.parse(startTimeLocal, garminFormatter).atZone(ZoneId.of("UTC")).toInstant());


        float distance = (float) summary.path("distance").asDouble(0.0);
        int elapsedTime = (int) summary.path("elapsedDuration").asDouble(0.0);
        int movingTime = (int) summary.path("movingDuration").asDouble(0.0);

        activity.setDistance(distance);
        activity.setElapsedTime(elapsedTime);
        activity.setMovingTime(movingTime > 0 ? movingTime : elapsedTime);

        float elevationGain = (float) summary.path("elevationGain").asDouble(0.0);
        activity.setTotalElevationGain(elevationGain);

        float avgSpeed = (float) summary.path("averageSpeed").asDouble(0.0);
        float maxSpeed = (float) summary.path("maxSpeed").asDouble(0.0);
        activity.setAverageSpeed(avgSpeed);
        activity.setMaxSpeed(maxSpeed);

        float avgHr = (float) summary.path("averageHR").asDouble(0.0);
        float maxHr = (float) summary.path("maxHR").asDouble(0.0);
        activity.setAverageHeartrate(avgHr);
        activity.setMaxHeartrate(maxHr);

        activity.setSummaryPolyline(null);

        int sessionLoad;
        int actualMovingTime = movingTime > 0 ? movingTime : elapsedTime;
        long startTimestamp = summary.path("startTimeLocal").asLong();

        if (maxHr > 0 && avgHr > 0 && actualMovingTime > 0) {
            List<Float> heartRates = new ArrayList<>();
            List<Float> timestamps = new ArrayList<>();

            int numPoints = Math.max(1, actualMovingTime / 60);
            float timeStep = (float) actualMovingTime / numPoints;

            for (int i = 0; i < numPoints; i++) {
                heartRates.add(avgHr);
                timestamps.add((float) startTimestamp / 1000.0f + (i * timeStep));
            }

            sessionLoad = fitnessScoreService.calculateSessionLoad(
                    heartRates,
                    timestamps,
                    activity
            );

            // Fetch weather data
            activityProcessingService.fetchWeatherForActivity(activity);

            // Calculate time in hr-zones
            Map<Integer, Float> timeInZones = fitnessScoreService.calculateTimeInZones(heartRates, timestamps, user);

            // Set time in hr-zones
            timeInZones.forEach((zone, time) -> {
                switch (zone) {
                    case 1 -> activity.setTimeZ1(Math.round(time));
                    case 2 -> activity.setTimeZ2(Math.round(time));
                    case 3 -> activity.setTimeZ3(Math.round(time));
                    case 4 -> activity.setTimeZ4(Math.round(time));
                    case 5 -> activity.setTimeZ5(Math.round(time));
                    default -> throw new IllegalStateException("Unexpected value: " + zone);
                }
            });

            // Create activity streams
            ActivityStream activityStream = activityProcessingService.createActivityStream(
                    timestamps.stream().mapToDouble(Float::doubleValue).boxed().toList(),
                    null,
                    heartRates.stream().mapToDouble(Float::doubleValue).boxed().toList(),
                    ActivityStreamSource.GARMIN);

            if (activityStream != null) {
                activityStreamRepository.save(activityStream);
                activity.setActivityStream(activityStream);
            }

            log.debug("Calculated session load using HR data (avg={}, max={}) for activity {}",
                    avgHr, maxHr, summary.path("activityId").asLong());
        } else {
            // No heart rate data - fall back to distance/time based method
            sessionLoad = fitnessScoreService.calculateSessionLoad(
                    distance,
                    actualMovingTime,
                    elevationGain,
                    activity.getSportType()
            );

            log.debug("Calculated session load using distance/time method for activity {}",
                    summary.path("activityId").asLong());
        }

        activity.setSessionLoad(sessionLoad);
        activity.setGarminActivityTrainingsLoad(summary.path("activityTrainingLoad").asDouble());

        Activity saved;
        List<Activity> storedActivities = activityRepository.findAllByUserAndStartDate(user, activity.getStartDate());
        Activity storedActivity = null;
        if (storedActivities.size() > 1) {
            float newDistance = activity.getDistance();

            for (Activity stored : storedActivities) {
                float storedDistance = stored.getDistance();
                float distanceDiff = Math.abs(storedDistance - newDistance);

                if (distanceDiff <= 1000) {
                    storedActivity = stored;
                    break;
                }
            }
        } else if (storedActivities.size() == 1) {
            storedActivity = storedActivities.get(0);
        }


        if (storedActivity == null) {
            saved = activityRepository.save(activity);
        } else {
            storedActivity.setGarminActivityTrainingsLoad(summary.path("activityTrainingLoad").asDouble());
            storedActivity.setTotalElevationGain(activity.getTotalElevationGain());
            storedActivity.setAverageSpeed(activity.getAverageSpeed());
            storedActivity.setMaxSpeed(activity.getMaxSpeed());
            storedActivity.setAverageHeartrate(activity.getAverageHeartrate());

            // only update session load if strava suffer score was not set before
            if (storedActivity.getSufferScore() == null) {
                storedActivity.setSessionLoad(activity.getSessionLoad());
            }

            storedActivity.setStartDate(activity.getStartDate());
            storedActivity.setElapsedTime(activity.getElapsedTime());
            storedActivity.setMovingTime(activity.getMovingTime());
            storedActivity.setMaxHeartrate(activity.getMaxHeartrate());
            storedActivity.setExternalId(storedActivity.getExternalId());
            storedActivity.setSummaryPolyline(storedActivity.getSummaryPolyline());
            storedActivity.setAverageWatts(storedActivity.getAverageWatts());
            storedActivity.setKilojoules(storedActivity.getKilojoules());
            storedActivity.setStravaId(storedActivity.getStravaId());
            storedActivity.setSufferScore(storedActivity.getSufferScore());
            storedActivity.setSportType(storedActivity.getSportType());


            saved = activityRepository.save(storedActivity);
        }

        log.info("Successfully imported Garmin activity {} (no GPS) for user {}: {} - {}m, {}s, HR avg/max={}/{}, sessionLoad={}",
                summary.path("activityId").asLong(),
                user.getEmail(),
                saved.getName(),
                (int) distance,
                elapsedTime,
                (int) avgHr,
                (int) maxHr,
                sessionLoad);
    }

    // Helper method to escape XML special characters
    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GarminScriptResult {
        public Map<String, JsonNode> tokens;
        public List<JsonNode> activities;
    }
}