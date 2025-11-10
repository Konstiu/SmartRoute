package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.endpoint.dto.UserLoginDto;
import com.smartroute.smartroute1.service.UserService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest()
@ActiveProfiles({ "test", "generateData" })
@Transactional
class LoginServiceTest {

	@Autowired
	private UserService userService;


	@Test
	void login_withValidCredentials_shouldReturnToken() {
		UserLoginDto loginDto = new UserLoginDto();
		loginDto.setEmail("email0@smartroute.com");
		loginDto.setPassword("password0");

		String token = userService.login(loginDto);

		assertNotNull(token);
		assertFalse(token.isEmpty());
	}

	@Test
	void login_withInvalidPassword_shouldThrowBadCredentialsException() {
		UserLoginDto loginDto = new UserLoginDto();
		loginDto.setEmail("emial0@smartroute.com");
		loginDto.setPassword("WrongPassword123!");

		assertThrows(UsernameNotFoundException.class, () -> userService.login(loginDto));
	}

	@Test
	void login_withNonExistentUser_shouldThrowUsernameNotFoundException() {
		UserLoginDto loginDto = new UserLoginDto();
		loginDto.setEmail("nonexistent@email.com");
		loginDto.setPassword("Password123!");

		assertThrows(UsernameNotFoundException.class, () -> userService.login(loginDto));
	}

}
