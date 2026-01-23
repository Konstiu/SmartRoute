package com.smartroute.smartroute1.unittest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.*;
import com.smartroute.smartroute1.service.impl.JuliaPlannerClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class JuliaPlannerClientTest {

    private MockWebServer server;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void fitThenScoreTemplate_contractTest_requestAndResponseShapes() throws Exception {
        // --- enqueue: 1) fit-user response ---
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                    {
                      "ok": true,
                      "b": 40.0,
                      "m": [0.0,1,1,1,1,1,1],
                      "sigma0": 0.25,
                      "sigmaK": [1,1,1,1,1,1,1],
                      "betaFat": 0.0
                    }
                """));

        // --- enqueue: 2) score-template response ---
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                    {
                      "avgUtility": 123.4,
                      "tsbDists": [
                        {"p10":-30,"p50":-20,"p90":-10,"mean":-20,"std":5},
                        {"p10":-29,"p50":-19,"p90": -9,"mean":-19,"std":5},
                        {"p10":-28,"p50":-18,"p90": -8,"mean":-18,"std":5},
                        {"p10":-27,"p50":-17,"p90": -7,"mean":-17,"std":5},
                        {"p10":-26,"p50":-16,"p90": -6,"mean":-16,"std":5},
                        {"p10":-25,"p50":-15,"p90": -5,"mean":-15,"std":5},
                        {"p10":-24,"p50":-14,"p90": -4,"mean":-14,"std":5}
                      ]
                    }
                """));

        String baseUrl = server.url("/").toString();
        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();

        JuliaPlannerClient client = new JuliaPlannerClient(webClient);
        ReflectionTestUtils.setField(client, "enabled", true);

        String startDate = LocalDate.of(2026, 1, 20).toString();

        FitUserModelRequest fitReq = new FitUserModelRequest(
                "123", "INTERMEDIATE", List.of(), 40.0, 35.0, 42L
        );

        Optional<FitUserModelResponse> fitResp = client.fitUserModel(fitReq);

        JuliaScoreTemplateRequest scoreReq = new JuliaScoreTemplateRequest(
                "123",
                startDate,
                List.of("EASY_RUN","REST_DAY","TEMPO_RUN","REST_DAY","INTERVAL_RUN","REST_DAY","LONG_RUN"),
                List.of("EASY_RUN","REST_DAY","TEMPO_RUN","REST_DAY","INTERVAL_RUN","REST_DAY","LONG_RUN"),
                40.0,
                35.0,
                List.of(10, 20, 30),
                "INTERMEDIATE",
                0.2,
                65,
                Arrays.asList(0.8, 0.7, 0.55, null, 0.4, 0.9, 0.6),
                80,
                43L,
                1.1,
                40.0,
                List.of(0.0,1.0,1.0,1.0,1.0,1.0,1.0),
                0.25,
                List.of(1.0,1.0,1.0,1.0,1.0,1.0,1.0),
                0.0
        );

        Optional<JuliaScoreTemplateResponse> scoreResp = client.scoreTemplate(scoreReq);

        // --- verify requests ---
        RecordedRequest r1 = server.takeRequest(2, TimeUnit.SECONDS);
        RecordedRequest r2 = server.takeRequest(2, TimeUnit.SECONDS);

        // --- parse request bodies ---
        assertNotNull(r1);
        assertNotNull(r2);

        JsonNode fitJson = om.readTree(r1.getBody().readString(StandardCharsets.UTF_8));
        JsonNode scoreJson = om.readTree(r2.getBody().readString(StandardCharsets.UTF_8));

        assertAll("fitThenScoreTemplate_contractTest",
                () -> assertAll("fitUserModel response",
                        () -> assertTrue(fitResp.isPresent(), "fitUserModel should return a response"),
                        () -> assertEquals(40.0, fitResp.get().b(), 1e-9)
                ),

                () -> assertAll("scoreTemplate response",
                        () -> assertTrue(scoreResp.isPresent(), "scoreTemplate should return a response"),
                        () -> assertEquals(123.4, scoreResp.get().getAvgUtility(), 1e-9),
                        () -> assertNotNull(scoreResp.get().getTsbDists()),
                        () -> assertEquals(7, scoreResp.get().getTsbDists().size(), "tsbDists should be size 7"),
                        () -> assertEquals(-30.0, scoreResp.get().getTsbDists().get(0).getP10(), 1e-9),
                        () -> assertEquals(-14.0, scoreResp.get().getTsbDists().get(6).getP50(), 1e-9),
                        () -> assertEquals(5.0, scoreResp.get().getTsbDists().get(3).getStd(), 1e-9)
                ),

                () -> assertAll("request routing",
                        () -> assertEquals("POST", r1.getMethod()),
                        () -> assertEquals("/model/fit-user", r1.getPath()),
                        () -> assertEquals("POST", r2.getMethod()),
                        () -> assertEquals("/plan/score-template", r2.getPath())
                ),

                () -> assertAll("fit-user request JSON contract",
                        () -> assertEquals("123", fitJson.get("userId").asText()),
                        () -> assertEquals("INTERMEDIATE", fitJson.get("experienceLevel").asText()),
                        () -> assertTrue(fitJson.get("days").isArray()),
                        () -> assertEquals(0, fitJson.get("days").size()),
                        () -> assertEquals(40.0, fitJson.get("ctl0").asDouble(), 1e-9),
                        () -> assertEquals(35.0, fitJson.get("atl0").asDouble(), 1e-9),
                        () -> assertEquals(42L, fitJson.get("seed").asLong())
                ),

                () -> assertAll("score-template request JSON contract",
                        () -> assertEquals("123", scoreJson.get("userId").asText()),
                        () -> assertEquals(startDate, scoreJson.get("startDate").asText()),
                        () -> assertTrue(scoreJson.has("effectiveTemplate")),
                        () -> assertEquals(7, scoreJson.get("effectiveTemplate").size()),
                        () -> assertEquals("EASY_RUN", scoreJson.get("effectiveTemplate").get(0).asText()),
                        () -> assertEquals("LONG_RUN", scoreJson.get("effectiveTemplate").get(6).asText()),
                        () -> assertTrue(scoreJson.has("weatherScores")),
                        () -> assertEquals(7, scoreJson.get("weatherScores").size()),
                        () -> assertTrue(scoreJson.get("weatherScores").get(3).isNull(), "weatherScores[3] should be null"),
                        () -> assertEquals(80, scoreJson.get("sims").asInt()),
                        () -> assertEquals(43L, scoreJson.get("seed").asLong()),
                        () -> assertEquals(1.1, scoreJson.get("baseUncertaintyMult").asDouble(), 1e-9),
                        // learned-model fields names must match what Julia expects
                        () -> assertEquals(40.0, scoreJson.get("b").asDouble(), 1e-9),
                        () -> assertEquals(0.25, scoreJson.get("sigma0").asDouble(), 1e-9),
                        () -> assertTrue(scoreJson.get("m").isArray()),
                        () -> assertEquals(7, scoreJson.get("m").size()),
                        () -> assertTrue(scoreJson.get("sigmaK").isArray()),
                        () -> assertEquals(7, scoreJson.get("sigmaK").size()),
                        () -> assertEquals(0.0, scoreJson.get("betaFat").asDouble(), 1e-9)
                )
        );
    }

    @Test
    void scoreTemplate_returnsEmptyWhenDisabled_andDoesNotCallServer() throws Exception {
        String baseUrl = server.url("/").toString();
        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();

        JuliaPlannerClient client = new JuliaPlannerClient(webClient);
        ReflectionTestUtils.setField(client, "enabled", false);

        JuliaScoreTemplateRequest scoreReq = new JuliaScoreTemplateRequest(
                "123",
                LocalDate.of(2026, 1, 20).toString(),
                List.of("EASY_RUN","REST_DAY","TEMPO_RUN","REST_DAY","INTERVAL_RUN","REST_DAY","LONG_RUN"),
                List.of("EASY_RUN","REST_DAY","TEMPO_RUN","REST_DAY","INTERVAL_RUN","REST_DAY","LONG_RUN"),
                40.0, 35.0, List.of(10, 20, 30),
                "INTERMEDIATE", 0.2, 65,
                Arrays.asList(0.8, 0.7, 0.55, null, 0.4, 0.9, 0.6),
                80, 43L, 1.1,
                null, null, null, null, null
        );

        Optional<JuliaScoreTemplateResponse> resp = client.scoreTemplate(scoreReq);
        RecordedRequest r = server.takeRequest(250, TimeUnit.MILLISECONDS);

        assertAll("scoreTemplate disabled behavior",
                () -> assertTrue(resp.isEmpty(), "When enabled=false, scoreTemplate must return Optional.empty()"),
                () -> assertNull(r, "Client should not call server when enabled=false")
        );
    }

    @Test
    void scoreTemplate_returnsEmptyOn4xx_andStillSendsRequest() throws Exception {
        // Only enqueue the score-template call (no fit needed here)
        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                    {"error":"bad request","details":"missing field or invalid type"}
                """));

        String baseUrl = server.url("/").toString();
        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();

        JuliaPlannerClient client = new JuliaPlannerClient(webClient);
        ReflectionTestUtils.setField(client, "enabled", true);

        String startDate = LocalDate.of(2026, 1, 20).toString();

        JuliaScoreTemplateRequest scoreReq = new JuliaScoreTemplateRequest(
                "123",
                startDate,
                List.of("EASY_RUN","REST_DAY","TEMPO_RUN","REST_DAY","INTERVAL_RUN","REST_DAY","LONG_RUN"),
                List.of("EASY_RUN","REST_DAY","TEMPO_RUN","REST_DAY","INTERVAL_RUN","REST_DAY","LONG_RUN"),
                40.0,
                35.0,
                List.of(10, 20, 30),
                "INTERMEDIATE",
                0.2,
                65,
                Arrays.asList(0.8, 0.7, 0.55, null, 0.4, 0.9, 0.6),
                80,
                43L,
                1.1,
                40.0,
                List.of(0.0,1.0,1.0,1.0,1.0,1.0,1.0),
                0.25,
                List.of(1.0,1.0,1.0,1.0,1.0,1.0,1.0),
                0.0
        );

        Optional<JuliaScoreTemplateResponse> resp = client.scoreTemplate(scoreReq);

        RecordedRequest r = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(r, "Expected a request to be sent to the server");

        JsonNode scoreJson = om.readTree(r.getBody().readString(StandardCharsets.UTF_8));

        assertAll("scoreTemplate 4xx behavior",
                () -> assertTrue(resp.isEmpty(), "On 4xx, client currently swallows error and returns Optional.empty()"),
                () -> assertEquals("POST", r.getMethod()),
                () -> assertEquals("/plan/score-template", r.getPath()),
                () -> assertEquals("123", scoreJson.get("userId").asText()),
                () -> assertEquals(startDate, scoreJson.get("startDate").asText()),
                () -> assertTrue(scoreJson.get("weatherScores").get(3).isNull())
        );
    }
}
