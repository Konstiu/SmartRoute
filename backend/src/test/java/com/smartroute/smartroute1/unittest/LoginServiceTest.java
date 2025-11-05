package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.security.JwtTokenizer;
import com.smartroute.smartroute1.service.UserService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest()
@ActiveProfiles({ "test", "generateData" })
@Transactional
public class LoginServiceTest {

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtTokenizer jwtTokenizer;

}
