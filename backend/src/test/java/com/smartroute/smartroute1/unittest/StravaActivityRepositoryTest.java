package com.smartroute.smartroute1.unittest;


import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.StravaAccount;
import com.smartroute.smartroute1.entity.StravaActivity;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.repository.StravaAccountRepository;
import com.smartroute.smartroute1.repository.StravaActivityRepository;
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
public class StravaActivityRepositoryTest {

    @Autowired
    private StravaActivityRepository activityRepository;

    @Autowired
    private StravaAccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    private StravaActivity activity(long id, StravaAccount acc, String date) {
        StravaActivity a = new StravaActivity();
        a.setId(id);
        a.setName("A" + id);
        a.setStravaAccount(acc);
        a.setStartDate(Instant.parse(date));
        return activityRepository.save(a);
    }

    private StravaAccount newAcc(long id) {
        StravaAccount a = new StravaAccount();
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
        userRepository.save(u);

        a.setUser(u);
        return accountRepository.save(a);
    }

    @BeforeEach
    public void setUp() {
        activityRepository.deleteAll();
        accountRepository.deleteAll();
    }


    @Test
    void testFindAllByStravaAccountAndStartDateBetweenOrderByStartDateAsc() {

        StravaAccount acc1 = newAcc(1L);
        StravaAccount acc2 = newAcc(2L);


        Instant start = Instant.parse("2025-01-10T00:00:00Z");
        Instant end = Instant.parse("2025-01-20T23:59:59Z");

        activity(2L, acc1, "2025-01-10T12:00:00Z"); // include
        activity(1L, acc1, "2025-01-09T12:00:00Z"); // exclude
        activity(4L, acc1, "2025-01-20T02:00:00Z"); // include
        activity(5L, acc1, "2025-01-21T12:00:00Z"); // exclude
        activity(3L, acc1, "2025-01-15T12:00:00Z"); // include


        activity(6L, acc2, "2025-01-15T12:00:00Z"); // exclude

        List<StravaActivity> results =
                activityRepository.findAllByStravaAccountAndStartDateBetweenOrderByStartDateAsc(
                        acc1, start, end);

        assertAll(
                () -> assertEquals(3, results.size()),
                () -> assertEquals(2L, results.get(0).getId()),
                () -> assertEquals(3L, results.get(1).getId()),
                () -> assertEquals(4L, results.get(2).getId())
        );
    }
}
