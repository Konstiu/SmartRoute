package com.smartroute.smartroute1.unittest;


import com.smartroute.smartroute1.endpoint.dto.CreateUserDto;
import com.smartroute.smartroute1.endpoint.mapper.UserMapper;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.service.UserService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest()
@ActiveProfiles({"test", "generateData"})
@Transactional
class UserServiceTest {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void createNewValidUser() throws Exception {
        ApplicationUser user = new ApplicationUser("test@email.com", "Password", "K", "U");
        CreateUserDto applicationUserDto = userMapper.applicationUserToDto(user);

        ApplicationUser createdApplicationUser = userService.create(applicationUserDto, null);

        assertTrue(passwordEncoder.matches(user.getPassword(), createdApplicationUser.getPassword()));

        assertAll(
                () -> assertNotNull(createdApplicationUser),
                () -> assertEquals(createdApplicationUser.getEmail(), applicationUserDto.getEmail()),
                () -> assertEquals(createdApplicationUser.getFirstname(), applicationUserDto.getFirstname()),
                () -> assertEquals(createdApplicationUser.getLastname(), applicationUserDto.getLastname()),
                () -> assertTrue(passwordEncoder.matches(applicationUserDto.getPassword(), createdApplicationUser.getPassword()))
        );

    }
}