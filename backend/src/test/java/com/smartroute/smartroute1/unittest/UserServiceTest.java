package com.smartroute.smartroute1.unittest;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.smartroute.smartroute1.endpoint.dto.CreateUserDto;
import com.smartroute.smartroute1.endpoint.dto.PasswordResetDto;
import com.smartroute.smartroute1.endpoint.dto.PersonalDataDto;
import com.smartroute.smartroute1.endpoint.mapper.UserMapper;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.entity.enums.Weekday;
import com.smartroute.smartroute1.exception.NotFoundException;
import com.smartroute.smartroute1.exception.RateLimitExceededException;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.security.JwtTokenizer;
import com.smartroute.smartroute1.service.UserService;
import jakarta.mail.internet.MimeMessage;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.smartroute.smartroute1.basetest.TestData.ORIGIN;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest()
@ActiveProfiles({"test", "generateData"})
@Transactional
class UserServiceTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("test", "test"))
            .withPerMethodLifecycle(true); // Reset between tests

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenizer jwtTokenizer;


    // ==================== USER CREATION TESTS ====================

    @Test
    void createNewValidUser_shouldCreateUserAndSendVerificationEmail() throws Exception {
        ApplicationUser user = new ApplicationUser("test@email.com", "Password123!", "John", "Doe");
        CreateUserDto applicationUserDto = userMapper.applicationUserToDto(user);

        ApplicationUser createdApplicationUser = userService.create(applicationUserDto, ORIGIN);

        assertAll(
                () -> assertNotNull(createdApplicationUser),
                () -> assertEquals(applicationUserDto.getEmail(), createdApplicationUser.getEmail()),
                () -> assertEquals(applicationUserDto.getFirstname(), createdApplicationUser.getFirstname()),
                () -> assertEquals(applicationUserDto.getLastname(), createdApplicationUser.getLastname()),
                () -> assertTrue(passwordEncoder.matches(applicationUserDto.getPassword(), createdApplicationUser.getPassword())),
                () -> assertFalse(createdApplicationUser.isVerified(), "New user should not be verified")
        );

        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertEquals(1, messages.length, "One verification email should be sent");
        assertEquals(applicationUserDto.getEmail(), messages[0].getAllRecipients()[0].toString());
    }

    @Test
    void createUser_withExistingEmail_shouldThrowValidationException() throws Exception {
        ApplicationUser user = new ApplicationUser("test@email.com", "Password123!", "John", "Doe");
        CreateUserDto firstUser = userMapper.applicationUserToDto(user);
        userService.create(firstUser, ORIGIN);

        CreateUserDto duplicateUser = new CreateUserDto();
        duplicateUser.setEmail("test@email.com");
        duplicateUser.setPassword("AnotherPassword123!");
        duplicateUser.setFirstname("Jane");
        duplicateUser.setLastname("Smith");

        ValidationException exception = assertThrows(ValidationException.class,
                () -> userService.create(duplicateUser, ORIGIN));
        assertTrue(exception.getMessage().contains("Email already exists"));
    }

    @Test
    void createUser_withExistingCaseInsensitiveEmail_shouldThrowValidationException() throws Exception {
        ApplicationUser user = new ApplicationUser("test@email.com", "Password123!", "John", "Doe");
        CreateUserDto firstUser = userMapper.applicationUserToDto(user);
        userService.create(firstUser, ORIGIN);

        CreateUserDto duplicateUser = new CreateUserDto();
        duplicateUser.setEmail("TEST@EMAIL.com");
        duplicateUser.setPassword("AnotherPassword123!");
        duplicateUser.setFirstname("Jane");
        duplicateUser.setLastname("Smith");

        ValidationException exception = assertThrows(ValidationException.class,
            () -> userService.create(duplicateUser, ORIGIN));
        assertTrue(exception.getMessage().contains("Email already exists"));
    }

    @Test
    void createUser_withInvalidData_shouldThrowValidationException() {
        CreateUserDto invalidUser = new CreateUserDto();
        invalidUser.setEmail("");
        invalidUser.setPassword("weak");
        invalidUser.setFirstname("");
        invalidUser.setLastname("");

        assertThrows(ValidationException.class, () -> userService.create(invalidUser, ORIGIN));
    }

    @Test
    void createUser_trimsWhitespaceInNames() throws Exception {
        CreateUserDto userDto = new CreateUserDto();
        userDto.setEmail("test@email.com");
        userDto.setPassword("Password123!");
        userDto.setFirstname("  John   Middle  ");
        userDto.setLastname("  Doe  ");

        ApplicationUser created = userService.create(userDto, ORIGIN);

        assertEquals("John Middle", created.getFirstname());
        assertEquals("Doe", created.getLastname());
    }


    // ==================== EMAIL VERIFICATION TESTS ====================

    @Test
    void verifyEmail_withValidToken_shouldVerifyUser() throws Exception {
        ApplicationUser user = createUnverifiedUser("verify@email.com", "Password123!");
        String token = jwtTokenizer.buildVerificationToken(user.getEmail());

        boolean result = userService.verifyEmail(token);

        assertTrue(result);
        ApplicationUser verifiedUser = userRepository.findUserByEmail("verify@email.com");
        assertTrue(verifiedUser.isVerified());
    }

    @Test
    void verifyEmail_withInvalidToken_shouldReturnFalse() {
        String token = jwtTokenizer.buildVerificationToken("nonexistent@email.com");

        boolean result = userService.verifyEmail(token);

        assertFalse(result);
    }

    @Test
    void verifyEmail_withAlreadyVerifiedUser_shouldStillSucceed() throws Exception {
        ApplicationUser user = createAndVerifyUser("already@email.com", "Password123!");
        String token = jwtTokenizer.buildVerificationToken(user.getEmail());

        boolean result = userService.verifyEmail(token);

        assertTrue(result);
    }

    // ==================== RESEND VERIFICATION EMAIL TESTS ====================

    @Test
    void resendVerificationEmail_forUnverifiedUser_shouldSendEmail() throws Exception {
        createUnverifiedUser("resend@email.com", "Password123!");
        greenMail.purgeEmailFromAllMailboxes(); // Clear creation email

        userService.resendVerificationEmail("resend@email.com", ORIGIN);

        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertEquals(1, messages.length, "Verification email should be resent");
        assertEquals("resend@email.com", messages[0].getAllRecipients()[0].toString());
    }

    @Test
    void resendVerificationEmail_forVerifiedUser_shouldNotSendEmail() throws Exception {
        createAndVerifyUser("verified@email.com", "Password123!");
        greenMail.purgeEmailFromAllMailboxes(); // Clear previous emails

        userService.resendVerificationEmail("verified@email.com", ORIGIN);

        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertEquals(0, messages.length, "No email should be sent for verified user");
    }

    @Test
    void resendVerificationEmail_forNonExistentUser_shouldNotThrowException() {
        assertDoesNotThrow(() -> userService.resendVerificationEmail("nonexistent@email.com", ORIGIN));

        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertEquals(0, messages.length);
    }

    // ==================== PASSWORD RESET TESTS ====================

    @Test
    void requestPasswordReset_forExistingUser_shouldSendEmail() throws Exception {
        createAndVerifyUser("reset@email.com", "Password123!");
        greenMail.purgeEmailFromAllMailboxes();

        userService.requestPasswordReset("reset@email.com", ORIGIN);

        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertEquals(1, messages.length, "Password reset email should be sent");
        assertEquals("reset@email.com", messages[0].getAllRecipients()[0].toString());
    }

    @Test
    void requestPasswordReset_forNonExistentUser_shouldNotThrowException() {
        assertDoesNotThrow(() -> userService.requestPasswordReset("nonexistent@email.com", ORIGIN));

        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertEquals(0, messages.length);
    }

    @Test
    void changePasswordWithToken_withValidToken_shouldChangePassword() throws Exception {
        ApplicationUser user = createAndVerifyUser("change@email.com", "OldPassword123!");
        String token = jwtTokenizer.buildVerificationToken(user.getEmail());

        PasswordResetDto resetDto = new PasswordResetDto();
        resetDto.password = "NewPassword123!";
        resetDto.repeatPassword = "NewPassword123!";

        boolean result = userService.changePasswordWithToken(token, resetDto);

        assertTrue(result);
        ApplicationUser updatedUser = userRepository.findUserByEmail("change@email.com");
        assertTrue(passwordEncoder.matches("NewPassword123!", updatedUser.getPassword()));
        assertFalse(passwordEncoder.matches("OldPassword123!", updatedUser.getPassword()));
    }

    @Test
    void changePasswordWithToken_withInvalidToken_shouldReturnFalse() {
        String token = jwtTokenizer.buildVerificationToken("nonexistent@email.com");

        PasswordResetDto resetDto = new PasswordResetDto();
        resetDto.password = "NewPassword123!";
        resetDto.repeatPassword = "NewPassword123!";


        AtomicBoolean result = new AtomicBoolean(false);
        assertDoesNotThrow(
                () -> result.set(userService.changePasswordWithToken(token, resetDto)));
        assertFalse(result.get());
    }

    @Test
    void changePasswordWithToken_withMismatchedPasswords_shouldThrowValidationException() throws Exception {
        ApplicationUser user = createAndVerifyUser("mismatch@email.com", "OldPassword123!");
        String token = jwtTokenizer.buildVerificationToken(user.getEmail());

        PasswordResetDto resetDto = new PasswordResetDto();
        resetDto.password = "NewPassword123!";
        resetDto.repeatPassword = "DifferentPassword123!";

        assertThrows(ValidationException.class,
                () -> userService.changePasswordWithToken(token, resetDto));
    }

    @Test
    void changePasswordWithToken_withWeakPassword_shouldThrowValidationException() throws Exception {
        ApplicationUser user = createAndVerifyUser("weak@email.com", "OldPassword123!");
        String token = jwtTokenizer.buildVerificationToken(user.getEmail());

        PasswordResetDto resetDto = new PasswordResetDto();
        resetDto.password = "weak";
        resetDto.repeatPassword = "weak";

        // When & Then
        assertThrows(ValidationException.class,
                () -> userService.changePasswordWithToken(token, resetDto));
    }

    // ==================== FIND USER TESTS ====================

    @Test
    void findApplicationUserByEmail_withExistingUser_shouldReturnUser() throws Exception {
        ApplicationUser created = createUnverifiedUser("find@email.com", "Password123!");

        ApplicationUser found = userService.findApplicationUserByEmail("find@email.com");

        assertNotNull(found);
        assertEquals(created.getEmail(), found.getEmail());
        assertEquals(created.getFirstname(), found.getFirstname());
        assertEquals(created.getLastname(), found.getLastname());
    }

    @Test
    void findApplicationUserByEmail_withNonExistentUser_shouldThrowNotFoundException() {
        assertThrows(NotFoundException.class,
                () -> userService.findApplicationUserByEmail("nonexistent@email.com"));
    }

    @Test
    void findApplicationUserByEmailWithWeekdays_withExistingUser_shouldReturnUserWithWeekdays() throws Exception {
        ApplicationUser created = createAndVerifyUser("weekdays_user@email.com", "Password123!");
        PersonalDataDto personalDataDto = createTestPersonalDataDto();
        // update user to set weekdays
        userService.updatePersonalData(personalDataDto, created.getEmail());

        ApplicationUser found = userService.findApplicationUserByEmailWithWeekdays(created.getEmail());

        assertNotNull(found);
        assertAll(
            () -> assertEquals(created.getEmail(), found.getEmail()),
            () -> assertEquals(personalDataDto.getActiveWeekdays(), found.getActiveWeekdays())
        );
    }

    @Test
    void findApplicationUserByEmailWithWeekdays_withNonExistentUser_shouldThrowNotFoundException() {
        assertThrows(NotFoundException.class,
                () -> userService.findApplicationUserByEmailWithWeekdays("nonexistent_weekdays@email.com"));
    }

    // ==================== RATE LIMIT TEST ====================

    @Test
    void check_whenRequestsExceedLimit_shouldThrowRateLimitExceededException() throws Exception {
        createAndVerifyUser("reset_ratelimit@email.com", "Password123!");
        greenMail.purgeEmailFromAllMailboxes();

        for (int i = 0; i < 5; i++) {
            userService.requestPasswordReset("reset_ratelimit@email.com", ORIGIN);

            MimeMessage[] messages = greenMail.getReceivedMessages();
            assertEquals(i+1, messages.length, "Password reset email should be sent");
            assertEquals("reset_ratelimit@email.com", messages[i].getAllRecipients()[0].toString());
        }
        assertThrows(RateLimitExceededException.class,
                () -> userService.requestPasswordReset("reset_ratelimit@email.com", ORIGIN));

    }

    // ==================== PERSONAL USER DATA METHODS ====================
    @Test
    void updatePersonalData_withValidData_shouldUpdateUser() throws Exception {
        ApplicationUser user = createAndVerifyUser("personal_data@email.com", "Password123!");
        PersonalDataDto personalDataDto = createTestPersonalDataDto();
        ApplicationUser updatedUser = userService.updatePersonalData(personalDataDto, user.getEmail());

        assertAll(
                () -> assertEquals(personalDataDto.getSex(), updatedUser.getSex()),
                () -> assertEquals(personalDataDto.getHeight(), updatedUser.getHeight()),
                () -> assertEquals(personalDataDto.getWeight(), updatedUser.getWeight()),
                () -> assertEquals(personalDataDto.getBirthdate(), updatedUser.getBirthdate()),
                () -> assertEquals(personalDataDto.getExperienceLevel(), updatedUser.getExperienceLevel()),
                () -> assertEquals(personalDataDto.getActiveWeekdays(), updatedUser.getActiveWeekdays())
        );
    }

    @Test
    void updatePersonalData_withInvalidData_shouldThrowValidationException() throws Exception {
        ApplicationUser user = createAndVerifyUser("personal_data@email.com", "Password123!");
        PersonalDataDto personalDataDto = createTestPersonalDataDto();
        personalDataDto.setHeight(0);
        assertThrows(ValidationException.class, () -> {
            userService.updatePersonalData(personalDataDto, user.getEmail());
        });
    }

    @Test
    void updatePersonalData_withInvalidWeight_shouldThrowValidationException() throws Exception {
        ApplicationUser user = createAndVerifyUser("personal_data_weight@email.com", "Password123!");
        PersonalDataDto personalDataDto = createTestPersonalDataDto();
        personalDataDto.setWeight(new BigDecimal("0"));
        assertThrows(ValidationException.class, () -> {
            userService.updatePersonalData(personalDataDto, user.getEmail());
        });
    }

    @Test
    void updatePersonalData_withInvalidBirthdate_shouldThrowValidationException() throws Exception {
        ApplicationUser user = createAndVerifyUser("personal_data_birthdate@email.com", "Password123!");
        PersonalDataDto personalDataDto = createTestPersonalDataDto();
        personalDataDto.setBirthdate(LocalDate.now().plusDays(1));
        assertThrows(ValidationException.class, () -> {
            userService.updatePersonalData(personalDataDto, user.getEmail());
        });
    }

    @Test
    void updatePersonalData_withUnrealisticBirthdate_shouldThrowValidationException() throws Exception {
        ApplicationUser user = createAndVerifyUser("personal_data_unrealistic_birthdate@email.com", "Password123!");
        PersonalDataDto personalDataDto = createTestPersonalDataDto();
        personalDataDto.setBirthdate(LocalDate.of(1800, 1, 1));
        assertThrows(ValidationException.class, () -> {
            userService.updatePersonalData(personalDataDto, user.getEmail());
        });
    }

    @Test
    void updatePersonalData_withInvalidActiveWeekdays_shouldThrowValidationException() throws Exception {
        ApplicationUser user = createAndVerifyUser("personal_data_weekdays@email.com", "Password123!");
        PersonalDataDto personalDataDto = createTestPersonalDataDto();
        personalDataDto.setActiveWeekdays(null);
        assertThrows(ValidationException.class, () -> {
            userService.updatePersonalData(personalDataDto, user.getEmail());
        });
    }






    // ==================== HELPER METHODS ====================

    private ApplicationUser createUnverifiedUser(String email, String password) throws Exception {
        CreateUserDto userDto = new CreateUserDto();
        userDto.setEmail(email);
        userDto.setPassword(password);
        userDto.setFirstname("Test");
        userDto.setLastname("User");

        return userService.create(userDto, ORIGIN);
    }

    private ApplicationUser createAndVerifyUser(String email, String password) throws Exception {
        ApplicationUser user = createUnverifiedUser(email, password);
        user.setVerified(true);
        return userRepository.save(user);
    }

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

