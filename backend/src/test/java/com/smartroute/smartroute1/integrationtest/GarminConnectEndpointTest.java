package com.smartroute.smartroute1.integrationtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.service.GarminImportService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({ "test", "generateData" })
@AutoConfigureMockMvc
class GarminConnectEndpointTest extends BaseTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private GarminImportService garminImportService;


	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@WithMockUser(username = "email0@smartroute.com", roles = "USER")
	@Disabled
	void syncActivities_withValidRequestBody_shouldReturn200WithData() throws Exception {
		// Arrange: Create mock response
		JsonNode activity1 = objectMapper.readTree("{\"id\":1,\"name\":\"Morning Run\"}");
		JsonNode activity2 = objectMapper.readTree("{\"id\":2,\"name\":\"Evening Bike\"}");
		List<JsonNode> serviceResponse = List.of(activity1, activity2);

		// Mock the service to return data
		when(garminImportService.syncActivities(any(), anyInt(), anyString(), anyString()))
				.thenReturn(serviceResponse);

		String requestBody = """
            {
                "gEmail": "test@garmin.com",
                "gPassword": "myPassword",
                "count": 5
            }
            """;

		// Act + Assert
		mockMvc.perform(post("/api/v1/garmin/sync")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andDo(print()) // keep this on for debugging
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(1))
				.andExpect(jsonPath("$[1].id").value(2));

		// Verify service was called
		verify(garminImportService, times(1))
				.syncActivities(any(), eq(5), eq("test@garmin.com"), eq("myPassword"));
	}

	@Test
	@WithMockUser(roles = "USER")
	void syncActivities_withEmptyResult_shouldReturn200WithEmptyArray() throws Exception {
		when(garminImportService.syncActivities(any(), anyInt(), anyString(), anyString()))
				.thenReturn(List.of());

		String requestBody = """
            {
                "gEmail": "test@garmin.com",
                "gPassword": "myPassword",
                "count": 5
            }
            """;

		mockMvc.perform(post("/api/v1/garmin/sync")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$.length()").value(0));
	}


	@Test
	@WithMockUser(roles = "USER")
	void syncActivities_withNullPassword_shouldAcceptRequest() throws Exception {
		when(garminImportService.syncActivities(any(), anyInt(), anyString(), any()))
				.thenReturn(List.of());

		String requestBody = """
            {
                "gEmail": "test@garmin.com",
                "count": 5
            }
            """;

		mockMvc.perform(post("/api/v1/garmin/sync")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andExpect(status().isOk());
	}


	@Test
	void syncActivities_withoutAuthentication_shouldReturn403() throws Exception {
		String requestBody = """
            {
                "gEmail": "test@garmin.com",
                "gPassword": "password",
                "count": 5
            }
            """;

		// No @WithMockUser - should be blocked by security
		mockMvc.perform(post("/api/v1/garmin/sync")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andExpect(status().isForbidden());

		// Service should never be called
		verify(garminImportService, never())
				.syncActivities(any(), anyInt(), anyString(), anyString());
	}

	@Test
	@WithMockUser(roles = "WRONG_ROLE")
	void syncActivities_withWrongRole_shouldReturn403() throws Exception {
		String requestBody = """
            {
                "gEmail": "test@garmin.com",
                "gPassword": "password",
                "count": 5
            }
            """;

		mockMvc.perform(post("/api/v1/garmin/sync")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andExpect(status().isForbidden());
	}
}