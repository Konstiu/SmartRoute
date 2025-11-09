package com.smartroute.smartroute1.service.impl;


import com.smartroute.smartroute1.endpoint.dto.CreateUserDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.security.JwtTokenizer;
import com.smartroute.smartroute1.service.EmailSmtpService;
import com.smartroute.smartroute1.service.ThymeleafService;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class EmailSmtpServiceImpl implements EmailSmtpService {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final JavaMailSender mailSender;

    private final ThymeleafService thymeleafService;

    private final JwtTokenizer jwtTokenizer;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Autowired
    public EmailSmtpServiceImpl(JwtTokenizer jwtTokenizer, JavaMailSender mailSender, ThymeleafService thymeleafService) {
        this.mailSender = mailSender;
        this.thymeleafService = thymeleafService;
        this.jwtTokenizer = jwtTokenizer;
    }

    @Override
    public void sendVerificationEmail(CreateUserDto userDto, String origin) {
        LOG.trace("Send verification email {}", userDto);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            helper.setTo(userDto.getEmail());
            helper.setSubject("SmartRoute - Verify your email");

            Map<String, Object> variables = new HashMap<>();
            variables.put("full_name", userDto.getFirstname() + " " + userDto.getLastname());
            String token = jwtTokenizer.buildVerificationToken(userDto.getEmail());
            variables.put("verification_link", origin + "/register/verify/" + token);
            helper.setText(thymeleafService.createContent("verification_email.html", variables), true);
            helper.setFrom(senderEmail);
            mailSender.send(message);
        } catch (Exception e) {
            LOG.debug("Send verification email failed", e.getStackTrace());
        }
    }

    @Override
    public void sendPasswordResetEmail(ApplicationUser userDto, String origin) {
        LOG.trace("Send password reset email {}", userDto);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            helper.setTo(userDto.getEmail());
            helper.setSubject("SmartRoute - Reset Password");

            Map<String, Object> variables = new HashMap<>();
            variables.put("full_name", userDto.getFirstname() + " " + userDto.getLastname());
            String token = jwtTokenizer.buildVerificationToken(userDto.getEmail());
            variables.put("password_reset_link", origin + "/password_reset/" + token);
            helper.setText(thymeleafService.createContent("password_reset_email.html", variables), true);
            helper.setFrom(senderEmail);
            mailSender.send(message);
        } catch (Exception e) {
            LOG.debug("Send verification email failed", e.getStackTrace());
        }
    }
}
