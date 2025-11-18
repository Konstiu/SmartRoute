package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.StravaActivity;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.GpxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles({"test", "generateData"})
@Transactional
public class GpxServiceTest {

    @Autowired
    private GpxService gpxService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void importValidGpxFile_shouldCreateStravaActivity() throws Exception {
        ApplicationUser testUser = createTestUser("gpxImport@email.com");
        InputStream gpxStream = new ClassPathResource("activity_strava.gpx").getInputStream();
        StravaActivity activity = gpxService.importStravaGpxFile(gpxStream, testUser.getEmail());

        assertAll(
            () -> assertNotNull(activity.getId()),
            () -> assertNull(activity.getStravaId()),
            () -> assertEquals("Abendlauf", activity.getName()),
            () -> assertEquals(8729.9668, activity.getDistance(), 0.1),
            () -> assertEquals(3357, activity.getMovingTime()),
            () -> assertEquals(3442, activity.getElapsedTime()),
            () -> assertEquals(15.5, activity.getTotalElevationGain(), 0.1),
            () -> assertNull(activity.getType()),
            () -> assertNull(activity.getSportType()),
            () -> assertEquals("2025-10-22T18:41:58Z", activity.getStartDate()),
            () -> assertNull(activity.getStartDateLocal()),
            () -> assertEquals(2.5363064, activity.getAverageSpeed(), 0.001),
            () -> assertEquals(11.466359, activity.getMaxSpeed(), 0.001),
            () -> assertEquals(155.86769, activity.getAverageHeartrate(), 0.1),
            () -> assertEquals(176, activity.getMaxHeartrate(), 0.1),
            () -> assertNull(activity.getAverageWatts()),
            () -> assertNull(activity.getKilojoules()),
            () -> assertNull(activity.getSufferScore()),
            () -> assertNotNull(activity.getSummaryPolyline())
        );

    }

    // ==================== HELPER METHODS ====================
    private ApplicationUser createTestUser(String email) {
        ApplicationUser user = new ApplicationUser();
        user.setEmail(email);
        user.setPassword("SOMETHING_THAT_IS_HASHED");
        user.setFirstname("Test");
        user.setLastname("User");
        user.setVerified(true);
        return userRepository.save(user);
    }

}
