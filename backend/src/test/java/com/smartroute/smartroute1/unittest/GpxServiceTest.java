package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.AthleteZone;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.repository.AthleteZoneRepository;
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;

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
    @Autowired
    private AthleteZoneRepository athleteZoneRepository;

    @Test
    void importValidGpxFile_shouldCreateStravaActivity() throws Exception {
        ApplicationUser testUser = createTestUser("gpxImport@email.com");
        InputStream gpxStream = new ClassPathResource("activity_strava.gpx").getInputStream();
        Activity activity = gpxService.importStravaGpxFile(gpxStream, testUser.getEmail());

        assertAll(
            () -> assertNotNull(activity.getId()),
            () -> assertNull(activity.getStravaId()),
            () -> assertEquals("Abendlauf", activity.getName()),
            () -> assertEquals(8729.5332, activity.getDistance(), 0.1),
            () -> assertEquals(3377, activity.getMovingTime()),
            () -> assertEquals(3442, activity.getElapsedTime()),
            () -> assertEquals(15.5, activity.getTotalElevationGain(), 0.1),
            () -> assertNull(activity.getType()),
            () -> assertNull(activity.getSportType()),
            () -> assertEquals(Instant.parse("2025-10-22T18:41:58Z"), activity.getStartDate()),
            () -> assertNull(activity.getStartDateLocal()),
            () -> assertEquals(2.5363064, activity.getAverageSpeed(), 0.001),
            () -> assertEquals(11.466359, activity.getMaxSpeed(), 0.001),
            () -> assertEquals(155.86769, activity.getAverageHeartrate(), 0.1),
            () -> assertEquals(176, activity.getMaxHeartrate(), 0.1),
            () -> assertNull(activity.getAverageWatts()),
            () -> assertNull(activity.getKilojoules()),
            () -> assertNull(activity.getSufferScore()),
            () -> assertEquals(106, activity.getSessionLoad()),
            () -> assertNotNull(activity.getSummaryPolyline())
        );
    }

    @Test
    void importGpxFile_invalidFile_shouldThrowValidationException() {
        ApplicationUser testUser = createTestUser("gpxImport@email.com");
        InputStream invalidGpxStream = new ByteArrayInputStream("invalid gpx".getBytes());
        assertThrows(ValidationException.class, () -> {
            gpxService.importStravaGpxFile(invalidGpxStream, testUser.getEmail());
        });
    }

    // ==================== HELPER METHODS ====================
    private ApplicationUser createTestUser(String email) {
        ApplicationUser user = new ApplicationUser();
        user.setEmail(email);
        user.setPassword("SOMETHING_THAT_IS_HASHED");
        user.setFirstname("Test");
        user.setLastname("User");
        user.setVerified(true);
        user = userRepository.save(user);

        AthleteZone zone1 = new AthleteZone();
        zone1.setZoneIndex(1);
        zone1.setMax(124);
        zone1.setMin(0);
        zone1.setCustom(false);
        zone1.setUser(user);

        AthleteZone zone2 = new AthleteZone();
        zone2.setZoneIndex(2);
        zone2.setMax(154);
        zone2.setMin(124);
        zone2.setCustom(false);
        zone2.setUser(user);

        AthleteZone zone3 = new AthleteZone();
        zone3.setZoneIndex(3);
        zone3.setMax(169);
        zone3.setMin(154);
        zone3.setCustom(false);
        zone3.setUser(user);

        AthleteZone zone4 = new AthleteZone();
        zone4.setZoneIndex(4);
        zone4.setMax(184);
        zone4.setMin(169);
        zone4.setCustom(false);
        zone4.setUser(user);

        AthleteZone zone5 = new AthleteZone();
        zone5.setZoneIndex(5);
        zone5.setMax(-1);
        zone5.setMin(184);
        zone5.setCustom(false);
        zone5.setUser(user);

        athleteZoneRepository.save(zone1);
        athleteZoneRepository.save(zone2);
        athleteZoneRepository.save(zone3);
        athleteZoneRepository.save(zone4);
        athleteZoneRepository.save(zone5);

        return user;
    }

}
