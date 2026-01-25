package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.endpoint.dto.ActivityDto;
import com.smartroute.smartroute1.endpoint.dto.RouteDto;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.entity.enums.Weekday;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.service.OpenRouteServiceService;
import com.smartroute.smartroute1.service.RouteGenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
public class RouteGenerationServiceTest {

    @Autowired
    private RouteGenerationService routeGenerationService;

    @MockitoBean
    private ActivityRepository activityRepository;

    @MockitoBean
    private OpenRouteServiceService openRouteServiceService;

    @Test
    void generateRouteDetail_withNewUser_shouldGenerateSafeDefaultDetails() throws Exception {
        ApplicationUser user = new ApplicationUser();
        user.setSex(Sex.MALE);
        user.setEmail("test@email.com");
        user.setPassword("password");
        user.setVerified(true);
        user.setActiveWeekdays(Set.of(Weekday.MONDAY, Weekday.TUESDAY, Weekday.WEDNESDAY));
        user.setFirstname("firstname");
        user.setLastname("lastname");
        user.setId(1000L);
        user.setExperienceLevel(ExperienceLevel.BEGINNER);
        user.setBirthdate(LocalDate.now().minusYears(20));
        user.setHeight(177);
        user.setWeight(new BigDecimal(66));

        when(activityRepository.findTop10ByUserAndTypeIsAndWorkoutTypeIsOrderByStartDateDesc(
                any(), any(), any(), any())).thenReturn(List.of());

        RouteDto routeDto = routeGenerationService.generateRouteDetails(user, WorkoutType.EASY_RUN, 0.8);

        assertAll(
                () -> assertTrue(routeDto.getDistance() > 1000),
                () -> assertTrue(routeDto.getPace() < 3600)
        );
    }

    @Test
    void generateRouteDetail_withUserThatHasHadSomeRuns_shouldGenerateSimilarRouteDetails() throws Exception {
        ApplicationUser user = new ApplicationUser();
        user.setSex(Sex.MALE);
        user.setEmail("test@email.com");
        user.setPassword("password");
        user.setVerified(true);
        user.setActiveWeekdays(Set.of(Weekday.MONDAY, Weekday.TUESDAY, Weekday.WEDNESDAY));
        user.setFirstname("firstname");
        user.setLastname("lastname");
        user.setId(1000L);
        user.setExperienceLevel(ExperienceLevel.BEGINNER);
        user.setBirthdate(LocalDate.now().minusYears(20));
        user.setHeight(177);
        user.setWeight(new BigDecimal(66));


        Activity activity1 = new Activity();
        activity1.setDistance(6000);
        activity1.setUser(user);
        activity1.setType("Run");
        activity1.setStartDate(Instant.now().minusSeconds(172800));
        activity1.setMovingTime(1000);
        activity1.setTotalElevationGain(200);
        activity1.setElapsedTime(1100);
        when(activityRepository.findTop10ByUserAndTypeIsAndWorkoutTypeIsOrderByStartDateDesc(
                any(), any(), any(), any())).thenReturn(List.of(activity1));

        RouteDto routeDto = routeGenerationService.generateRouteDetails(user, WorkoutType.EASY_RUN, 0.8);

        assertAll(
                () -> assertTrue(routeDto.getDistance() - activity1.getDistance() < 100),
                () -> assertTrue(routeDto.getPace() - activity1.getMovingTime() / activity1.getDistance() < 10)
        );
    }
}
