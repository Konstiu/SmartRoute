package com.smartroute.smartroute1.integrationtest;

import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.service.ActivityProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "generateData"})
@AutoConfigureMockMvc
public class GpxEndpointTest extends BaseTest {

    private static final String GPX_IMPORT_URI = "/api/v1/gpx/import-strava";
    @Autowired
    private MockMvc mockMvc;
    @SpyBean
    private ActivityProcessingService activityService;


    @BeforeEach
    void setup() {
        doNothing().when(activityService).fetchWeatherForActivity(Mockito.any()); //Avoid API calls in testing
    }

    @Test
    @WithMockUser(username = "email0@smartroute.com")
    void importStravaGpx_shouldReturnDetailedActivityDtoList_whenValidGpxFileUploaded() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/activity_strava.gpx")) {
            MockMultipartFile file = new MockMultipartFile("files", "activity_strava.gpx", "application/gpx+xml", is);

            mockMvc.perform(multipart(GPX_IMPORT_URI)
                            .file(file)
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].name").value("Abendlauf"));
        }
    }

    @Test
    @WithMockUser(username = "email0@smartroute.com")
    void importStravaGpx_shouldThrowValidationException_whenTotalSizeExceedsLimit() throws Exception {
        byte[] large = new byte[11 * 1024 * 1024];
        MockMultipartFile largeFile = new MockMultipartFile("files", "large.gpx", "application/gpx+xml", large);

        mockMvc.perform(multipart(GPX_IMPORT_URI)
                        .file(largeFile)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = "email0@smartroute.com")
    void importStravaGpx_shouldReturnUnprocessableEntity_whenInvalidGpxFileUploaded() throws Exception {
        String invalidGpx = """
                <gpx version="1.1" creator="Test" xmlns="http://www.topografix.com/GPX/1/1">
                  <trk>
                    <trkseg>
                      <trkpt lat="abc" lon="11.5"></trkpt>
                    </trkseg>
                  </trk>
                </gpx>
                """;
        MockMultipartFile invalidFile = new MockMultipartFile("files", "invalid.gpx", "application/gpx+xml", invalidGpx.getBytes());

        mockMvc.perform(multipart(GPX_IMPORT_URI)
                        .file(invalidFile)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void importStravaGpx_shouldReturnForbidden_whenNotAuthenticated() throws Exception {
        byte[] dummy = new byte[10];
        MockMultipartFile file = new MockMultipartFile("files", "dummy.gpx", "application/gpx+xml", dummy);
        mockMvc.perform(multipart(GPX_IMPORT_URI)
                        .file(file)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isForbidden());
    }

}
