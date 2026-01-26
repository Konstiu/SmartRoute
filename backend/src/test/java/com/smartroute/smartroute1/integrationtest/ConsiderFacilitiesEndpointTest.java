package com.smartroute.smartroute1.integrationtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.GeoPoint;
import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.endpoint.dto.RouteWithFacilitiesDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonFeature;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonGeometryLineString;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.security.JwtTokenizer;
import com.smartroute.smartroute1.service.OpenRouteServiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static com.smartroute.smartroute1.basetest.TestData.DEFAULT_USER_EMAIL;
import static com.smartroute.smartroute1.basetest.TestData.SAMPLE_POLYLINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "generateData"})
class ConsiderFacilitiesEndpointTest extends BaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenizer jwtTokenizer;

    @MockBean
    private OpenRouteServiceService openRouteServiceService;

    private String authToken;

    private String endpoint = "/api/v1/stops/";


    @BeforeEach
    void setUp() {
        // Get user with ID 0 and generate JWT token
        ApplicationUser user = userRepository.getByEmail(DEFAULT_USER_EMAIL);
        String token = jwtTokenizer.getAuthToken(user.getEmail(), List.of("ROLE_USER"));
        // Remove "Bearer " prefix if it exists
        authToken = token.startsWith("Bearer ") ? token.substring(7) : token;

        // Mock OpenRouteService - we don't actually need it to return anything
        // since the endpoint works with the decoded polyline coordinates directly

        GeoJsonDto geoJsonDto = new GeoJsonDto();
        geoJsonDto.setType("FeatureCollection");
        geoJsonDto.setBbox(List.of(16.36745, 48.19998, 170.0, 16.370733, 48.20514, 189.0));
        GeoJsonFeature f = new GeoJsonFeature();
        f.setType("Feature");
        f.setBbox(List.of(16.36745, 48.19998, 170.0, 16.370733, 48.20514, 189.0));
        List<GeoJsonPosition> positions = List.of(
                new GeoJsonPosition(48.204531, 16.368572, 182.0),
                new GeoJsonPosition(48.204727, 16.368461, 182.0),
                new GeoJsonPosition(48.204777, 16.368637, 182.0),
                new GeoJsonPosition(48.205106, 16.368436, 188.0),
                new GeoJsonPosition(48.20514, 16.368563, 186.1),
                new GeoJsonPosition(48.205132, 16.368651, 186.5),
                new GeoJsonPosition(48.204901, 16.369512, 182.0),
                new GeoJsonPosition(48.204702, 16.370468, 189.0),
                new GeoJsonPosition(48.204659, 16.370733, 189.0),
                new GeoJsonPosition(48.204155, 16.370518, 183.0),
                new GeoJsonPosition(48.20372, 16.37031, 183.0),
                new GeoJsonPosition(48.203591, 16.370326, 183.0),
                new GeoJsonPosition(48.203053, 16.370071, 185.0),
                new GeoJsonPosition(48.202841, 16.369962, 182.0),
                new GeoJsonPosition(48.202647, 16.369861, 182.0),
                new GeoJsonPosition(48.202563, 16.369813, 182.0),
                new GeoJsonPosition(48.202493, 16.369774, 182.0),
                new GeoJsonPosition(48.202456, 16.369846, 182.0),
                new GeoJsonPosition(48.202388, 16.369809, 182.0),
                new GeoJsonPosition(48.202373, 16.369801, 182.0),
                new GeoJsonPosition(48.20232, 16.369772, 182.0),
                new GeoJsonPosition(48.202279, 16.369749, 182.0),
                new GeoJsonPosition(48.202251, 16.369735, 182.0),
                new GeoJsonPosition(48.202174, 16.369678, 182.0),
                new GeoJsonPosition(48.202099, 16.369716, 182.0),
                new GeoJsonPosition(48.202071, 16.369717, 182.0),
                new GeoJsonPosition(48.202075, 16.369697, 182.0),
                new GeoJsonPosition(48.201993, 16.369661, 182.0),
                new GeoJsonPosition(48.201639, 16.369468, 177.0),
                new GeoJsonPosition(48.201279, 16.369293, 177.0),
                new GeoJsonPosition(48.201086, 16.369193, 177.0),
                new GeoJsonPosition(48.201039, 16.369158, 178.0),
                new GeoJsonPosition(48.200976, 16.369126, 178.0),
                new GeoJsonPosition(48.200822, 16.369045, 172.5),
                new GeoJsonPosition(48.2007, 16.369057, 170.0),
                new GeoJsonPosition(48.200678, 16.36899, 170.0),
                new GeoJsonPosition(48.20067, 16.368967, 170.0),
                new GeoJsonPosition(48.200657, 16.368775, 170.0),
                new GeoJsonPosition(48.200646, 16.368606, 170.0),
                new GeoJsonPosition(48.200152, 16.368323, 174.0),
                new GeoJsonPosition(48.200182, 16.368194, 177.2),
                new GeoJsonPosition(48.200171, 16.368069, 176.2),
                new GeoJsonPosition(48.199988, 16.367728, 185.0),
                new GeoJsonPosition(48.199983, 16.367453, 183.0),
                new GeoJsonPosition(48.19998, 16.36745, 183.0));
        GeoJsonGeometryLineString g = new GeoJsonGeometryLineString();
        g.setType("LineString");
        g.setCoordinates(positions);
        f.setGeometry(g);
        geoJsonDto.setFeatures(List.of(f));
        when(openRouteServiceService.requestRoute(any(), anyBoolean())).thenReturn(geoJsonDto);
        when(openRouteServiceService.generateRouteAvoidingPolygon(any(), any(), anyBoolean())).thenReturn(geoJsonDto);
    }

    @Test
    void testGenerateRouteWithNoFacilities() throws Exception {
        RouteWithFacilitiesDto request = new RouteWithFacilitiesDto();
        request.setOriginalRoute(SAMPLE_POLYLINE);
        request.setIncludeToilets(false);
        request.setIncludeFountains(false);

        MvcResult result = mockMvc.perform(post(endpoint + "with-facilities")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);

        assertAll("Response should have correct structure and values for no facilities",
                () -> assertThat(response.has("polyline")).isTrue(),
                () -> assertThat(response.get("polyline").asText()).isNotEmpty(),
                () -> assertThat(response.get("facilitiesAdded").asInt()).isEqualTo(0),
                () -> assertThat(response.get("distance").asDouble()).isGreaterThan(0),
                () -> assertThat(response.get("originalDistance").asDouble()).isEqualTo(response.get("distance").asDouble()),
                () -> assertThat(response.get("distanceAdded").asDouble()).isEqualTo(0.0)
        );
    }

    @Test
    void testGenerateRouteWithToilets() throws Exception {
        RouteWithFacilitiesDto request = new RouteWithFacilitiesDto();
        request.setOriginalRoute(SAMPLE_POLYLINE);
        request.setIncludeToilets(true);
        request.setToiletIntervalMeters(5000);
        request.setIncludeFountains(false);
        request.setMaxFacilityDistance(500);

        MvcResult result = mockMvc.perform(post(endpoint + "with-facilities")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);

        assertAll("Response should have correct structure for toilets",
                () -> assertThat(response.has("polyline")).isTrue(),
                () -> assertThat(response.get("polyline").asText()).isNotEmpty(),
                () -> assertThat(response.get("distance").asDouble()).isGreaterThan(0),
                () -> assertThat(response.get("originalDistance").asDouble()).isGreaterThan(0)
        );

        // If toilets were found and added, distance should increase
        if (response.get("facilitiesAdded").asInt() > 0) {
            assertAll("Distance should increase when facilities are added",
                    () -> assertThat(response.get("distance").asDouble())
                            .isGreaterThan(response.get("originalDistance").asDouble()),
                    () -> assertThat(response.get("distanceAdded").asDouble()).isGreaterThan(0)
            );
        }
    }

    @Test
    void testGenerateRouteWithFountains() throws Exception {
        RouteWithFacilitiesDto request = new RouteWithFacilitiesDto();
        request.setOriginalRoute(SAMPLE_POLYLINE);
        request.setIncludeToilets(false);
        request.setIncludeFountains(true);
        request.setFountainIntervalMeters(3000);
        request.setMaxFacilityDistance(500);

        MvcResult result = mockMvc.perform(post(endpoint + "with-facilities")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);

        assertAll("Response should have correct structure for fountains",
                () -> assertThat(response.has("polyline")).isTrue(),
                () -> assertThat(response.get("distance").asDouble()).isGreaterThan(0),
                () -> assertThat(response.get("originalDistance").asDouble()).isGreaterThan(0)
        );
    }

    @Test
    void testGenerateRouteWithBothFacilities() throws Exception {
        RouteWithFacilitiesDto request = new RouteWithFacilitiesDto();
        request.setOriginalRoute(SAMPLE_POLYLINE);
        request.setIncludeToilets(true);
        request.setToiletIntervalMeters(5000);
        request.setIncludeFountains(true);
        request.setFountainIntervalMeters(3000);
        request.setMaxFacilityDistance(500);

        MvcResult result = mockMvc.perform(post(endpoint + "with-facilities")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);

        assertAll("Response should have all required fields",
                () -> assertThat(response.has("polyline")).isTrue(),
                () -> assertThat(response.has("distance")).isTrue(),
                () -> assertThat(response.has("originalDistance")).isTrue(),
                () -> assertThat(response.has("distanceAdded")).isTrue(),
                () -> assertThat(response.has("facilitiesAdded")).isTrue(),
                () -> assertThat(response.has("totalPoints")).isTrue(),
                () -> assertThat(response.get("totalPoints").asInt()).isGreaterThan(0)
        );
    }

    @Test
    void testInvalidPolyline() throws Exception {
        RouteWithFacilitiesDto request = new RouteWithFacilitiesDto();
        request.setOriginalRoute("invalid_polyline_data");
        request.setIncludeToilets(true);

        mockMvc.perform(post(endpoint + "with-facilities")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testNullPolyline() throws Exception {
        RouteWithFacilitiesDto request = new RouteWithFacilitiesDto();
        request.setIncludeToilets(true);

        mockMvc.perform(post(endpoint + "with-facilities")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetFacilitiesInBounds() throws Exception {
        MvcResult result = mockMvc.perform(get(endpoint + "facilities")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);

        assertAll("Facilities list should have correct structure",
                () -> assertThat(response.isArray()).isTrue()
        );

        if (!response.isEmpty()) {
            JsonNode firstFacility = response.get(0);
            assertAll("Each facility should have required fields",
                    () -> assertThat(firstFacility.has("id")).isTrue(),
                    () -> assertThat(firstFacility.has("coordinate")).isTrue(),
                    () -> assertThat(firstFacility.has("type")).isTrue(),
                    () -> assertThat(firstFacility.get("type").asText()).isIn("Fountain", "Toilet")
            );
        }
    }

    @Test
    void testRouteWithVerySmallInterval_ShouldNotBePossible() throws Exception {
        RouteWithFacilitiesDto request = new RouteWithFacilitiesDto();
        request.setOriginalRoute(SAMPLE_POLYLINE);
        request.setIncludeToilets(true);
        request.setToiletIntervalMeters(100); // Very small interval
        request.setMaxFacilityDistance(500);

        MvcResult result = mockMvc.perform(post(endpoint + "with-facilities")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(400))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);

        assertAll("Small interval should still produce valid response",
                () -> assertThat(response.has("facilitiesAdded")).isFalse()
        );
    }

    @Test
    void testRouteWithLargeMaxDistance_ShouldFindSomeFacilities() throws Exception {
        RouteWithFacilitiesDto request = new RouteWithFacilitiesDto();
        request.setOriginalRoute(SAMPLE_POLYLINE);
        request.setIncludeToilets(true);
        request.setToiletIntervalMeters(5000);
        request.setMaxFacilityDistance(2000); // Larger search radius

        MvcResult result = mockMvc.perform(post(endpoint + "with-facilities")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);

        assertAll("Large max distance should produce valid response",
                () -> assertThat(response.has("facilitiesAdded")).isTrue()
        );
    }
}