package com.smartroute.smartroute1.datagenerator;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;

@Profile("generateData")
@Component
public class UserDataGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final int NUMBER_OF_USERS_TO_GENERATE = 10;
    private static final int BATCH_SIZE = 100;

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    public UserDataGenerator(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @PostConstruct
    @Transactional
    public void generateUser() {
        if (userRepository.count() > 0) {
            LOGGER.info("Users already generated");
            return;
        }
        LOGGER.info("Generating {} user entries", NUMBER_OF_USERS_TO_GENERATE);
        List<ApplicationUser> batch = new ArrayList<>(BATCH_SIZE);
        String encodedPassword = passwordEncoder.encode("password");
        for (int i = 0; i < NUMBER_OF_USERS_TO_GENERATE; i++) {
            ApplicationUser user = new ApplicationUser();
            user.setEmail("email" + i + "@smartroute.com");
            user.setFirstname("Max" + i);
            user.setLastname("Mustermann" + i);
            user.setPassword(encodedPassword);
            user.setBirthdate(LocalDate.of(1980, 1, 1));
            batch.add(user);
            if (batch.size() >= BATCH_SIZE) {
                userRepository.saveAll(batch);
                LOGGER.debug("Saved batch of {} users", batch.size());
                batch.clear();
            }
            if (!batch.isEmpty()) {
                userRepository.saveAll(batch);
                LOGGER.debug("Saved final batch of {} users", batch.size());
            }
        }
        LOGGER.info("Successfully generated {} users", NUMBER_OF_USERS_TO_GENERATE);
    }
}