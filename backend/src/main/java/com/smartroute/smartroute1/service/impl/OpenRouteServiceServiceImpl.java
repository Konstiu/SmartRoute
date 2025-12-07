package com.smartroute.smartroute1.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.service.OpenRouteServiceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpenRouteServiceServiceImpl implements OpenRouteServiceService {

    private final WebClient webClient;
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final String orsAccessToken = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6ImQ2MDAyNmVjYmY4NTRhOWZiNThlMTI3YjY5NzAxODVlIiwiaCI6Im11cm11cjY0In0=";

    // First Name: No
    // Last Name: Answer
    // email: wrg86009@laoia.com
    // email: col600aom@ia.wrg89

    @Override
    public GeoJsonDto generateRoute(List<GeoJsonPosition> positions) {
        if (positions == null || positions.size() < 2) {
            throw new IllegalArgumentException("'coordinates' must contain at least two coordinates.");
        }

        // Convert to ORS-required format: [[lon,lat], [lon,lat], ...]
        List<List<Double>> orsCoords = positions.stream()
                .map(p -> List.of(p.getLongitude(), p.getLatitude()))
                .toList();

        try {
            String response = webClient.post()
                    .uri("https://api.openrouteservice.org/v2/directions/foot-walking/geojson")
                    .header("Authorization", orsAccessToken)
                    .bodyValue(
                            Map.of("coordinates", orsCoords)
                    )
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(response, GeoJsonDto.class);

        } catch (WebClientResponseException e) {
            LOGGER.error("ORS error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e; // DO NOT swallow
        } catch (Exception e) {
            LOGGER.error("Error contacting ORS", e);
            throw new RuntimeException("Could not call ORS", e);
        }
    }


    @Override
    public GeoJsonDto generateRoundTrip(List<GeoJsonPosition> coordinates, int length, int points, int seed) {
        if (coordinates == null || coordinates.isEmpty()) {
            throw new IllegalArgumentException("'coordinates' must contain at least one coordinate for a round trip.");
        }
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String coords = objectMapper.writeValueAsString(coordinates);

            String response = webClient.post()
                    .uri("https://api.openrouteservice.org/v2/directions/foot-walking/geojson")
                    .header("Authorization", orsAccessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue("{\"coordinates\":" + coords + "\"language\":\"en\",\"units\":\"km\",\"options\":{\"round_trip\":{\"length\":" + length + ",\"points\":" + points + ",\"seed\":" + seed + "}}" + "}")
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
}
