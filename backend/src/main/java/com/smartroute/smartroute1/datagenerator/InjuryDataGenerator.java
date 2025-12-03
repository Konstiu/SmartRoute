package com.smartroute.smartroute1.datagenerator;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Injuries;
import com.smartroute.smartroute1.entity.enums.BodyPart;
import com.smartroute.smartroute1.repository.InjuryRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Profile("generateData")
@Component
@DependsOn("userDataGenerator")
public class InjuryDataGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final InjuryRepository injuryRepository;
    private final UserRepository userRepository;

    private static final int MAX_DAYS_BACK = 365;
    private static final int RECENT_DAYS = 14;
    private static final int MAX_HEALTHY_DAYS = 28;
    private static final int BATCH_SIZE = 500;

    public InjuryDataGenerator(InjuryRepository injuryRepository,
                               UserRepository userRepository) {
        this.injuryRepository = injuryRepository;
        this.userRepository = userRepository;
    }

    @PostConstruct
    @Transactional
    public void generateInjuries() {
        if (injuryRepository.count() > 0) {
            LOGGER.info("Injuries already generated, skipping datagen.");
            return;
        }
        List<ApplicationUser> users = userRepository.findAll();
        if (users.isEmpty()) {
            LOGGER.warn("No users found, cannot generate injuries.");
            return;
        }
        LOGGER.info("Generating injuries for {} users…", users.size());
        LocalDate today = LocalDate.now();
        BodyPart[] bodyParts = BodyPart.values();
        int bodyPartsLength = bodyParts.length;
        List<Injuries> batch = new ArrayList<>(BATCH_SIZE);
        int totalInjuries = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (ApplicationUser user : users) {
            int injuryCount = random.nextInt(40);
            if (injuryCount == 0) {
                continue;
            }
            for (int i = 0; i < injuryCount; i++) {
                Injuries injury = new Injuries();
                injury.setApplicationUser(user);
                injury.setAffectedArea(bodyParts[random.nextInt(bodyPartsLength)]);
                boolean recent = random.nextDouble() < 0.3;
                int daysAgo = recent
                        ? random.nextInt(RECENT_DAYS + 1)
                        : random.nextInt(RECENT_DAYS + 1, MAX_DAYS_BACK + 1);

                LocalDate lastHealthyDate = today.minusDays(daysAgo);
                injury.setLastHealthyDate(lastHealthyDate);

                BodyPart area = injury.getAffectedArea();
                double injuryIndex = (area == BodyPart.BONE_FRACTURE
                        || area == BodyPart.SPINAL_INJURY
                        || area == BodyPart.RESPIRATION_REGION)
                        ? 0.6 + random.nextDouble() * 0.4
                        : 0.1 + random.nextDouble() * 0.7;

                injury.setInjuryIndex(injuryIndex);
                LocalDate lastInjuryDate = null;
                if (!recent || random.nextDouble() < 0.3) {
                    int daysToHealthy = random.nextInt(1, MAX_HEALTHY_DAYS + 1);
                    lastInjuryDate = lastHealthyDate.plusDays(daysToHealthy);
                    if (lastInjuryDate.isAfter(today)) {
                        lastInjuryDate = today;
                    }
                }
                injury.setLastInjuryDate(lastInjuryDate);
                batch.add(injury);
                if (batch.size() >= BATCH_SIZE) {
                    injuryRepository.saveAll(batch);
                    totalInjuries += batch.size();
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            injuryRepository.saveAll(batch);
            totalInjuries += batch.size();
        }
        LOGGER.info("Generated {} injuries for {} users.", totalInjuries, users.size());
    }
}