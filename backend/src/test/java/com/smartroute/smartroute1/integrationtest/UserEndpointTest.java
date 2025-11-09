package com.smartroute.smartroute1.integrationtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.endpoint.dto.CreateUserDto;
import com.smartroute.smartroute1.endpoint.mapper.UserMapper;
import com.smartroute.smartroute1.entity.ApplicationUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;


import static com.smartroute.smartroute1.basetest.TestData.DEFAULT_USER_EMAIL;
import static com.smartroute.smartroute1.basetest.TestData.USER_BASE_URI;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "generateData"})
@AutoConfigureMockMvc
class UserEndpointTest extends BaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;


    @Test
    void createNewValidUser() throws Exception {
        ApplicationUser user = new ApplicationUser("test@email.com", "SuperSecretPassword", "Konsti", "U");
        CreateUserDto studentDto = userMapper.applicationUserToDto(user);
        String body = objectMapper.writeValueAsString(studentDto);

        MvcResult mvcResult = this.mockMvc.perform(post(USER_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        MockHttpServletResponse response = mvcResult.getResponse();

        assertAll(
                () -> assertEquals(HttpStatus.CREATED.value(), response.getStatus()),
                () -> assertEquals(MediaType.APPLICATION_JSON_VALUE, response.getContentType())
        );

        CreateUserDto createdUserDto = objectMapper.readValue(response.getContentAsString(),
                CreateUserDto.class);

        assertAll(
                () -> assertNotNull(createdUserDto),
                () -> assertEquals(createdUserDto.getEmail(), studentDto.getEmail()),
                () -> assertEquals(createdUserDto.getFirstname(), studentDto.getFirstname()),
                () -> assertEquals(createdUserDto.getLastname(), studentDto.getLastname()),
                () -> assertTrue(passwordEncoder.matches(studentDto.getPassword(), createdUserDto.getPassword()))
        );

    }

    @Test
    void createUserWithExistingEmailShouldFailWith422() throws Exception {
        CreateUserDto user = new CreateUserDto();
        user.setPassword("Password123");
        user.setFirstname("test");
        user.setLastname("test");
        user.setEmail(DEFAULT_USER_EMAIL);
        String body = objectMapper.writeValueAsString(user);

        MvcResult mvcResult = this.mockMvc.perform(post(USER_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();
        mvcResult.getResponse().setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());

        assertAll(
                () -> assertNotNull(mvcResult.getResponse()),
                () -> assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), mvcResult.getResponse().getStatus())
        );

    }


    @Test
    void createNewInvalidUser_422() throws Exception {
        ApplicationUser user = new ApplicationUser();
        CreateUserDto userDto = userMapper.applicationUserToDto(user);
        String body = objectMapper.writeValueAsString(userDto);

        MvcResult mvcResult = this.mockMvc.perform(post(USER_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())  // Expect 422
                .andReturn();

        MockHttpServletResponse response = mvcResult.getResponse();

        assertAll(
                () -> assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), response.getStatus()),
                () -> {
                    String content = response.getContentAsString();
                    assertTrue(content.contains("Email cannot be null"));
                }
        );
    }
}