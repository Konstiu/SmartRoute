package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.CreateUserDto;
import com.smartroute.smartroute1.endpoint.dto.UserLoginDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.exception.ValidationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public interface UserService extends UserDetailsService {

    /**
     * Find a user in the context of Spring Security based on the email address
     * <br>
     * For more information have a look at this tutorial:
     * https://www.baeldung.com/spring-security-authentication-with-a-database
     *
     * @param email the email address
     * @return a Spring Security user
     * @throws UsernameNotFoundException is thrown if the specified user does not
     *                                   exists
     */
    @Override
    UserDetails loadUserByUsername(String email) throws UsernameNotFoundException;

    /**
     * Find an application user based on the email address.
     *
     * @param email the email address
     * @return a application user
     */
    ApplicationUser findApplicationUserByEmail(String email);

    /**
     * Log in a user.
     *
     * @param userLoginDto login credentials
     * @return the JWT, if successful
     * @throws org.springframework.security.authentication.BadCredentialsException if
     *                                                                             credentials
     *                                                                             are
     *                                                                             bad
     */
    String login(UserLoginDto userLoginDto);

    /**
     * Create a new user in the database.
     *
     * @param toCreate The user to create.
     * @param origin TODO - Maybe we don't need it.
     * @return The created user entry.
     * @throws ValidationException If any validations error occur. (no name, ...)
     */
    ApplicationUser create(CreateUserDto toCreate, String origin) throws ValidationException;
}
