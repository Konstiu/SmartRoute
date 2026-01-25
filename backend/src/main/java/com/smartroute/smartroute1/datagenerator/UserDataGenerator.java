package com.smartroute.smartroute1.datagenerator;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.entity.enums.Weekday;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.time.LocalDate;
import java.util.Set;

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

            switch (i) {
                case 1 -> {
                    Set<Weekday> weekdays = new HashSet<>();
                    weekdays.add(Weekday.MONDAY);
                    weekdays.add(Weekday.FRIDAY);
                    user.setActiveWeekdays(weekdays);
                    user.setExperienceLevel(ExperienceLevel.BEGINNER);
                    user.setWeight(BigDecimal.valueOf(90L));
                    user.setHeight(184);
                    user.setSex(Sex.MALE);
                    user.setBirthdate(LocalDate.of(2000, 1, 1));
                }
                case 2 -> {
                    Set<Weekday> weekdays = new HashSet<>();
                    weekdays.add(Weekday.MONDAY);
                    weekdays.add(Weekday.WEDNESDAY);
                    weekdays.add(Weekday.FRIDAY);
                    weekdays.add(Weekday.SATURDAY);
                    user.setActiveWeekdays(weekdays);
                    user.setExperienceLevel(ExperienceLevel.INTERMEDIATE);
                    user.setWeight(BigDecimal.valueOf(80L));
                    user.setHeight(173);
                    user.setSex(Sex.OTHER);
                    user.setBirthdate(LocalDate.of(2000, 1, 1));
                }
                case 3 -> {
                    Set<Weekday> weekdays = new HashSet<>();
                    weekdays.add(Weekday.MONDAY);
                    weekdays.add(Weekday.TUESDAY);
                    weekdays.add(Weekday.WEDNESDAY);
                    weekdays.add(Weekday.THURSDAY);
                    weekdays.add(Weekday.FRIDAY);
                    weekdays.add(Weekday.SATURDAY);
                    weekdays.add(Weekday.SUNDAY);
                    user.setActiveWeekdays(weekdays);
                    user.setExperienceLevel(ExperienceLevel.COMPETITIVE_ATHLETE);
                    user.setWeight(BigDecimal.valueOf(70L));
                    user.setHeight(175);
                    user.setSex(Sex.FEMALE);
                    user.setBirthdate(LocalDate.of(2000, 1, 1));
                }
                default -> {
                    Set<Weekday> weekdays = new HashSet<>();
                    weekdays.add(Weekday.MONDAY);
                    weekdays.add(Weekday.TUESDAY);
                    weekdays.add(Weekday.THURSDAY);
                    weekdays.add(Weekday.SATURDAY);
                    user.setActiveWeekdays(weekdays);
                    user.setExperienceLevel(ExperienceLevel.CASUAL);
                    user.setWeight(BigDecimal.valueOf(80L));
                    user.setHeight(180);
                    user.setSex(Sex.MALE);
                }
            }

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