package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.endpoint.UserEndpoint;
import com.smartroute.smartroute1.endpoint.dto.ConsistencyScoreResultDto;
import com.smartroute.smartroute1.endpoint.dto.statistics.*;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Injuries;
import com.smartroute.smartroute1.entity.enums.BodyPart;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.Weekday;
import com.smartroute.smartroute1.entity.enums.WorkoutType;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.InjuryRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.ConsistencyAnalyzerService;
import com.smartroute.smartroute1.service.FatigueAndOverloadService;
import com.smartroute.smartroute1.service.GymWorkoutSelectorService;
import com.smartroute.smartroute1.service.StatisticsService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;


@SpringBootTest()
@ActiveProfiles({"test", "generateData"})
@Transactional
@AutoConfigureMockMvc
public class StatisticsServiceTest {

    @Autowired
    private StatisticsService service;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private InjuryRepository injuryRepository;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private GymWorkoutSelectorService gymWorkoutSelectorService;

    @MockitoBean
    private ConsistencyAnalyzerService consistencyAnalyzerService;

    @MockitoBean
    private FatigueAndOverloadService fatigueAndOverloadService;

    private int numberOfDaysInYear;

    @BeforeEach
    public void setUp() {
        numberOfDaysInYear = 365;
        if (Year.isLeap(LocalDate.now().getYear())) {
            numberOfDaysInYear = 366;
        }
    }


    @Test
    public void test_WhenCallingEndpoint_ThenReturnsStatus200() throws Exception {
        createUser();

        mockMvc.perform(get("/api/v1/user/statistics")
                        .with(user("stattest@stattest.com").roles("USER")))
                .andExpect(status().isOk());

    }

    @Test
    public void test_WhenGetStatisticsWithNoData_ThenReturnsEmptyDto() {
        ApplicationUser user = createUser();

        HistoryDto dto = new HistoryDto(
                service.getConsistencyHistory(user),
                service.getGymHistory(user),
                service.getInjuryHistory(user),
                service.getRunHistory(user)
        );

        assertAll(
                () -> assertEquals(0, dto.getGymHistory().getNoOfGymRecommendations()),
                () -> assertEquals(0, dto.getInjuryHistory().getNoOfInjuries()),
                () -> assertEquals(0, dto.getRunHistory().getNumberOfRuns())
        );
    }

