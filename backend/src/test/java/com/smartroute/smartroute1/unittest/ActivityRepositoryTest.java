package com.smartroute.smartroute1.unittest;


import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.StravaAccountRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest()
@ActiveProfiles({"test", "generateData"})
@Transactional
public class ActivityRepositoryTest {

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private StravaAccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    private Activity activity(long id, ApplicationUser user, String date) {
        Activity a = new Activity();
        a.setName("A" + id);
        a.setUser(user);
        a.setStartDate(Instant.parse(date));
        return activityRepository.save(a);
    }

    private ApplicationUser newUser(long id) {
        ApplicationUser u = new ApplicationUser();
        u.setEmail("u " + id + "@test.com");
        u.setActiveWeekdays(null);
        u.setHeight(182);
        u.setFirstname("test");
        u.setBirthdate(LocalDate.now());
        u.setLastname("test");
        u.setExperienceLevel(ExperienceLevel.BEGINNER);
        u.setPassword("test");
        u.setSex(Sex.OTHER);
        u.setWeight(BigDecimal.TEN);


        return userRepository.save(u);
    }

    @BeforeEach
    public void setUp() {
        activityRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        activityRepository.flush();
        accountRepository.flush();
        userRepository.flush();
    }


    @Test
    void testFindAllByStravaAccountAndStartDateBetweenOrderByStartDateAsc() {

        ApplicationUser acc1 = newUser(1L);
        ApplicationUser acc2 = newUser(2L);


        Instant start = Instant.parse("2025-01-10T00:00:00Z");
        Instant end = Instant.parse("2025-01-21T00:00:00Z");

        activity(2L, acc1, "2025-01-10T12:00:00Z"); // include
        activity(1L, acc1, "2025-01-09T12:00:00Z"); // exclude
        activity(4L, acc1, "2025-01-20T02:00:00Z"); // include
        activity(5L, acc1, "2025-01-21T12:00:00Z"); // exclude
        activity(3L, acc1, "2025-01-15T12:00:00Z"); // include


        activity(6L, acc2, "2025-01-15T12:00:00Z"); // exclude

        List<Activity> results =
                activityRepository.findAllByUserAndStartDateBetweenOrderByStartDateAsc(
                        acc1, start, end);
        assertAll(
                () -> assertEquals(3, results.size()),
                () -> assertEquals(Instant.parse("2025-01-10T12:00:00Z"), results.get(0).getStartDate()),
                () -> assertEquals(Instant.parse("2025-01-15T12:00:00Z"), results.get(1).getStartDate()),
                () -> assertEquals(Instant.parse("2025-01-20T02:00:00Z"), results.get(2).getStartDate())
        );
    }
}
