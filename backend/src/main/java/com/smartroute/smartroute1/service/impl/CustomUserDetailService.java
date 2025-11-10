package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.CreateUserDto;
import com.smartroute.smartroute1.endpoint.dto.PasswordResetDto;
import com.smartroute.smartroute1.endpoint.dto.PersonalDataDto;
import com.smartroute.smartroute1.endpoint.dto.UserLoginDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.exception.NotFoundException;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.security.JwtTokenizer;
import com.smartroute.smartroute1.service.EmailSmtpService;
import com.smartroute.smartroute1.service.RateLimitCheck;
import com.smartroute.smartroute1.service.UserService;
import com.smartroute.smartroute1.service.validators.UserValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;
import java.util.List;

@Service
public class CustomUserDetailService implements UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenizer jwtTokenizer;
    private final UserValidator validator;
    private final EmailSmtpService emailService;
    private final RateLimitCheck rateLimitCheck;

    @Autowired
    public CustomUserDetailService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenizer jwtTokenizer, UserValidator validator, EmailSmtpService emailService, RateLimitCheck rateLimitCheck) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenizer = jwtTokenizer;
        this.validator = validator;
        this.emailService = emailService;
        this.rateLimitCheck = rateLimitCheck;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        LOGGER.debug("Load all user by email");
        try {
            ApplicationUser applicationUser = findApplicationUserByEmail(email);

            List<GrantedAuthority> grantedAuthorities;
            grantedAuthorities = AuthorityUtils.createAuthorityList("ROLE_USER");

            return new User(applicationUser.getEmail(), applicationUser.getPassword(), grantedAuthorities);
        } catch (NotFoundException e) {
            throw new UsernameNotFoundException(e.getMessage(), e);
        }
    }

    @Override
    public ApplicationUser findApplicationUserByEmail(String email) {
        LOGGER.debug("Find application user by email");
        ApplicationUser applicationUser = userRepository.findUserByEmail(email);
        if (applicationUser != null) {
            return applicationUser;
        }
        throw new NotFoundException(String.format("Could not find the user with the email address %s", email));
    }

    @Override
    public String login(UserLoginDto userLoginDto) {
        UserDetails userDetails = loadUserByUsername(userLoginDto.getEmail());
        if (userDetails != null
                && userDetails.isAccountNonExpired()
                && userDetails.isAccountNonLocked()
                && userDetails.isCredentialsNonExpired()
                && passwordEncoder.matches(userLoginDto.getPassword(), userDetails.getPassword())) {
            List<String> roles = userDetails.getAuthorities()
                    .stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();
            return jwtTokenizer.getAuthToken(userDetails.getUsername(), roles);
        }
        throw new BadCredentialsException("Username or password is incorrect or account is locked");
    }

    @Override
    public ApplicationUser create(CreateUserDto toCreate, String origin) throws ValidationException {

        LOGGER.trace("Create user by CreateUserDto: {}", toCreate);
        validator.validateForCreate(toCreate);
        if (userRepository.findUserByEmail(toCreate.email) != null) {
            throw new ValidationException("Email already exits please try an other one");
        }

        CreateUserDto userDto = new CreateUserDto();
        userDto.setEmail(toCreate.email);
        userDto.setFirstname(toCreate.firstname);
        userDto.setLastname(toCreate.lastname);
        emailService.sendVerificationEmail(userDto, origin);

        String encodedPassword = passwordEncoder.encode(toCreate.password);
        final ApplicationUser applicationUser = new ApplicationUser(
                toCreate.email,
                encodedPassword,
                toCreate.firstname.trim().replaceAll("\\s+", " "),
                toCreate.lastname.trim().replaceAll("\\s+", " ")
        );

        return userRepository.save(applicationUser);
    }

    @Override
    public ApplicationUser updatePersonalData(PersonalDataDto personalData, String userEmail) throws ValidationException {
        LOGGER.trace("Update user by PersonalDataDto: {}, {}", personalData, userEmail);
        validator.validatePersonalData(personalData);
        ApplicationUser user = userRepository.findUserByEmail(userEmail);
        if (user == null) {
            throw new NotFoundException(String.format("Could not find the user with the email %s", userEmail));
        }

        user.setSex(personalData.getSex());
        user.setHeight(personalData.getHeight());
        user.setWeight(personalData.getWeight());
        user.setBirthdate(personalData.getBirthdate());
        user.setExperienceLevel(personalData.getExperienceLevel());
        user.setActiveWeekdays(personalData.getActiveWeekdays());

        return userRepository.save(user);
    }

    @Override
    public boolean verifyEmail(String token) {
        LOGGER.trace("Verify Email with token:{}", token);
        String tokenEmail = jwtTokenizer.extractUsernameFromVerificationToken(token);
        try {
            UserDetails userDetails = loadUserByUsername(tokenEmail);
            if (userDetails != null
                    && userDetails.isAccountNonExpired()
                    && userDetails.isAccountNonLocked()
                    && userDetails.isCredentialsNonExpired()
            ) {
                ApplicationUser applicationUser = findApplicationUserByEmail(tokenEmail);
                applicationUser.setVerified(true);
                userRepository.save(applicationUser);
            } else {
                return false;
            }
        } catch (UsernameNotFoundException | NotFoundException e) {
            return false;
        }
        return true;
    }

    @Override
    public void resendVerificationEmail(String email, String origin) {
        LOGGER.trace("Resend verification email :{}", email);
        try {
            UserDetails userDetails = loadUserByUsername(email);
            if (userDetails != null
                    && userDetails.isAccountNonExpired()
                    && userDetails.isAccountNonLocked()
                    && userDetails.isCredentialsNonExpired()
            ) {
                ApplicationUser applicationUser = findApplicationUserByEmail(email);
                if (!applicationUser.isVerified()) {
                    CreateUserDto userDto = new CreateUserDto();
                    userDto.setEmail(email);
                    userDto.setFirstname(applicationUser.getFirstname());
                    userDto.setLastname(applicationUser.getLastname());
                    rateLimitCheck.check(email, "verification");
                    emailService.sendVerificationEmail(userDto, origin);
                }
            }
        } catch (UsernameNotFoundException | NotFoundException ignored) {
            LOGGER.warn(String.format("Could not resend verification email %s", email));
        }
    }

    @Override
    public void requestPasswordReset(String email, String origin) {
        LOGGER.trace("Send Password Reset Email to :{}", email);
        try {
            UserDetails userDetails = loadUserByUsername(email);
            if (userDetails != null
                    && userDetails.isAccountNonExpired()
                    && userDetails.isAccountNonLocked()
                    && userDetails.isCredentialsNonExpired()
            ) {
                ApplicationUser applicationUser = findApplicationUserByEmail(email);
                rateLimitCheck.check(email, "password");
                emailService.sendPasswordResetEmail(applicationUser, origin);
            }
        } catch (UsernameNotFoundException | NotFoundException ignored) {
            LOGGER.warn("Password Reset Email Request with non-existent User: {}", email);
        }
    }

    @Override
    public boolean changePasswordWithToken(String token, PasswordResetDto resetDto) throws ValidationException {
        LOGGER.trace("Change Password using token :{}", token);
        String tokenEmail = jwtTokenizer.extractUsernameFromVerificationToken(token);
        try {
            UserDetails userDetails = loadUserByUsername(tokenEmail);
            if (userDetails != null
                    && userDetails.isAccountNonExpired()
                    && userDetails.isAccountNonLocked()
                    && userDetails.isCredentialsNonExpired()
            ) {
                ApplicationUser applicationUser = findApplicationUserByEmail(tokenEmail);
                validator.validatePasswordChange(resetDto, applicationUser.getPassword());
                String encodedPassword = passwordEncoder.encode(resetDto.password);
                applicationUser.setPassword(encodedPassword);
                userRepository.save(applicationUser);
                return true;
            }
        } catch (UsernameNotFoundException | NotFoundException ignored) {
            LOGGER.warn("Password Reset submitted with invalid token: {}", token);
        }
        return false;
    }


}
