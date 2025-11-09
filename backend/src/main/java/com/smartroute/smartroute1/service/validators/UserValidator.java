package com.smartroute.smartroute1.service.validators;


import com.smartroute.smartroute1.endpoint.dto.CreateUserDto;
import com.smartroute.smartroute1.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

@Component
public class UserValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String VALIDATION_PATTERN_1 = "^(.+)@(\\S+)$";
    private static final String VALIDATION_PATTERN_2 = "^\\s+";



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

}