    @Test
    public void test_GivenUserWithDataForRunHistory_WhenGetRunHistory_ThenReturnsRunHistoryCorrectly() {
        ApplicationUser user = createUser();

        Activity a1 = new Activity();
        a1.setUser(user);
        a1.setDistance(4000f);
        a1.setElapsedTime(60);
        a1.setStartDate(LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        a1.setType("Run");

        Activity a2 = new Activity();
        a2.setUser(user);
        a2.setDistance(6000f);
        a2.setElapsedTime(40);
        a2.setStartDate(LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        a2.setType("Run");

        Activity a3 = new Activity();
        a3.setUser(user);
        a3.setDistance(4000f);
        a3.setElapsedTime(60);
        a3.setStartDate(LocalDate.now().minusDays(400).atStartOfDay(ZoneId.systemDefault()).toInstant());
        a3.setType("Run");

        Activity a4 = new Activity();
        a4.setUser(user);
        a4.setDistance(6000f);
        a4.setElapsedTime(40);
        a4.setStartDate(LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());


        activityRepository.save(a1);
        activityRepository.save(a2);
        //Not eligible
        activityRepository.save(a3);
        activityRepository.save(a4);

        RunHistoryDto runHistoryDto = service.getRunHistory(user);

        assertAll(
                () -> assertEquals(2, runHistoryDto.getNumberOfRuns()),
                () -> assertEquals(10000f, runHistoryDto.getTotalDistance()),
                () -> assertEquals(100, runHistoryDto.getTotalRunTime())
        );
    }

    @Test
    public void test_GivenUserWithDataForInjuryHistory_WhenGetRunHistory_ThenReturnsRunHistoryCorrectly() {
        ApplicationUser user = createUser();

        injuryRepository.save(
                new Injuries(
                        user,
                        3,
                        BodyPart.KNEE_REGION,
                        LocalDate.now().minusDays(10), LocalDate.now().minusDays(9)
                )
        );

        injuryRepository.save(
                new Injuries(
                        user,
                        2,
                        BodyPart.FEET_REGION,
                        LocalDate.now().minusDays(50), LocalDate.now().minusDays(49)
                )
        );


        injuryRepository.save(
                new Injuries(
                        user,
                        4,
                        BodyPart.BONE_FRACTURE,
                        LocalDate.now().minusDays(400), LocalDate.now().minusDays(399)
                )
        );

        InjuryHistoryDto injuryHistoryDto = service.getInjuryHistory(user);


        assertAll(
                () -> assertEquals(2, injuryHistoryDto.getNoOfInjuries()),
                () -> assertEquals(2, injuryHistoryDto.getInjuriesList().size()),
                () -> assertEquals(
                        LocalDate.now().minusDays(50),
                        injuryHistoryDto.getInjuriesList().getFirst().getLastHealthyDate()
                )
        );
    }

    @Test
    public void test_WhenGivenUserWithGymData_ThenReturnsGymHistoryCorrectly() {
        ApplicationUser user = createUser();
        gymWorkoutSelectorService.getGymWorkout(user, new HashMap<>(), 50);
        gymWorkoutSelectorService.getGymWorkout(user, new HashMap<>(), 50);

        GymHistoryDto gymHistoryDto = service.getGymHistory(user);


        assertEquals(2, gymHistoryDto.getNoOfGymRecommendations());

    }

    @Test
    void test_GivenUser_WhenGetConsistencyHistory_ThenReturnsHistoryForWholeYear() throws Exception {

        ApplicationUser user = createUser();
        user.setActiveWeekdays(EnumSet.of(
                Weekday.MONDAY,
                Weekday.WEDNESDAY,
                Weekday.FRIDAY
        ));
        user.setExperienceLevel(ExperienceLevel.INTERMEDIATE);
        userRepository.save(user);
        ConsistencyScoreResultDto score =
                new ConsistencyScoreResultDto(0.8, 0.4, 0.5);

        when(consistencyAnalyzerService.computeScore(
                any(),
                any(),
                any(),
                anyInt()
        )).thenReturn(score);

        when(fatigueAndOverloadService.ctlOn(any(), any()))
                .thenReturn(45.0);
        when(fatigueAndOverloadService.atlOn(any(), any()))
                .thenReturn(55.0);
        when(fatigueAndOverloadService.tsbOn(any(), any()))
                .thenReturn(-10.0);


        ConsistencyHistoryDto result = service.getConsistencyHistory(user);


        assertAll(
                () -> assertEquals(numberOfDaysInYear, result.getConsistencyHistory().size()),
                () -> assertEquals(numberOfDaysInYear, result.getCtlHistory().size()),
                () -> assertEquals(numberOfDaysInYear, result.getAtlHistory().size()),
                () -> assertEquals(numberOfDaysInYear, result.getTsbHistory().size()),
                () -> Assertions.assertTrue(result.getConsistencyHistory().containsValue(score)),
                () -> Assertions.assertTrue(result.getAtlHistory().containsValue(55.0)),
                () -> Assertions.assertTrue(result.getCtlHistory().containsValue(45.0)),
                () -> Assertions.assertTrue(result.getTsbHistory().containsValue(-10.0))
        );
    }

    private ApplicationUser createUser() {
        ApplicationUser user = new ApplicationUser("stattest@stattest.com", "Max12345678", "Max", "Mustermann");
        user.setExperienceLevel(ExperienceLevel.BEGINNER);
        user = userRepository.save(user);
        return user;
    }
}
