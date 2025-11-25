package com.smartroute.smartroute1.datagenerator;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.lang.invoke.MethodHandles;
import java.time.LocalDate;

@Profile("generateData")
@Order(1)
@Component
public class UserDataGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final int NUMBER_OF_USERS_TO_GENERATE = 5;
    private final PasswordEncoder passwordEncoder;

    private final UserRepository userRepository;

    public UserDataGenerator(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @PostConstruct
    public void generateUser() {
        if (!userRepository.findAll().isEmpty()) {
            LOGGER.info("Users already generated");
        } else {
            LOGGER.info("generating {} user entries", NUMBER_OF_USERS_TO_GENERATE);
            for (int i = 0; i < NUMBER_OF_USERS_TO_GENERATE; i++) {
                ApplicationUser user = new ApplicationUser();
                user.setEmail("email" + i + "@smartroute.com");
                user.setFirstname("Max" + i);
                user.setLastname("Mustermann" + i);
                user.setPassword(passwordEncoder.encode("password" + i));
                user.setBirthdate(LocalDate.of(1980, 1, 1));
                userRepository.save(user);
                LOGGER.info("saving user {}", user.getEmail());
            }
        }
    }
}
