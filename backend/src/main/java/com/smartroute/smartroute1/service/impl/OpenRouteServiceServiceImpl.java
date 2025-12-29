package com.smartroute.smartroute1.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.service.OpenRouteServiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenRouteServiceServiceImpl implements OpenRouteServiceService {

    private final WebClient webClient;
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final String orsAccessToken = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6ImQ2MDAyNmVjYmY4NTRhOWZiNThlMTI3YjY5NzAxODVlIiwiaCI6Im11cm11cjY0In0=";

    @Override
    public GeoJsonDto generateRoute(List<GeoJsonPosition> coordinates) {
        if (coordinates == null || coordinates.size() < 2) {
            throw new IllegalArgumentException("'coordinates' must contain at least two coordinates.");
        }
        ObjectMapper objectMapper = new ObjectMapper();
        // ORS expects coordinates as [lon, lat]
        List<List<Double>> orsCoords = coordinates.stream()
                .map(pos -> List.of(pos.getLongitude(), pos.getLatitude()))
                .toList();
        try {
            String coords = objectMapper.writeValueAsString(orsCoords);

            String response = webClient.post()
                    .uri("https://ors.unterweger.tech/ors/v2/directions/foot-walking/geojson")
                    .header("Authorization", orsAccessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue("{\"coordinates\":" + coords + "}")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return objectMapper.readValue(response, GeoJsonDto.class);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error processing json: ", e);
        } catch (WebClientResponseException e) {
            LOGGER.error("Failed generating route. Response from server: ", e);
        }
        return null;
    }

    @Override
    public GeoJsonDto generateRoundTrip(List<GeoJsonPosition> coordinates, int length, int points, int seed) {
        if (coordinates == null || coordinates.isEmpty()) {
            throw new IllegalArgumentException("'coordinates' must contain at least one coordinate for a round trip.");
        }
        ObjectMapper objectMapper = new ObjectMapper();
        // ORS expects coordinates as [lon, lat]
        List<List<Double>> orsCoords = coordinates.stream()
                .map(pos -> List.of(pos.getLongitude(), pos.getLatitude()))
                .toList();
        try {
            String coords = objectMapper.writeValueAsString(orsCoords);

            String response = webClient.post()
                    .uri("https://ors.unterweger.tech/ors/v2/directions/foot-walking/geojson")
                    .header("Content-Type", "application/json")
                    .bodyValue("{\"coordinates\":" + coords + ",\"elevation\":true,\"language\":\"en\",\"units\":\"m\",\"options\":{\"round_trip\":{\"length\":" + length + ",\"points\":" + points + ",\"seed\":" + seed + "}}}")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return objectMapper.readValue(response, GeoJsonDto.class);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error processing json: ", e);
        } catch (WebClientResponseException e) {
            LOGGER.error("Failed generating route. Response from server: ", e);
        }
        return null;
    }

    @Override
    public GeoJsonDto generateRouteAvoidingPolygon(List<GeoJsonPosition> waypoints, List<List<Double>> avoidPolygon) {
        ObjectMapper objectMapper = new ObjectMapper();

        // Build the request body with avoid polygon
        Map<String, Object> requestBody = new HashMap<>();

        // Add coordinates (ORS expects [lon, lat])
        List<List<Double>> coordinates = waypoints.stream()
                .map(wp -> List.of(wp.getLongitude(), wp.getLatitude()))
                .toList();
        requestBody.put("coordinates", coordinates);

        // Add options with avoid_polygons
        Map<String, Object> options = new HashMap<>();
        Map<String, Object> avoidPolygons = new HashMap<>();
        avoidPolygons.put("type", "Polygon");
        avoidPolygons.put("coordinates", List.of(avoidPolygon));
        options.put("avoid_polygons", avoidPolygons);
        requestBody.put("options", options);

        log.info("Requesting route with avoid area: {}", requestBody);

        try {
            String response = webClient.post()
                    .uri("https://ors.unterweger.tech/ors/v2/directions/foot-walking/geojson")
                    .header("Authorization", orsAccessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return objectMapper.readValue(response, GeoJsonDto.class);

        } catch (JsonProcessingException e) {
            LOGGER.error("Error processing JSON response from ORS with avoid area", e);
            throw new RuntimeException("Failed to parse ORS response", e);
        } catch (WebClientResponseException e) {
            LOGGER.error("ORS API error with avoid area. Status: {}, Response: {}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("ORS API request failed", e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error calling ORS with avoid area", e);
            throw new RuntimeException("Unexpected error in ORS request", e);
        }
    }
}