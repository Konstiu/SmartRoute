package com.smartroute.smartroute1.integrationtest;

import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.config.properties.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static com.smartroute.smartroute1.basetest.TestData.DEFAULT_USER_EMAIL;
import static com.smartroute.smartroute1.basetest.TestData.LOGIN_BASE_URI;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "generateData"})
@AutoConfigureMockMvc
class LoginEndpointTest extends BaseTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityProperties securityProperties;

    @Test
    void validUserLogin() throws Exception {
        String loginData = "{\"password\": \"password\", \"email\": \"" + DEFAULT_USER_EMAIL + "\"}";
        validLoginTest(loginData);
    }

    @Test
    void UserLoginWithNonexistentEmailReturnsNotFound() throws Exception {
        String loginData = "{\"password\": \"Password123\", \"email\": \"nonexistentEmail@student.tuwien.ac.at\"}";
        invalidLoginTest(loginData, HttpStatus.NOT_FOUND.value());
    }

    private void validLoginTest(String loginData)
            throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(LOGIN_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginData))
                .andDo(print())
                .andReturn();
        MockHttpServletResponse response = mvcResult.getResponse();
        String responseBody = response.getContentAsString();
        String token = responseBody.replace("Bearer ", "");
        byte[] signingKey = securityProperties.getJwtSecret().getBytes();
        Claims claims = Jwts.parser()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertAll(
                () -> assertEquals(HttpStatus.OK.value(), response.getStatus()),
                () -> assertEquals("text/plain;charset=UTF-8", response.getContentType()),
                () -> assertEquals(com.smartroute.smartroute1.basetest.TestData.DEFAULT_USER_EMAIL, claims.get("sub")));
    }

    private void invalidLoginTest(String loginData, int expectedStatus) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(LOGIN_BASE_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginData))
                .andDo(print())
                .andReturn();
        MockHttpServletResponse response = mvcResult.getResponse();
        assertAll(
                () -> assertEquals(expectedStatus, response.getStatus()),
                () -> assertEquals("text/plain;charset=UTF-8", response.getContentType()));
    }
}
