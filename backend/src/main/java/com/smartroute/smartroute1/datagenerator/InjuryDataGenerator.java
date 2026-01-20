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

    public InjuryDataGenerator(InjuryRepository injuryRepository,
                               UserRepository userRepository) {
        this.injuryRepository = injuryRepository;
        this.userRepository = userRepository;
    }

    // Public methods to get injury periods for each user type
    public List<InjuryPeriod> getBeginnerInjuryPeriods() {
        LocalDate today = LocalDate.now();
        List<InjuryPeriod> periods = new ArrayList<>();

        periods.add(new InjuryPeriod(
                today.minusDays(120),
                today.minusDays(106),
                BodyPart.FEET_REGION,
                0.35
        ));
        periods.add(new InjuryPeriod(
                today.minusDays(65),
                today.minusDays(55),
                BodyPart.KNEE_REGION,
                0.42
        ));
        periods.add(new InjuryPeriod(
                today.minusDays(30),
                today.minusDays(23),
                BodyPart.LOWER_LEG_REGION,
                0.28
        ));

        return periods;
    }

    public List<InjuryPeriod> getAdvancedInjuryPeriods() {
        LocalDate today = LocalDate.now();
        List<InjuryPeriod> periods = new ArrayList<>();

        periods.add(new InjuryPeriod(
                today.minusDays(180),
                today.minusDays(159),
                BodyPart.UPPER_LEG_REGION,
                0.55
        ));
        periods.add(new InjuryPeriod(
                today.minusDays(95),
                today.minusDays(67),
                BodyPart.CORE_REGION,
                0.48
        ));
        periods.add(new InjuryPeriod(
                today.minusDays(45),
                today.minusDays(27),
                BodyPart.FEET_REGION,
                0.52
        ));
        // Active injury (no end date)
        periods.add(new InjuryPeriod(
                today.minusDays(8),
                null, // still injured!
                BodyPart.FEET_REGION,
                0.58
        ));

        return periods;
    }

    public List<InjuryPeriod> getProInjuryPeriods() {
        LocalDate today = LocalDate.now();
        List<InjuryPeriod> periods = new ArrayList<>();

        periods.add(new InjuryPeriod(
                today.minusDays(240),
                today.minusDays(198),
                BodyPart.BONE_FRACTURE,
                0.82
        ));
        periods.add(new InjuryPeriod(
                today.minusDays(150),
                today.minusDays(125),
                BodyPart.CORE_REGION,
                0.62
        ));
        periods.add(new InjuryPeriod(
                today.minusDays(90),
                today.minusDays(76),
                BodyPart.LOWER_LEG_REGION,
                0.45
        ));
        periods.add(new InjuryPeriod(
                today.minusDays(50),
                today.minusDays(38),
                BodyPart.UPPER_REGION,
                0.38
        ));
        periods.add(new InjuryPeriod(
                today.minusDays(25),
                today.minusDays(17),
                BodyPart.LOWER_LEG_REGION,
                0.35
        ));

        return periods;
    }

    public List<InjuryPeriod> getEliteInjuryPeriods() {
        LocalDate today = LocalDate.now();
        List<InjuryPeriod> periods = new ArrayList<>();

        // Elite runner with just ONE significant injury mid-season
        periods.add(new InjuryPeriod(
                today.minusDays(180),
                today.minusDays(160),
                BodyPart.UPPER_LEG_REGION,
                0.68
        ));

        return periods;
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

        for (int i = 0; i < users.size(); i++) {
            switch (i) {
                case 1 -> saveInjuryPeriods(users.get(i), getBeginnerInjuryPeriods());
                case 2 -> saveInjuryPeriods(users.get(i), getAdvancedInjuryPeriods());
                case 3 -> saveInjuryPeriods(users.get(i), getProInjuryPeriods());
                case 4 -> saveInjuryPeriods(users.get(i), getEliteInjuryPeriods());
                case 5 -> saveInjuryPeriods(users.get(i), getInjuryAwareBeginnerPeriods()); // NEW
                case 6 -> saveInjuryPeriods(users.get(i), getInjuryAwareAdvancedPeriods()); // NEW
                case 7 -> saveInjuryPeriods(users.get(i), getEliteWithOneInjuryPeriods()); // NEW
                default -> {
                    // Random injuries for other users (existing logic)
                    generateRandomInjuries(users.get(i));
                }
            }
        }

        LOGGER.info("Generated injuries for {} users.", users.size());
    }

    private void saveInjuryPeriods(ApplicationUser user, List<InjuryPeriod> periods) {
        for (InjuryPeriod period : periods) {
            Injuries injury = new Injuries();
            injury.setApplicationUser(user);
            injury.setAffectedArea(period.bodyPart());
            injury.setLastHealthyDate(period.start());
            injury.setLastInjuryDate(period.end()); // can be null for active injuries
            injury.setInjuryIndex(period.injuryIndex());
            injuryRepository.save(injury);
        }
    }

    private void generateRandomInjuries(ApplicationUser user) {
        // Your existing random injury generation logic
        LocalDate today = LocalDate.now();
        BodyPart[] bodyParts = BodyPart.values();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int injuryCount = random.nextInt(20);
        if (injuryCount == 0) {
            return;
        }

        for (int i = 0; i < injuryCount; i++) {
            Injuries injury = new Injuries();
            injury.setApplicationUser(user);
            injury.setAffectedArea(bodyParts[random.nextInt(bodyParts.length)]);

            boolean recent = random.nextDouble() < 0.3;
            int daysAgo = recent
                    ? random.nextInt(15)
                    : random.nextInt(15, 366);

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
                int daysToHealthy = random.nextInt(1, 29);
                lastInjuryDate = lastHealthyDate.plusDays(daysToHealthy);
                if (lastInjuryDate.isAfter(today)) {
                    lastInjuryDate = today;
                }
            }
            injury.setLastInjuryDate(lastInjuryDate);
            injuryRepository.save(injury);
        }
    }

    public List<InjuryPeriod> getInjuryAwareBeginnerPeriods() {
        LocalDate today = LocalDate.now();
        List<InjuryPeriod> periods = new ArrayList<>();

        // Beginner with 2 injuries over the year
        periods.add(new InjuryPeriod(
                today.minusDays(280),
                today.minusDays(266), // 14 days
                BodyPart.KNEE_REGION,
                0.38
        ));
        periods.add(new InjuryPeriod(
                today.minusDays(120),
                today.minusDays(106), // 14 days
                BodyPart.FEET_REGION,
                0.32
        ));

        return periods;
    }

    public List<InjuryPeriod> getInjuryAwareAdvancedPeriods() {
        LocalDate today = LocalDate.now();
        List<InjuryPeriod> periods = new ArrayList<>();

        // Advanced runner with 3 injuries
        periods.add(new InjuryPeriod(
                today.minusDays(310),
                today.minusDays(289), // 21 days
                BodyPart.UPPER_LEG_REGION,
                0.52
        ));
        periods.add(new InjuryPeriod(
                today.minusDays(180),
                today.minusDays(165), // 15 days
                BodyPart.CORE_REGION,
                0.45
        ));
        periods.add(new InjuryPeriod(
                today.minusDays(60),
                today.minusDays(48), // 12 days
                BodyPart.LOWER_LEG_REGION,
                0.48
        ));

        return periods;
    }

    public List<InjuryPeriod> getEliteWithOneInjuryPeriods() {
        LocalDate today = LocalDate.now();
        List<InjuryPeriod> periods = new ArrayList<>();

        // Elite runner with ONE significant injury mid-season
        // This will show dramatic ATL drop and rebuild
        periods.add(new InjuryPeriod(
                today.minusDays(180),
                today.minusDays(155), // 25 days off - significant!
                BodyPart.UPPER_LEG_REGION,
                0.72
        ));

        return periods;
    }
}