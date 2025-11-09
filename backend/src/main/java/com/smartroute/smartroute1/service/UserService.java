package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.CreateUserDto;
import com.smartroute.smartroute1.endpoint.dto.PasswordResetDto;
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
     * For more information, have a look at this tutorial:
     * https://www.baeldung.com/spring-security-authentication-with-a-database
     *
     * @param email the email address
     * @return a Spring Security user
     * @throws UsernameNotFoundException is thrown if the specified user does not exist
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
     * @throws org.springframework.security.authentication.BadCredentialsException if credentials are bad
     */
    String login(UserLoginDto userLoginDto);

    /**
     * Create a new user in the database.
     *
     * @param toCreate The user to create.
     * @param origin   specific origin depending on where the service is deployed (i.e., localhost:8080/ …)
     * @return The created user entry.
     * @throws ValidationException If any validation error occurs. (no name, ...)
     */
    ApplicationUser create(CreateUserDto toCreate, String origin) throws ValidationException;


    /**
     * sets the user verification status to verify.
     *
     * @param token The token that was generated for verify an email
     * @return true if user was verified this way or false if the token was invalid (e.g. expired, wrong format)
     */
    boolean verifyEmail(String token);

    /**
     * sends a new verification email if a user with this email exists and is not verified yet.
     *
     * @param email  the email of to which the verification is being sent.
     * @param origin specific origin depending on where the service is deployed (i.e., localhost:8080/ …)
     */
    void resendVerificationEmail(String email, String origin);


    /**
     * sends a password reset email to user if email exists.
     *
     * @param email the email of the user
     */
    void requestPasswordReset(String email, String origin);

    /**
     * changes token user password to new password if token is valid.
     *
     * @param token           the encoded email of the user
     * @param resetDto     the password to change to and repeat password avoid user mistakes
     *
     * @return true if password was changed this way or false if the token or password was invalid (e.g. expired, wrong format)
     */
    boolean changePasswordWithToken(String token, PasswordResetDto resetDto) throws ValidationException;



}
