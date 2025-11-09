package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.CreateUserDto;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.invoke.MethodHandles;

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
}
