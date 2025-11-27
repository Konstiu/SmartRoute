package com.smartroute.smartroute1.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.GarminAccount;
import com.smartroute.smartroute1.exception.garmin.GarminAuthenticationException;
import com.smartroute.smartroute1.exception.garmin.GarminException;
import com.smartroute.smartroute1.exception.garmin.GarminNoDataException;
import com.smartroute.smartroute1.exception.garmin.GarminScriptException;
import com.smartroute.smartroute1.repository.GarminAccountRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.GarminImportService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GarminImportServiceImpl implements GarminImportService {

    @Value("${garmin.python.script.path:${user.dir}/python/python_garmin_connect.py}")
    private String pythonScriptPath;

    @Value("${garmin.python.executable:${user.dir}/python/.venv/bin/python3.12}")
    private String pythonExecutable;

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final GarminAccountRepository garminAccountRepository;

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

            GarminScriptResult result;

            if (firstLogin) {
                // for first login
                // Usage: script.py <email> <password> <activity_count>
                result = executePythonScript(email, password, activityCount);
            } else {
                // for token-based login
                // Usage: script.py  --token-json '<json>' <activity_count>
                result = executePythonScript("--token-json", garminAccount.getTokenJson(), activityCount);
            }

            // Update tokens in DB (tokens may be refreshed on every run)
            String newTokenJson = objectMapper.writeValueAsString(result.tokens);
            garminAccount.setTokenJson(newTokenJson);
            garminAccountRepository.save(garminAccount);

            // Optionally: log activities
            logActivities(result.activities);

            // TODO - Here we have to call a mapper to first return a DTO and second store the received activities properly
            return result.activities;

        } catch (GarminException e) {
            // Re-throw Garmin exceptions as-is so the exception handler can catch them
            log.error("Garmin error for user {}: {}", user.getEmail(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to sync Garmin activities for user {}", user.toString(), e);
            throw new RuntimeException("Garmin sync failed: " + e.getMessage(), e);
        }
    }

    /**
     * Executes the Python script with:
     * 1) Legacy credential mode: script.py <\email> <\password> <\activity_count>
     * 2) Inline token JSON: script.py --token-json '<\json>' <\activity_count>
     * and expects JSON:
     * { "tokens": {...}, "activities": [...] }
     */
    private GarminScriptResult executePythonScript(String first, String second, int activityCount) throws IOException, InterruptedException, GarminScriptException {

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
        log.debug("Raw JSON from Python: {}", json);

        return objectMapper.readValue(json, GarminScriptResult.class);
    }

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


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GarminScriptResult {
        public Map<String, JsonNode> tokens;
        public List<JsonNode> activities;
    }
}
