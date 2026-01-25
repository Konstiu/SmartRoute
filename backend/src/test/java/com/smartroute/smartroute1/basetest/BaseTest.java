package com.smartroute.smartroute1.basetest;

import com.smartroute.smartroute1.datagenerator.InjuryDataGenerator;
import com.smartroute.smartroute1.datagenerator.ActivityDataGenerator;
import com.smartroute.smartroute1.datagenerator.UserDataGenerator;
import com.smartroute.smartroute1.repository.GarminAccountRepository;
import com.smartroute.smartroute1.repository.StravaAccountRepository;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.repository.*;
import com.smartroute.smartroute1.repository.statistics.AtlRepository;
import com.smartroute.smartroute1.repository.statistics.CtlRepository;
import com.smartroute.smartroute1.repository.statistics.TsbRepository;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ApiMockConfig.class, AsyncTestConfig.class})
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
    private ActivityDataGenerator stravaAccountDataGenerator;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ActivityStreamRepository aStreamRepository;

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
    @Autowired
    private ActivityStreamRepository activityStreamRepository;
    @Autowired
    private ConsistencyRepository consistencyRepository;
    @Autowired
    private CtlRepository ctlRepository;
    @Autowired
    private AtlRepository atlRepository;
    @Autowired
    private TsbRepository tsbRepository;

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
        stravaAccountDataGenerator.generateActivities();
        injuryDataGenerator.generateInjuries();
        friendshipRepository.deleteAllInBatch();

    }

    private void clearData() {
        tsbRepository.deleteAllInBatch();
        ctlRepository.deleteAllInBatch();
        atlRepository.deleteAllInBatch();
        gymWorkoutRepository.deleteAllInBatch();
        garminAccountRepository.deleteAllInBatch();
        athleteZoneRepository.deleteAllInBatch();
        activityRepository.deleteAllInBatch();
        activityStreamRepository.deleteAllInBatch();
        stravaAccountRepository.deleteAllInBatch();
        injuryRepository.deleteAllInBatch();
        friendshipRepository.deleteAllInBatch();
        consistencyRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        stravaAccountRepository.deleteAll();
        userRepository.deleteAll();
    }
}
