package com.smartroute.smartroute1.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.GarminAccount;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GarminImportServiceImpl implements GarminImportService {

    @Value("${garmin.python.script.path:src/main/scripts/python_script.py}")
    private String pythonScriptPath;

    @Value("${garmin.python.executable:.venv/bin/python3.12}")
    private String pythonExecutable;

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final GarminAccountRepository garminAccountRepository;

    /**
     * Sync activities for the given user.
     * First call (no tokens in DB yet):
     * - pass email + password
     * Subsequent calls:
     * - email + password can be null → uses tokens only
     */
    public List<JsonNode> syncActivities(Long userId, int activityCount, String email, String password) {
        log.info("Starting Garmin activity sync for user {}", userId);

        try {
            ApplicationUser user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

            GarminAccount garminAccount = garminAccountRepository.findByUser(user);
            boolean firstLogin = (garminAccount == null
                    || garminAccount.getTokenJson() == null
                    || garminAccount.getTokenJson().isBlank());

            if (firstLogin && (email == null || email.isBlank() || password == null || password.isBlank())) {
                throw new IllegalArgumentException("Email and password are required for first-time Garmin login");
            }

            if (garminAccount == null) {
                garminAccount = new GarminAccount();
                garminAccount.setUser(user);
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

        } catch (Exception e) {
            log.error("Failed to sync Garmin activities for user {}", userId, e);
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
    private GarminScriptResult executePythonScript(String first, String second, int activityCount) throws IOException, InterruptedException {
        log.info("Executing Python script: {}", pythonScriptPath);

        File scriptFile = new File(pythonScriptPath);
        if (!scriptFile.exists()) {
            throw new IOException("Python script not found at: " + pythonScriptPath);
        }

        List<String> command = new ArrayList<>();
        command.add(pythonExecutable);
        command.add(scriptFile.getAbsolutePath());
        command.add(first);
        command.add(second);
        command.add(String.valueOf(activityCount));


        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(scriptFile.getParentFile());
        processBuilder.redirectErrorStream(false);

        Process process = processBuilder.start();

        // Consume stderr concurrently so the child process cannot block
        Thread stderrThread = new Thread(() -> {
            try (BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    log.info("Python: {}", line);
                }
            } catch (IOException e) {
                log.warn("Failed to read python stderr", e);
            }
        }, "python-stderr-reader");
        stderrThread.setDaemon(true);
        stderrThread.start();

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

        if (process.exitValue() != 0) {
            throw new RuntimeException("Python script failed with exit code " + process.exitValue());
        }

        log.info("Python script completed successfully");

        String json = output.toString().trim();
        log.debug("Raw JSON from Python: {}", json);

        return objectMapper.readValue(json, GarminScriptResult.class);
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GarminScriptResult {
        public Map<String, JsonNode> tokens;
        public List<JsonNode> activities;
    }
}
