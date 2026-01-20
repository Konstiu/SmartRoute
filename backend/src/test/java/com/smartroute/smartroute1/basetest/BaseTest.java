package com.smartroute.smartroute1.basetest;

import com.smartroute.smartroute1.datagenerator.InjuryDataGenerator;
import com.smartroute.smartroute1.datagenerator.StravaDataGenerator;
import com.smartroute.smartroute1.datagenerator.UserDataGenerator;
import com.smartroute.smartroute1.repository.GarminAccountRepository;
import com.smartroute.smartroute1.repository.StravaAccountRepository;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.entity.AthleteZone;
import com.smartroute.smartroute1.repository.*;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ApiMockConfig.class)
public class BaseTest {

    @Autowired
    protected MockWebServer mockApiServer;

    @Autowired
    protected ApiMockConfig.MockWebServerProvider mockApiServerProvider;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    private UserDataGenerator userDataGenerator;

    @Autowired
    private StravaAccountRepository stravaAccountRepository;

    @Autowired
    private StravaDataGenerator stravaAccountDataGenerator;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private GarminAccountRepository garminAccountRepository;

    @Autowired
    private InjuryRepository injuryRepository;

    @Autowired
    private InjuryDataGenerator injuryDataGenerator;

    @Autowired
    private AthleteZoneRepository athleteZoneRepository;

    @Autowired
    private GymWorkoutRepository gymWorkoutRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @BeforeEach
    void setUp() {
        try {
            this.mockApiServer = mockApiServerProvider.resetAndGet();
        } catch (Exception e) {
            throw new RuntimeException("Failed to reset mockApiServer", e);
        }

        generateData();
    }

    @AfterEach
    void tearDown() {
        clearData();
    }

    private void generateData() {
        userDataGenerator.generateUser();
        stravaAccountDataGenerator.generateAccounts();
        injuryDataGenerator.generateInjuries();
        friendshipRepository.deleteAllInBatch();

    }

    private void clearData() {
        gymWorkoutRepository.deleteAllInBatch();
        garminAccountRepository.deleteAllInBatch();
        athleteZoneRepository.deleteAllInBatch();
        activityRepository.deleteAllInBatch();
        stravaAccountRepository.deleteAllInBatch();
        injuryRepository.deleteAllInBatch();
        friendshipRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        stravaAccountRepository.deleteAll();
        userRepository.deleteAll();
    }
}
