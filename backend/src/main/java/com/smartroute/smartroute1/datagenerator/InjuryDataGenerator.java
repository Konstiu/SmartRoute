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

        for (int j = 0; j < users.size(); j++) {
            switch (j) {
                case 1 -> {
                    // User 1 (Beginner): 3 past injuries, all recovered
                    generateBeginnerInjuries(users.get(j), today, batch);
                }
                case 2 -> {
                    // User 2 (Advanced): 4 injuries, 1 currently active
                    generateAdvancedInjuries(users.get(j), today, batch);
                }
                case 3 -> {
                    // User 3 (Pro): 5 past injuries, all recovered
                    generateProInjuries(users.get(j), today, batch);
                }
                default -> {
                    int injuryCount = random.nextInt(20);
                    if (injuryCount == 0) {
                        continue;
                    }
                    for (int i = 0; i < injuryCount; i++) {
                        Injuries injury = new Injuries();
                        injury.setApplicationUser(users.get(j));
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
            }
        }
        if (!batch.isEmpty()) {
            injuryRepository.saveAll(batch);
            totalInjuries += batch.size();
        }
        LOGGER.info("Generated {} injuries for {} users.", totalInjuries, users.size());
    }


    private void generateBeginnerInjuries(ApplicationUser user, LocalDate today, List<Injuries> batch) {
        Injuries injury1 = new Injuries();
        injury1.setApplicationUser(user);
        injury1.setAffectedArea(BodyPart.FEET_REGION);
        injury1.setLastHealthyDate(today.minusDays(120));
        injury1.setLastInjuryDate(today.minusDays(106)); // recovered
        injury1.setInjuryIndex(0.35);
        injuryRepository.save(injury1);
        //batch.add(injury1);

        Injuries injury2 = new Injuries();
        injury2.setApplicationUser(user);
        injury2.setAffectedArea(BodyPart.KNEE_REGION);
        injury2.setLastHealthyDate(today.minusDays(65));
        injury2.setLastInjuryDate(today.minusDays(55)); // recovered
        injury2.setInjuryIndex(0.42);
        injuryRepository.save(injury2);
        //batch.add(injury2);

        Injuries injury3 = new Injuries();
        injury3.setApplicationUser(user);
        injury3.setAffectedArea(BodyPart.LOWER_LEG_REGION);
        injury3.setLastHealthyDate(today.minusDays(30));
        injury3.setLastInjuryDate(today.minusDays(23)); // recovered
        injury3.setInjuryIndex(0.28);
        injuryRepository.save(injury3);
        //batch.add(injury3);
    }

    private void generateAdvancedInjuries(ApplicationUser user, LocalDate today, List<Injuries> batch) {
        Injuries injury1 = new Injuries();
        injury1.setApplicationUser(user);
        injury1.setAffectedArea(BodyPart.UPPER_LEG_REGION);
        injury1.setLastHealthyDate(today.minusDays(180));
        injury1.setLastInjuryDate(today.minusDays(159)); // recovered
        injury1.setInjuryIndex(0.55);
        injuryRepository.save(injury1);
        //batch.add(injury1);

        Injuries injury2 = new Injuries();
        injury2.setApplicationUser(user);
        injury2.setAffectedArea(BodyPart.CORE_REGION);
        injury2.setLastHealthyDate(today.minusDays(95));
        injury2.setLastInjuryDate(today.minusDays(67)); // recovered
        injury2.setInjuryIndex(0.48);
        injuryRepository.save(injury2);
        //batch.add(injury2);

        Injuries injury3 = new Injuries();
        injury3.setApplicationUser(user);
        injury3.setAffectedArea(BodyPart.FEET_REGION);
        injury3.setLastHealthyDate(today.minusDays(45));
        injury3.setLastInjuryDate(today.minusDays(27)); // recovered
        injury3.setInjuryIndex(0.52);
        injuryRepository.save(injury3);
        //batch.add(injury3);

        Injuries injury4 = new Injuries();
        injury4.setApplicationUser(user);
        injury4.setAffectedArea(BodyPart.FEET_REGION);
        injury4.setLastHealthyDate(today.minusDays(8));
        injury4.setLastInjuryDate(null); // active injury
        injury4.setInjuryIndex(0.58);
        injuryRepository.save(injury4);
        //batch.add(injury4);
    }

    private void generateProInjuries(ApplicationUser user, LocalDate today, List<Injuries> batch) {
        Injuries injury1 = new Injuries();
        injury1.setApplicationUser(user);
        injury1.setAffectedArea(BodyPart.BONE_FRACTURE);
        injury1.setLastHealthyDate(today.minusDays(240));
        injury1.setLastInjuryDate(today.minusDays(198)); // recovered
        injury1.setInjuryIndex(0.82);
        injuryRepository.save(injury1);
        //batch.add(injury1);

        Injuries injury2 = new Injuries();
        injury2.setApplicationUser(user);
        injury2.setAffectedArea(BodyPart.CORE_REGION);
        injury2.setLastHealthyDate(today.minusDays(150));
        injury2.setLastInjuryDate(today.minusDays(125)); // recovered
        injury2.setInjuryIndex(0.62);
        injuryRepository.save(injury2);
        //batch.add(injury2);

        Injuries injury3 = new Injuries();
        injury3.setApplicationUser(user);
        injury3.setAffectedArea(BodyPart.LOWER_LEG_REGION);
        injury3.setLastHealthyDate(today.minusDays(90));
        injury3.setLastInjuryDate(today.minusDays(76)); // recovered
        injury3.setInjuryIndex(0.45);
        injuryRepository.save(injury3);
        //batch.add(injury3);

        Injuries injury4 = new Injuries();
        injury4.setApplicationUser(user);
        injury4.setAffectedArea(BodyPart.UPPER_REGION);
        injury4.setLastHealthyDate(today.minusDays(50));
        injury4.setLastInjuryDate(today.minusDays(38)); // recovered
        injury4.setInjuryIndex(0.38);
        injuryRepository.save(injury4);
        //batch.add(injury4);

        Injuries injury5 = new Injuries();
        injury5.setApplicationUser(user);
        injury5.setAffectedArea(BodyPart.LOWER_LEG_REGION);
        injury5.setLastHealthyDate(today.minusDays(25));
        injury5.setLastInjuryDate(today.minusDays(17)); // recovered
        injury5.setInjuryIndex(0.35);
        injuryRepository.save(injury5);
        //batch.add(injury5);
    }
}