package com.smartroute.smartroute1.integrationtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.endpoint.dto.*;
import com.smartroute.smartroute1.endpoint.mapper.UserMapper;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Injuries;
import com.smartroute.smartroute1.entity.enums.BodyPart;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.entity.enums.Weekday;
import com.smartroute.smartroute1.repository.InjuryRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.security.JwtTokenizer;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static com.smartroute.smartroute1.basetest.TestData.*;
import static com.smartroute.smartroute1.basetest.TestData.ORIGIN;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "generateData"})
@AutoConfigureMockMvc
class UserEndpointTest extends BaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenizer jwtTokenizer;

    @Autowired
    private InjuryRepository injuryRepository;

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("test", "test"))
            .withPerMethodLifecycle(false);


    // ==================== USER CREATION TESTS ====================

    @Test
    void createNewValidUser_shouldReturn201() throws Exception {
        ApplicationUser user = new ApplicationUser("test@email.com", "SuperSecretPassword", "Konsti", "U");
        CreateUserDto studentDto = userMapper.applicationUserToDto(user);
        String body = objectMapper.writeValueAsString(studentDto);

        MvcResult mvcResult = this.mockMvc.perform(post(USER_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        MockHttpServletResponse response = mvcResult.getResponse();

        assertAll(
                () -> assertEquals(HttpStatus.CREATED.value(), response.getStatus()),
                () -> assertEquals(MediaType.APPLICATION_JSON_VALUE, response.getContentType())
        );

        CreateUserDto createdUserDto = objectMapper.readValue(response.getContentAsString(),
                CreateUserDto.class);

        assertAll(
                () -> assertNotNull(createdUserDto),
                () -> assertEquals(createdUserDto.getEmail(), studentDto.getEmail()),
                () -> assertEquals(createdUserDto.getFirstname(), studentDto.getFirstname()),
                () -> assertEquals(createdUserDto.getLastname(), studentDto.getLastname()),
                () -> assertTrue(passwordEncoder.matches(studentDto.getPassword(), createdUserDto.getPassword()))
        );
    }

    @Test
    void createUserWithExistingEmail_shouldFailWith422() throws Exception {
        CreateUserDto user = new CreateUserDto();
        user.setPassword("Password123");
        user.setFirstname("test");
        user.setLastname("test");
        user.setEmail(DEFAULT_USER_EMAIL);
        String body = objectMapper.writeValueAsString(user);

        MvcResult mvcResult = this.mockMvc.perform(post(USER_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        assertAll(
                () -> assertNotNull(mvcResult.getResponse()),
                () -> assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), mvcResult.getResponse().getStatus())
        );
    }

    @Test
    void createNewInvalidUser_shouldReturn422() throws Exception {
        ApplicationUser user = new ApplicationUser();
        CreateUserDto userDto = userMapper.applicationUserToDto(user);
        String body = objectMapper.writeValueAsString(userDto);

        MvcResult mvcResult = this.mockMvc.perform(post(USER_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        MockHttpServletResponse response = mvcResult.getResponse();

        assertAll(
                () -> assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), response.getStatus()),
                () -> {
                    String content = response.getContentAsString();
                    assertTrue(content.contains("Email cannot be null"));
                }
        );
    }

    // ==================== RESEND VERIFICATION EMAIL TESTS ====================

    @Test
    void resendVerificationEmail_withValidEmail_shouldReturn200() throws Exception {
        // Given - create a test user first
        createTestUser("test@example.com", "Password123!", false);

        Map<String, String> payload = new HashMap<>();
        payload.put("email", "test@example.com");

        // When & Then
        mockMvc.perform(post("/api/v1/user/verify/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        // Verify email was sent
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertTrue(messages.length > 0, "At least one email should be sent");
    }

    @Test
    void resendVerificationEmail_withNonExistentEmail_shouldReturn200ForSecurity() throws Exception {
        // Given - email doesn't exist (for security, should still return OK)
        Map<String, String> payload = new HashMap<>();
        payload.put("email", "nonexistent@example.com");

        // When & Then - Should return 200 to prevent email enumeration
        mockMvc.perform(post("/api/v1/user/verify/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    void resendVerificationEmail_withoutOriginHeader_shouldReturn200() throws Exception {
        // Given
        createTestUser("test@example.com", "Password123!", false);

        Map<String, String> payload = new HashMap<>();
        payload.put("email", "test@example.com");

        // When & Then
        mockMvc.perform(post("/api/v1/user/verify/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    void resendVerificationEmail_withEmptyPayload_shouldReturn200() throws Exception {
        // Given
        Map<String, String> payload = new HashMap<>();

        // When & Then - Service should handle null email gracefully
        mockMvc.perform(post("/api/v1/user/verify/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    void resendVerificationEmail_withAlreadyVerifiedUser_shouldReturn200() throws Exception {
        // Given - create verified user
        createTestUser("test@example.com", "Password123!", true);

        Map<String, String> payload = new HashMap<>();
        payload.put("email", "test@example.com");

        // When & Then - Should handle gracefully
        mockMvc.perform(post("/api/v1/user/verify/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    // ==================== VERIFY EMAIL TESTS ====================

    @Test
    void verifyEmail_withValidJwtToken_shouldReturn200AndVerifyUser() throws Exception {
        // Given - create unverified user
        ApplicationUser user = createTestUser("test@example.com", "Password123!", false);

        // Generate valid JWT token for email verification
        String validToken = jwtTokenizer.buildVerificationToken(user.getEmail());

        // When
        mockMvc.perform(get("/api/v1/user/verify/{token}", validToken))
                .andExpect(status().isOk());

        // Then - verify user is now verified
        ApplicationUser verifiedUser = userRepository.findUserByEmail("test@example.com");
        assertTrue(verifiedUser.isVerified(), "User should be verified after valid token");
    }


    @Test
    void verifyEmail_withTokenForNonExistentUser_shouldReturn401() throws Exception {
        // Given - valid JWT but user doesn't exist
        String token = jwtTokenizer.buildVerificationToken("nonexistent@example.com");

        // When & Then
        mockMvc.perform(get("/api/v1/user/verify/{token}", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyEmail_withAlreadyVerifiedUser_shouldStillReturn200() throws Exception {
        // Given - create already verified user
        ApplicationUser user = createTestUser("test@example.com", "Password123!", true);
        String token = jwtTokenizer.buildVerificationToken(user.getEmail());

        // When & Then - Should be idempotent
        mockMvc.perform(get("/api/v1/user/verify/{token}", token))
                .andExpect(status().isOk());
    }

    // ==================== REQUEST PASSWORD RESET TESTS ====================

    @Test
    void requestPasswordReset_withValidEmail_shouldReturn200() throws Exception {
        // Given
        createTestUser("user@example.com", "Password123!", true);

        EmailDto emailDto = new EmailDto();
        emailDto.email = "user@example.com";

        // When & Then
        mockMvc.perform(post("/api/v1/user/reset_password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .content(objectMapper.writeValueAsString(emailDto)))
                .andExpect(status().isOk());

        // Verify password reset email was sent
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertTrue(messages.length > 0, "Password reset email should be sent");
    }

    @Test
    void requestPasswordReset_withNonExistentEmail_shouldReturn200ForSecurity() throws Exception {
        // Given - for security, should return OK even if email doesn't exist
        EmailDto emailDto = new EmailDto();
        emailDto.email = "nonexistent@example.com";

        // When & Then - Should return 200 to prevent email enumeration
        mockMvc.perform(post("/api/v1/user/reset_password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .content(objectMapper.writeValueAsString(emailDto)))
                .andExpect(status().isOk());
    }

    @Test
    void requestPasswordReset_withoutOriginHeader_shouldReturn200() throws Exception {
        // Given
        createTestUser("user@example.com", "Password123!", true);

        EmailDto emailDto = new EmailDto();
        emailDto.email = "user@example.com";

        // When & Then
        mockMvc.perform(post("/api/v1/user/reset_password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emailDto)))
                .andExpect(status().isOk());
    }

    @Test
    void requestPasswordReset_withInvalidEmailFormat_shouldReturn200() throws Exception {
        // Given - Service layer should handle validation
        EmailDto emailDto = new EmailDto();
        emailDto.email = "not-a-valid-email";

        // When & Then
        mockMvc.perform(post("/api/v1/user/reset_password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .content(objectMapper.writeValueAsString(emailDto)))
                .andExpect(status().isOk());
    }

    @Test
    void requestPasswordReset_withUnverifiedUser_shouldReturn200() throws Exception {
        // Given - unverified user requests password reset
        createTestUser("user@example.com", "Password123!", false);

        EmailDto emailDto = new EmailDto();
        emailDto.email = "user@example.com";

        // When & Then - depends on your business logic
        mockMvc.perform(post("/api/v1/user/reset_password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .content(objectMapper.writeValueAsString(emailDto)))
                .andExpect(status().isOk());
    }

    // ==================== CHANGE PASSWORD WITH TOKEN TESTS ====================

    @Test
    void changePasswordWithToken_withValidJwtAndPassword_shouldReturn200() throws Exception {
        // Given
        ApplicationUser user = createTestUser("user@example.com", "OldPassword123!", true);
        String resetToken = jwtTokenizer.buildVerificationToken(user.getEmail());

        PasswordResetDto resetDto = new PasswordResetDto();
        resetDto.password = "NewSecurePassword123!";
        resetDto.repeatPassword = "NewSecurePassword123!";

        // When
        mockMvc.perform(post("/api/v1/user/reset_password/{token}", resetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetDto)))
                .andExpect(status().isOk());

        // Then - verify password was actually changed
        ApplicationUser updatedUser = userRepository.findUserByEmail("user@example.com");
        assertTrue(passwordEncoder.matches("NewSecurePassword123!", updatedUser.getPassword()),
                "Password should be updated to new password");
        assertFalse(passwordEncoder.matches("OldPassword123!", updatedUser.getPassword()),
                "Old password should no longer work");
    }

    @Test
    void changePasswordWithToken_withWeakPassword_shouldReturn4xx() throws Exception {
        // Given
        ApplicationUser user = createTestUser("user@example.com", "OldPassword123!", true);
        String resetToken = jwtTokenizer.buildVerificationToken(user.getEmail());

        PasswordResetDto resetDto = new PasswordResetDto();
        resetDto.password = "weak";
        resetDto.repeatPassword = "weak";

        // When & Then
        mockMvc.perform(post("/api/v1/user/reset_password/{token}", resetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetDto)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void changePasswordWithToken_withMismatchedPasswords_shouldReturn4xx() throws Exception {
        // Given
        ApplicationUser user = createTestUser("user@example.com", "OldPassword123!", true);
        String resetToken = jwtTokenizer.buildVerificationToken(user.getEmail());

        PasswordResetDto resetDto = new PasswordResetDto();
        resetDto.password = "Password123!";
        resetDto.repeatPassword = "DifferentPassword123!";

        // When & Then
        mockMvc.perform(post("/api/v1/user/reset_password/{token}", resetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetDto)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void changePasswordWithToken_withEmptyPassword_shouldReturn4xx() throws Exception {
        // Given
        ApplicationUser user = createTestUser("user@example.com", "OldPassword123!", true);
        String resetToken = jwtTokenizer.buildVerificationToken(user.getEmail());

        PasswordResetDto resetDto = new PasswordResetDto();
        resetDto.password = "";
        resetDto.repeatPassword = "";

        // When & Then
        mockMvc.perform(post("/api/v1/user/reset_password/{token}", resetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetDto)))
                .andExpect(status().is4xxClientError());
    }


    @Test
    void changePasswordWithToken_forNonExistentUser_shouldReturn401() throws Exception {
        // Given - valid JWT but user doesn't exist
        String token = jwtTokenizer.buildVerificationToken("nonexistent@example.com");

        PasswordResetDto resetDto = new PasswordResetDto();
        resetDto.password = "NewPassword123!";
        resetDto.repeatPassword = "NewPassword123!";

        // When & Then
        mockMvc.perform(post("/api/v1/user/reset_password/{token}", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetDto)))
                .andExpect(status().isUnauthorized());
    }


    // ==================== RATE LIMIT TEST ====================

    @Test
    void requestPasswordReset_whenRequestsExceedLimit_shouldReturn429() throws Exception {
        // Given
        createTestUser("reset_ratelimit@email.com", "Password123!", true);
        greenMail.purgeEmailFromAllMailboxes();

        // payload
        Map<String, String> payload = new HashMap<>();
        payload.put("email", "reset_ratelimit@email.com");
        String body = objectMapper.writeValueAsString(payload);

        // First 5 should pass
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/user/reset_password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.ORIGIN, ORIGIN)
                            .content(body))
                    .andExpect(status().isOk());

            MimeMessage[] messages = greenMail.getReceivedMessages();
            assertEquals(i + 1, messages.length, "Password reset email should be sent");
            assertEquals("reset_ratelimit@email.com",
                    messages[i].getAllRecipients()[0].toString());
        }

        // 6th should be rate limited
        mockMvc.perform(post("/api/v1/user/reset_password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .content(body))
                .andExpect(status().isTooManyRequests());
    }

    // ==================== UPDATE PERSONAL USER DATA ====================
    @Test
    void updatePersonalUserData_withValidData_shouldReturn200() throws Exception {
        createTestUser("personal_data@email.com", "Password123!", true);

        // payload
        PersonalDataDto personalDataDto = createTestPersonalDataDto();

        // authentication
        String authToken = jwtTokenizer.getAuthToken("personal_data@email.com", List.of("ROLE_USER"));

        // do request
        var response = mockMvc.perform(put("/api/v1/user/personal-data")
                        .header(HttpHeaders.AUTHORIZATION, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(personalDataDto)))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        UserDetailDto updatedUser = objectMapper.readValue(response.getContentAsString(),
                UserDetailDto.class);

        assertAll(
                () -> assertNotNull(updatedUser),
                () -> assertEquals(updatedUser.getSex(), personalDataDto.getSex()),
                () -> assertEquals(updatedUser.getHeight(), personalDataDto.getHeight()),
                () -> assertEquals(updatedUser.getWeight(), personalDataDto.getWeight()),
                () -> assertEquals(updatedUser.getBirthdate(), personalDataDto.getBirthdate()),
                () -> assertEquals(updatedUser.getExperienceLevel(), personalDataDto.getExperienceLevel()),
                () -> assertEquals(updatedUser.getActiveWeekdays(), personalDataDto.getActiveWeekdays())
        );
    }

    @Test
    void updatePersonalUserData_withoutJwtToken_shouldReturn403() throws Exception {
        PersonalDataDto personalDataDto = createTestPersonalDataDto();
        mockMvc.perform(put("/api/v1/user/personal-data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(personalDataDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatePersonalUserData_withInvalidHeight_shouldReturn400() throws Exception {
        String authToken = jwtTokenizer.getAuthToken("personal_data@email.com", List.of("ROLE_USER"));
        PersonalDataDto personalDataDto = createTestPersonalDataDto();
        personalDataDto.setHeight(-10);
        mockMvc.perform(put("/api/v1/user/personal-data")
                        .header(HttpHeaders.AUTHORIZATION, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(personalDataDto)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updatePersonalUserData_withInvalidWeight_shouldReturn400() throws Exception {
        String authToken = jwtTokenizer.getAuthToken("personal_data@email.com", List.of("ROLE_USER"));
        PersonalDataDto personalDataDto = createTestPersonalDataDto();
        personalDataDto.setWeight(new BigDecimal(-10));
        mockMvc.perform(put("/api/v1/user/personal-data")
                        .header(HttpHeaders.AUTHORIZATION, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(personalDataDto)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updatePersonalUserData_withInvalidBirthdate_shouldReturn400() throws Exception {
        String authToken = jwtTokenizer.getAuthToken("personal_data@email.com", List.of("ROLE_USER"));
        PersonalDataDto personalDataDto = createTestPersonalDataDto();
        personalDataDto.setBirthdate(LocalDate.of(2500, 1, 1));
        mockMvc.perform(put("/api/v1/user/personal-data")
                        .header(HttpHeaders.AUTHORIZATION, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(personalDataDto)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updatePersonalUserData_withInvalidActiveWeekdays_shouldReturn400() throws Exception {
        String authToken = jwtTokenizer.getAuthToken("personal_data@email.com", List.of("ROLE_USER"));
        PersonalDataDto personalDataDto = createTestPersonalDataDto();
        personalDataDto.setActiveWeekdays(null);
        mockMvc.perform(put("/api/v1/user/personal-data")
                        .header(HttpHeaders.AUTHORIZATION, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(personalDataDto)))
                .andExpect(status().isUnprocessableEntity());
    }


    // ==================== INJURY TEST  ====================

    // ==================== POST /api/v1/user/injuries ====================

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = "USER")
    void addInjuries_withValidSingleInjury_returnsCreated() throws Exception {
        ApplicationUser testUser = userRepository.getByEmail(DEFAULT_USER_EMAIL);
        injuryRepository.deleteAllInBatch();

        CreateInjuryStateDto injury = new CreateInjuryStateDto();
        injury.setInjuryIndex(0.5);
        injury.setAffectedArea(BodyPart.KNEE_REGION);
        injury.setLastHealthyDate(LocalDate.now().minusDays(5));

        List<CreateInjuryStateDto> injuries = List.of(injury);

        mockMvc.perform(post(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(injuries)))
                .andExpect(status().isCreated());

        // Verify injury was created
        List<Injuries> savedInjuries = injuryRepository.getAllByApplicationUser(testUser);
        assertEquals(1, savedInjuries.size());
        assertEquals(0.5, savedInjuries.get(0).getInjuryIndex());
        assertEquals(BodyPart.KNEE_REGION, savedInjuries.get(0).getAffectedArea());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = "USER")
    void addInjuries_withMultipleInjuries_returnsCreated() throws Exception {
        ApplicationUser testUser = userRepository.getByEmail(DEFAULT_USER_EMAIL);
        injuryRepository.deleteAllInBatch();

        CreateInjuryStateDto injury1 = new CreateInjuryStateDto();
        injury1.setInjuryIndex(0.3);
        injury1.setAffectedArea(BodyPart.KNEE_REGION);
        injury1.setLastHealthyDate(LocalDate.now().minusDays(3));

        CreateInjuryStateDto injury2 = new CreateInjuryStateDto();
        injury2.setInjuryIndex(0.6);
        injury2.setAffectedArea(BodyPart.UPPER_REGION);
        injury2.setLastHealthyDate(LocalDate.now().minusDays(7));

        CreateInjuryStateDto injury3 = new CreateInjuryStateDto();
        injury3.setInjuryIndex(0.2);
        injury3.setAffectedArea(BodyPart.CORE_REGION);
        injury3.setLastHealthyDate(LocalDate.now().minusDays(2));

        List<CreateInjuryStateDto> injuries = List.of(injury1, injury2, injury3);

        mockMvc.perform(post(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(injuries)))
                .andExpect(status().isCreated());

        // Verify all injuries were created
        List<Injuries> savedInjuries = injuryRepository.getAllByApplicationUser(testUser);
        assertEquals(3, savedInjuries.size());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = "USER")
    void addInjuries_withEmptyList_returnsCreated() throws Exception {
        ApplicationUser testUser = userRepository.getByEmail(DEFAULT_USER_EMAIL);
        injuryRepository.deleteAllInBatch();

        List<CreateInjuryStateDto> injuries = new ArrayList<>();

        mockMvc.perform(post(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(injuries)))
                .andExpect(status().isCreated());

        // Verify no injuries were created
        List<Injuries> savedInjuries = injuryRepository.getAllByApplicationUser(testUser);
        assertEquals(0, savedInjuries.size());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = "USER")
    void addInjuries_withCriticalBoneInjury_returnsCreated() throws Exception {
        ApplicationUser testUser = userRepository.getByEmail(DEFAULT_USER_EMAIL);
        injuryRepository.deleteAllInBatch();

        CreateInjuryStateDto injury = new CreateInjuryStateDto();
        injury.setInjuryIndex(0.8);
        injury.setAffectedArea(BodyPart.BONE_FRACTURE);
        injury.setLastHealthyDate(LocalDate.now().minusDays(1));

        List<CreateInjuryStateDto> injuries = List.of(injury);

        mockMvc.perform(post(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(injuries)))
                .andExpect(status().isCreated());

        List<Injuries> savedInjuries = injuryRepository.getAllByApplicationUser(testUser);
        assertEquals(1, savedInjuries.size());
        assertEquals(BodyPart.BONE_FRACTURE, savedInjuries.get(0).getAffectedArea());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = "USER")
    void addInjuries_withCriticalSpinalInjury_returnsCreated() throws Exception {
        ApplicationUser testUser = userRepository.getByEmail(DEFAULT_USER_EMAIL);
        injuryRepository.deleteAllInBatch();

        CreateInjuryStateDto injury = new CreateInjuryStateDto();
        injury.setInjuryIndex(0.8);
        injury.setAffectedArea(BodyPart.SPINAL_INJURY);
        injury.setLastHealthyDate(LocalDate.now().minusDays(1));

        List<CreateInjuryStateDto> injuries = List.of(injury);

        mockMvc.perform(post(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(injuries)))
                .andExpect(status().isCreated());

        List<Injuries> savedInjuries = injuryRepository.getAllByApplicationUser(testUser);
        assertEquals(1, savedInjuries.size());
        assertEquals(BodyPart.SPINAL_INJURY, savedInjuries.get(0).getAffectedArea());
    }

    @Test
    void addInjuries_withoutAuthentication_returnsUnauthorized() throws Exception {
        CreateInjuryStateDto injury = new CreateInjuryStateDto();
        injury.setInjuryIndex(0.5);
        injury.setAffectedArea(BodyPart.KNEE_REGION);

        List<CreateInjuryStateDto> injuries = List.of(injury);

        mockMvc.perform(post(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(injuries)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = "ADMIN")
    void addInjuries_withWrongRole_returnsForbidden() throws Exception {
        CreateInjuryStateDto injury = new CreateInjuryStateDto();
        injury.setInjuryIndex(0.5);
        injury.setAffectedArea(BodyPart.KNEE_REGION);

        List<CreateInjuryStateDto> injuries = List.of(injury);

        mockMvc.perform(post(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(injuries)))
                .andExpect(status().isForbidden());
    }

    // ==================== PUT /api/v1/user/injuries ====================

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = "USER")
    void updateInjuries_withValidSingleInjury_returnsOk() throws Exception {
        ApplicationUser testUser = userRepository.getByEmail(DEFAULT_USER_EMAIL);
        injuryRepository.deleteAllInBatch();
        // First create an injury
        Injuries existingInjury = new Injuries();
        existingInjury.setInjuryIndex(0.3);
        existingInjury.setAffectedArea(BodyPart.KNEE_REGION);
        existingInjury.setLastHealthyDate(LocalDate.now().minusDays(10));
        existingInjury.setLastInjuryDate(LocalDate.now().minusDays(5));
        existingInjury.setApplicationUser(testUser);
        existingInjury = injuryRepository.save(existingInjury);

        // Update the injury
        UpdateInjuryDto updateDto = new UpdateInjuryDto();
        updateDto.setInjuryId(existingInjury.getId());
        updateDto.setInjuryIndex(0.7);
        updateDto.setAffectedArea(BodyPart.UPPER_REGION);
        updateDto.setLastHealthyDate(LocalDate.now().minusDays(8));
        updateDto.setLastInjuryDate(LocalDate.now().minusDays(2));

        List<UpdateInjuryDto> updates = List.of(updateDto);

        mockMvc.perform(put(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates)))
                .andExpect(status().isOk());

        // Verify the update
        Injuries updatedInjury = injuryRepository.findById(existingInjury.getId()).orElse(null);
        assertNotNull(updatedInjury);
        assertEquals(0.7, updatedInjury.getInjuryIndex());
        assertEquals(BodyPart.UPPER_REGION, updatedInjury.getAffectedArea());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = "USER")
    void updateInjuries_withMultipleInjuries_returnsOk() throws Exception {
        ApplicationUser testUser = userRepository.getByEmail(DEFAULT_USER_EMAIL);
        injuryRepository.deleteAllInBatch();

        // Create multiple injuries
        Injuries injury1 = new Injuries();
        injury1.setInjuryIndex(0.3);
        injury1.setAffectedArea(BodyPart.KNEE_REGION);
        injury1.setLastHealthyDate(LocalDate.now().minusDays(10));
        injury1.setApplicationUser(testUser);
        injury1 = injuryRepository.save(injury1);

        Injuries injury2 = new Injuries();
        injury2.setInjuryIndex(0.5);
        injury2.setAffectedArea(BodyPart.UPPER_REGION);
        injury2.setLastHealthyDate(LocalDate.now().minusDays(7));
        injury2.setApplicationUser(testUser);
        injury2 = injuryRepository.save(injury2);

        // Update both
        UpdateInjuryDto update1 = new UpdateInjuryDto();
        update1.setInjuryId(injury1.getId());
        update1.setInjuryIndex(0.4);
        update1.setAffectedArea(BodyPart.KNEE_REGION);
        update1.setLastInjuryDate(LocalDate.now().minusDays(1));

        UpdateInjuryDto update2 = new UpdateInjuryDto();
        update2.setInjuryId(injury2.getId());
        update2.setInjuryIndex(0.6);
        update2.setAffectedArea(BodyPart.UPPER_REGION);
        update2.setLastInjuryDate(LocalDate.now().minusDays(2));

        List<UpdateInjuryDto> updates = List.of(update1, update2);

        mockMvc.perform(put(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates)))
                .andExpect(status().isOk());

        // Verify both updates
        Injuries updated1 = injuryRepository.findById(injury1.getId()).orElse(null);
        Injuries updated2 = injuryRepository.findById(injury2.getId()).orElse(null);

        assertNotNull(updated1);
        assertNotNull(updated2);
        assertEquals(0.4, updated1.getInjuryIndex());
        assertEquals(0.6, updated2.getInjuryIndex());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = "USER")
    void updateInjuries_withEmptyList_returnsOk() throws Exception {
        List<UpdateInjuryDto> updates = new ArrayList<>();

        mockMvc.perform(put(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = "USER")
    void updateInjuries_withNonExistentInjuryId_returnsOk() throws Exception {
        // Service returns null for non-existent injury, but endpoint still returns OK
        UpdateInjuryDto updateDto = new UpdateInjuryDto();
        updateDto.setInjuryId(999999L); // Non-existent ID
        updateDto.setInjuryIndex(0.5);
        updateDto.setAffectedArea(BodyPart.KNEE_REGION);

        List<UpdateInjuryDto> updates = List.of(updateDto);

        mockMvc.perform(put(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates)))
                .andExpect(status().isOk());
    }

    @Test
    void updateInjuries_withoutAuthentication_returnsUnauthorized() throws Exception {
        UpdateInjuryDto updateDto = new UpdateInjuryDto();
        updateDto.setInjuryId(1L);
        updateDto.setInjuryIndex(0.5);

        List<UpdateInjuryDto> updates = List.of(updateDto);

        mockMvc.perform(put(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = "ADMIN")
    void updateInjuries_withWrongRole_returnsForbidden() throws Exception {
        UpdateInjuryDto updateDto = new UpdateInjuryDto();
        updateDto.setInjuryId(1L);
        updateDto.setInjuryIndex(0.5);

        List<UpdateInjuryDto> updates = List.of(updateDto);

        mockMvc.perform(put(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates)))
                .andExpect(status().isForbidden());
    }

    // ==================== GET /api/v1/user/injuries ====================

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = "USER")
    void getInjuries_withNoInjuries_returnsEmptyList() throws Exception {
        injuryRepository.deleteAllInBatch();

        MvcResult result = mockMvc.perform(get(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0))
                .andReturn();
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = "USER")
    void getInjuries_withSingleInjury_returnsInjuryList() throws Exception {
        ApplicationUser testUser = userRepository.getByEmail(DEFAULT_USER_EMAIL);
        injuryRepository.deleteAllInBatch();
        // Create an injury
        Injuries injury = new Injuries();
        injury.setInjuryIndex(0.5);
        injury.setAffectedArea(BodyPart.KNEE_REGION);
        injury.setLastHealthyDate(LocalDate.now().minusDays(5));
        injury.setLastInjuryDate(LocalDate.now().minusDays(2));
        injury.setApplicationUser(testUser);
        injuryRepository.save(injury);

        mockMvc.perform(get(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].injuryIndex").value(0.5))
                .andExpect(jsonPath("$[0].affectedArea").value("KNEE_REGION"));
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = "USER")
    void getInjuries_withMultipleInjuries_returnsAllInjuries() throws Exception {
        ApplicationUser testUser = userRepository.getByEmail(DEFAULT_USER_EMAIL);
        injuryRepository.deleteAllInBatch();
        // Create multiple injuries
        Injuries injury1 = new Injuries();
        injury1.setInjuryIndex(0.3);
        injury1.setAffectedArea(BodyPart.KNEE_REGION);
        injury1.setLastHealthyDate(LocalDate.now().minusDays(10));
        injury1.setApplicationUser(testUser);
        injuryRepository.save(injury1);

        Injuries injury2 = new Injuries();
        injury2.setInjuryIndex(0.6);
        injury2.setAffectedArea(BodyPart.UPPER_REGION);
        injury2.setLastHealthyDate(LocalDate.now().minusDays(7));
        injury2.setApplicationUser(testUser);
        injuryRepository.save(injury2);

        Injuries injury3 = new Injuries();
        injury3.setInjuryIndex(0.2);
        injury3.setAffectedArea(BodyPart.CORE_REGION);
        injury3.setLastHealthyDate(LocalDate.now().minusDays(3));
        injury3.setApplicationUser(testUser);
        injuryRepository.save(injury3);

        mockMvc.perform(get(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = "USER")
    void getInjuries_withCriticalInjury_returnsInjury() throws Exception {
        ApplicationUser testUser = userRepository.getByEmail(DEFAULT_USER_EMAIL);
        injuryRepository.deleteAllInBatch();

        Injuries injury = new Injuries();
        injury.setInjuryIndex(0.9);
        injury.setAffectedArea(BodyPart.BONE_FRACTURE);
        injury.setLastHealthyDate(LocalDate.now().minusDays(1));
        injury.setLastInjuryDate(LocalDate.now());
        injury.setApplicationUser(testUser);
        injuryRepository.save(injury);

        mockMvc.perform(get(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].affectedArea").value("BONE_FRACTURE"));
    }


    @Test
    void getInjuries_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = "ADMIN")
    void getInjuries_withWrongRole_returnsForbidden() throws Exception {
        mockMvc.perform(get(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "other.user@example.com", roles = "USER")
    void getInjuries_differentUser_returnsOnlyTheirInjuries() throws Exception {
        ApplicationUser testUser = userRepository.getByEmail(DEFAULT_USER_EMAIL);
        injuryRepository.deleteAllInBatch();
        // Create injury for test user
        Injuries testUserInjury = new Injuries();
        testUserInjury.setInjuryIndex(0.5);
        testUserInjury.setAffectedArea(BodyPart.KNEE_REGION);
        testUserInjury.setApplicationUser(testUser);
        injuryRepository.save(testUserInjury);

        // Request as different user should return empty list
        mockMvc.perform(get(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ==================== Integration Tests ====================

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL, roles = "USER")
    void fullWorkflow_createUpdateAndGet_worksCorrectly() throws Exception {
        injuryRepository.deleteAllInBatch();

        // 1. Create an injury
        CreateInjuryStateDto createDto = new CreateInjuryStateDto();
        createDto.setInjuryIndex(0.3);
        createDto.setAffectedArea(BodyPart.KNEE_REGION);
        createDto.setLastHealthyDate(LocalDate.now().minusDays(5));

        mockMvc.perform(post(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(createDto))))
                .andExpect(status().isCreated());

        // 2. Get injuries and verify creation
        MvcResult getResult = mockMvc.perform(get(USER_INJURY_BASE_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();

        ViewInjuryDto[] injuries = objectMapper.readValue(
                getResult.getResponse().getContentAsString(),
                ViewInjuryDto[].class
        );
        Long injuryId = injuries[0].getInjuryId();

        // 3. Update the injury
        UpdateInjuryDto updateDto = new UpdateInjuryDto();
        updateDto.setInjuryId(injuryId);
        updateDto.setInjuryIndex(0.7);
        updateDto.setAffectedArea(BodyPart.UPPER_REGION);
        updateDto.setLastInjuryDate(LocalDate.now().minusDays(1));

        mockMvc.perform(put(USER_INJURY_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(updateDto))))
                .andExpect(status().isOk());

        // 4. Get injuries again and verify update
        mockMvc.perform(get(USER_INJURY_BASE_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].injuryIndex").value(0.7))
                .andExpect(jsonPath("$[0].affectedArea").value("UPPER_REGION"));
    }


// ==================== HELPER METHODS ====================

    /**
     * Helper method to create a test user in the database
     */
    private ApplicationUser createTestUser(String email, String password, boolean verified) {
        ApplicationUser user = new ApplicationUser();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setFirstname("Test");
        user.setLastname("User");
        user.setVerified(verified);

        return userRepository.save(user);
    }

    /**
     * Helper method to create a test personal data dto
     */
    private PersonalDataDto createTestPersonalDataDto() {
        return PersonalDataDto.builder()
                .sex(Sex.MALE)
                .height(175)
                .weight(new BigDecimal("78.5"))
                .birthdate(LocalDate.of(2003, 5, 24))
                .experienceLevel(ExperienceLevel.BEGINNER)
                .activeWeekdays(new HashSet<>(Set.of(Weekday.MONDAY, Weekday.TUESDAY, Weekday.WEDNESDAY)))
                .build();
    }
}