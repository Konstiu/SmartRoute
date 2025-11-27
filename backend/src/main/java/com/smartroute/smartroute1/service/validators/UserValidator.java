package com.smartroute.smartroute1.service.validators;


import com.smartroute.smartroute1.endpoint.dto.CreateUserDto;
import com.smartroute.smartroute1.endpoint.dto.PasswordResetDto;
import com.smartroute.smartroute1.endpoint.dto.PersonalDataDto;
import com.smartroute.smartroute1.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class UserValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String VALIDATION_PATTERN_1 = "^(.+)@(\\S+)$";
    private static final String VALIDATION_PATTERN_2 = "^\\s+";

    private final PasswordEncoder passwordEncoder;

    public UserValidator(PasswordEncoder passwordEncoder) {
        this.passwordEncoder =  passwordEncoder;
    }


    public boolean validateEmail(String email) {
        LOGGER.trace("Validation of user email to pattern: {}", email);
        return !email.matches(VALIDATION_PATTERN_1);
    }

    public void validateForCreate(CreateUserDto toCreate) throws ValidationException {
        LOGGER.trace("validateForCreate({})", toCreate);

        List<String> errors = new ArrayList<>();
        if (toCreate.email == null) {
            errors.add("Email cannot be null");
            throw new ValidationException("Errors while verifying user Data:", errors);
        }
        if (toCreate.email.length() > 255) {
            errors.add("Email is too long");
        }
        if (validateEmail(toCreate.email)) {
            errors.add("Email is not a valid Email Address");
        }
        if (toCreate.firstname.isEmpty()) {
            errors.add("Name cannot be null");
            throw new ValidationException("Errors while verifying user Data:", errors);
        }
        if (toCreate.firstname.matches(VALIDATION_PATTERN_2)) {
            errors.add("Name cannot be whitespace");
        }
        if (toCreate.lastname.isEmpty()) {
            errors.add("Name cannot be null");
            throw new ValidationException("Errors while verifying user Data:", errors);
        }
        if (toCreate.lastname.matches(VALIDATION_PATTERN_2)) {
            errors.add("Name cannot be whitespace");
        }
        if (toCreate.password.length() < 8) {
            errors.add("Password has to be at least of length 8");
        }
        if (toCreate.firstname.length() > 255) {
            errors.add("First name has to be smaller than 255 characters");
        }
        if (toCreate.lastname.length() > 255) {
            errors.add("Last name has to be smaller than 255 characters");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Errors while verifying user Data:", errors);
        }
    }

    public void validatePasswordChange(PasswordResetDto resetDto, String oldEncodedPassword) throws ValidationException {
        List<String> errors = new ArrayList<>();
        if (!resetDto.password.equals(resetDto.repeatPassword)) {
            errors.add("Passwords must match");
        }

        if (resetDto.password.length() < 8) {
            errors.add("Password has to be at least of length 8");
        }
        if (passwordEncoder.matches(resetDto.getPassword(), oldEncodedPassword)) {
            errors.add("New password can not be equal to old password");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Errors while verifying password change:", errors);
        }
    }

    public void validatePersonalData(PersonalDataDto personalDataDto) throws ValidationException {
        List<String> errors = new ArrayList<>();
        if (personalDataDto.getHeight() != null && (personalDataDto.getHeight() <= 0 || personalDataDto.getHeight() > 300)) {
            errors.add("Height must be between 1 and 300");
        }
        if (personalDataDto.getWeight() != null && (
            personalDataDto.getWeight().compareTo(new BigDecimal("0.1")) < 0
            || personalDataDto.getWeight().compareTo(new BigDecimal("500")) > 0)
        ) {
            errors.add("Weight must be between 0.1 and 500");
        }
        if (personalDataDto.getBirthdate() != null && personalDataDto.getBirthdate().isAfter(LocalDate.now())) {
            errors.add("Birthdate must be in the past");
        }
        if (personalDataDto.getActiveWeekdays() == null) {
            errors.add("Active weekdays cannot be null");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Errors while verifying personal data:", errors);
        }
    }
}