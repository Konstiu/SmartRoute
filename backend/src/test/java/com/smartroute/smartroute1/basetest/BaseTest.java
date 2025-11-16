package com.smartroute.smartroute1.basetest;

import com.smartroute.smartroute1.datagenerator.StravaDataGenerator;
import com.smartroute.smartroute1.datagenerator.UserDataGenerator;
import com.smartroute.smartroute1.repository.StravaAccountRepository;
import com.smartroute.smartroute1.repository.StravaActivityRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BaseTest {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    private UserDataGenerator userDataGenerator;

    @Autowired
    private StravaAccountRepository stravaAccountRepository;

    @Autowired
    private StravaDataGenerator stravaAccountDataGenerator;

    @Autowired
    private StravaActivityRepository stravaActivityRepository;

    @BeforeEach
    void setUp() {
        generateData();
    }

    @AfterEach
    void tearDown() {
        clearData();
    }

    private void generateData() {
        userDataGenerator.generateUser();
        stravaAccountDataGenerator.generateAccounts();
    }

    private void clearData() {
        stravaActivityRepository.deleteAll();
        stravaAccountRepository.deleteAll();
        userRepository.deleteAll();
    }
}
