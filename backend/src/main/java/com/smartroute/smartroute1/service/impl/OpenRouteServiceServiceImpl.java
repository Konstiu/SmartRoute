package com.smartroute.smartroute1.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.endpoint.dto.geojson.*;
import com.smartroute.smartroute1.endpoint.mapper.PolyLineMapper;
import com.smartroute.smartroute1.endpoint.mapper.PolyLineMapperImpl;
import com.smartroute.smartroute1.service.OpenRouteServiceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.lang.invoke.MethodHandles;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OpenRouteServiceServiceImpl implements OpenRouteServiceService {

    private final WebClient webClient;
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final String orsAccessToken = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6ImQ2MDAyNmVjYmY4NTRhOWZiNThlMTI3YjY5NzAxODVlIiwiaCI6Im11cm11cjY0In0=";
    private final PolyLineMapper polyLineMapper;

    // First Name: No
    // Last Name: Answer
    // email: wrg86009@laoia.com
    // email: col600aom@ia.wrg89

    @Override
    public GeoJsonDto requestRoute(List<GeoJsonPosition> coordinates) {
        if (coordinates == null || coordinates.size() < 2) {
            throw new IllegalArgumentException("'coordinates' must contain at least two coordinates.");
        }
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String coords = objectMapper.writeValueAsString(coordinates);
            String body = "{\"coordinates\":" + coords + ",\"elevation\":true,\"instructions\":false}";

            System.out.println(body);

            String response = webClient.post()
                    .uri("https://api.openrouteservice.org/v2/directions/foot-walking/geojson")
                    .header("Authorization", orsAccessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .map(b -> new RuntimeException("4xx: " + b)))
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .map(b -> new RuntimeException("5xx: " + b)))
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

    public List<GeoJsonPosition> requestElevation(List<GeoJsonPosition> coordinates) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            GeoJsonGeometryLineString ls = new GeoJsonGeometryLineString();
            ls.setCoordinates(coordinates);
            String body = "{\"format_in\":\"encodedpolyline6\",\"format_out\":\"geojson\",\"geometry\":\""
                    + polyLineMapper.geoJsonGeometryLineStringToPolyline(ls).replace("\\", "\\\\") + "\"}";

            String response = webClient.post()
                    .uri("https://api.openrouteservice.org/elevation/line")
                    .header("Authorization", orsAccessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .map(b -> new RuntimeException("4xx: " + b)))
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .map(b -> new RuntimeException("5xx: " + b)))
                    .bodyToMono(String.class)
                    .block();

            JsonNode node = objectMapper.readTree(response);
            JsonNode coordsNode = node.get("geometry").get("coordinates");
            List<GeoJsonPosition> coords = objectMapper.readValue(coordsNode.toString(), new TypeReference<>() {
            });

            return coords;
        } catch (JsonProcessingException e) {
            LOGGER.error("Error processing json: ", e);
        } catch (WebClientResponseException e) {
            LOGGER.error("Failed generating route. Response from server: ", e);
        }
        return null;
    }

    private GeoJsonDto makeRoundTripRequest(List<GeoJsonPosition> coordinates, int length, int points, int seed, int num) {
        GeoJsonDto dto = new GeoJsonDto();
        dto.setType("FeatureCollection");

        GeoJsonFeature f = new GeoJsonFeature();
        f.setType("Feature");
        dto.setFeatures(List.of(f));

        GeoJsonGeometryLineString ls = new GeoJsonGeometryLineString();
        ls.setType("LineString");
        f.setGeometry(ls);

        GeoJsonProperties p = new GeoJsonProperties();
        f.setProperties(p);

        if (coordinates == null || coordinates.isEmpty()) {
            throw new IllegalArgumentException("'coordinates' must contain at least one coordinate for a round trip.");
        }
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String coords = objectMapper.writeValueAsString(coordinates);
            String body = "{\"coordinates\":" + coords + ",\"elevation\":true,\"units\":\"km\",\"options\":{\"round_trip\":{\"length\":" + length + ",\"points\":" + points + ",\"seed\":" + seed + "}}}";

            System.out.println(body);

            String response = webClient.post()
                    .uri("https://api.openrouteservice.org/v2/directions/foot-walking/geojson")
                    .header("Accept", "application/json, application/geo+json, application/gpx+xml, img/png; charset=utf-8")
                    .header("Authorization", orsAccessToken)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .map(b -> new RuntimeException("4xx: " + b)))
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .map(b -> new RuntimeException("5xx: " + b)))
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
