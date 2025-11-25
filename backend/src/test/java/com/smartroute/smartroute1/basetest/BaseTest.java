package com.smartroute.smartroute1.basetest;

import com.smartroute.smartroute1.datagenerator.InjuryDataGenerator;
import com.smartroute.smartroute1.datagenerator.StravaDataGenerator;
import com.smartroute.smartroute1.datagenerator.UserDataGenerator;
import com.smartroute.smartroute1.repository.InjuryRepository;
import com.smartroute.smartroute1.repository.StravaAccountRepository;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.UserRepository;
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
    private InjuryRepository injuryRepository;

    @Autowired
    private InjuryDataGenerator injuryDataGenerator;

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

    }

    private void clearData() {
        stravaActivityRepository.deleteAllInBatch();
        stravaAccountRepository.deleteAllInBatch();
        injuryRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }
}
