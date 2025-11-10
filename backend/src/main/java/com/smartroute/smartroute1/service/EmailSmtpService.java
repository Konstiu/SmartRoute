package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.CreateUserDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import org.springframework.stereotype.Service;

@Service
public interface EmailSmtpService {

    /**
     * Send a verification email to provided application user.
     *
     */
    void sendVerificationEmail(CreateUserDto userDto, String origin);


    /**
     * Send a password reset email to provided email.
     *
     * @param userDto the user to whom the email is being sent
     * @param origin specific origin depending on where the service is deployed (i.e., localhost:8080/ …)
     */
    void sendPasswordResetEmail(ApplicationUser userDto, String origin);

}
