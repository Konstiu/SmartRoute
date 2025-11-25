package com.smartroute.smartroute1.integrationtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.endpoint.dto.CreateUserDto;
import com.smartroute.smartroute1.endpoint.dto.EmailDto;
import com.smartroute.smartroute1.endpoint.dto.PasswordResetDto;
import com.smartroute.smartroute1.endpoint.dto.PersonalDataDto;
import com.smartroute.smartroute1.endpoint.dto.UserDetailDto;
import com.smartroute.smartroute1.endpoint.mapper.UserMapper;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.entity.enums.Weekday;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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