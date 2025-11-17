package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.CreateUserDto;
import com.smartroute.smartroute1.endpoint.dto.EmailDto;
import com.smartroute.smartroute1.endpoint.dto.PasswordResetDto;
import com.smartroute.smartroute1.endpoint.dto.PersonalDataDto;
import com.smartroute.smartroute1.endpoint.dto.UserDetailDto;
import com.smartroute.smartroute1.endpoint.mapper.UserMapper;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.invoke.MethodHandles;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/v1/user")
@Tag(name = "User Endpoint")
public class UserEndpoint {
    private final UserService userService;
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final UserMapper mapper;


    public UserEndpoint(UserService userService, UserMapper mapper) {
        this.userService = userService;
        this.mapper = mapper;
    }

    @Operation(
            description = "Create a new user with the data in the DTO",
            summary = "Creates a new user")
    @PermitAll
    @PostMapping()
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUserDto create(@RequestBody CreateUserDto toCreate, HttpServletRequest request) throws ValidationException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        LOGGER.info("POST /api/v1/user/ body: {}, origin {}", toCreate, origin);
        ApplicationUser user = userService.create(toCreate, origin);
        return mapper.applicationUserToDto(user);
    }

    @Operation(
        description = "Updates the personal data of a user with the data in the DTO",
        summary = "Updates personal user data")
    @Secured("ROLE_USER")
    @PutMapping(value = "/personal-data")
    @ResponseStatus(HttpStatus.OK)
    public UserDetailDto updatePersonalData(@RequestBody PersonalDataDto toUpdate) throws ValidationException {
        LOGGER.info("POST /api/v1/user/personal-data body: {}", toUpdate);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ApplicationUser updatedUser = userService.updatePersonalData(toUpdate, authentication.getName());
        return mapper.applicationUserToDetailDto(updatedUser);
    }

    @Operation(
            description = "Resends a verification email to the email in the token.",
            summary = "Resend verification email")
    @PermitAll
    @PostMapping(value = "/verify/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Object> resendVerificationEmail(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        LOGGER.info("POST /api/v1/user/ body: {}, origin {}", payload, origin);
        userService.resendVerificationEmail(payload.get("email"), origin);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @Operation(
            description = "Sends the verification email to the email in the token.",
            summary = "Sends verification email")
    @GetMapping(value = "/verify/{token}")
    @PermitAll
    public ResponseEntity<Object> verifyEmail(@PathVariable("token") String token) {
        LOGGER.info("PUT /api/v1/user/verify/{}", token);
        if (userService.verifyEmail(token)) {
            return ResponseEntity.status(HttpStatus.OK).build();
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @Operation(
            description = "Request to reset the password. An email is being send to the email in the token.",
            summary = "Request to reset password")
    @PostMapping(value = "/reset_password")
    @PermitAll
    public ResponseEntity<Object> requestPasswordReset(@RequestBody EmailDto emailDto, HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        LOGGER.info("GET api/v1/authentication/reset_password with email: {}, origin {}", emailDto.email, origin);
        userService.requestPasswordReset(emailDto.email, origin);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @Operation(
            description = "Changes the password with a token sent in an email.",
            summary = "Reset password")
    @PostMapping(value = "/reset_password/{token}")
    @PermitAll
    public ResponseEntity<Object> changePasswordWithToken(@PathVariable("token") String token, @RequestBody PasswordResetDto resetDto) throws ValidationException {
        LOGGER.info("PUT /api/v1/user/verify/{}", token);
        if (userService.changePasswordWithToken(token, resetDto)) {
            return ResponseEntity.status(HttpStatus.OK).build();
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

}
