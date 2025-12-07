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
    public GeoJsonDto generateRoute(List<GeoJsonPosition> coordinates) {
        if (coordinates == null || coordinates.size() < 2) {
            throw new IllegalArgumentException("'coordinates' must contain at least two coordinates.");
        }

        try {
            // ORS expects [lon, lat]
            List<List<Double>> orsCoords = coordinates.stream()
                    .map(p -> List.of(p.getLongitude(), p.getLatitude()))
                    .toList();

            Map<String, Object> body = Map.of(
                    "coordinates", orsCoords,
                    "language", "en",
                    "units", "m"
            );

            String response = webClient.post()
                    .uri("https://api.openrouteservice.org/v2/directions/foot-walking/geojson")
                    .header("Authorization", orsAccessToken)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(response, GeoJsonDto.class);

        } catch (WebClientResponseException e) {
            LOGGER.error("ORS returned error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            LOGGER.error("Error generating ORS route", e);
            throw new RuntimeException(e);
        }
    }



    @Override
    public GeoJsonDto generateRoundTrip(List<GeoJsonPosition> coordinates, int length, int points, int seed) {
        if (coordinates == null || coordinates.isEmpty()) {
            throw new IllegalArgumentException("'coordinates' must contain at least one coordinate for a round trip.");
        }

        try {
            // ORS expects coordinates as [lon, lat]
            List<List<Double>> orsCoords = coordinates.stream()
                    .map(pos -> List.of(pos.getLongitude(), pos.getLatitude()))
                    .toList();

            Map<String, Object> body = Map.of(
                    "coordinates", orsCoords,
                    "language", "en",
                    "units", "m", // IMPORTANT: ORS expects meters, not km
                    "options", Map.of(
                            "round_trip", Map.of(
                                    "length", length,   // meters
                                    "points", points,
                                    "seed", seed
                            )
                    )
            );

            String response = webClient.post()
                    .uri("https://api.openrouteservice.org/v2/directions/foot-walking/geojson")
                    .header("Authorization", orsAccessToken)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(response, GeoJsonDto.class);

        } catch (WebClientResponseException e) {
            LOGGER.error("ORS returned error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            LOGGER.error("Error generating round trip", e);
            throw new RuntimeException("Failed generating ORS round trip", e);
        }
    }

}
