package com.smartroute.smartroute1.integrationtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.endpoint.dto.UserDetailDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.service.CommunicationService;
import com.smartroute.smartroute1.endpoint.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc
public class CommunicationEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CommunicationService communicationService;

    @MockitoBean
    private UserMapper userMapper;

    @Test
    @WithMockUser(username = "test@email.com")
    void uploadIdentityKey_withRoleUser_returnsUserDetailDto() throws Exception {
        String publicKey = "my-public-key";
        ApplicationUser returnedUser = new ApplicationUser();
        returnedUser.setEmail("test@email.com");
        returnedUser.setPublicIdentityKey(publicKey);

        UserDetailDto detailDto = new UserDetailDto();
        detailDto.setEmail("test@email.com");

        when(communicationService.uploadIdentityKey(eq("test@email.com"), eq(publicKey))).thenReturn(returnedUser);
        when(userMapper.applicationUserToDetailDto(returnedUser)).thenReturn(detailDto);

        String requestJson = objectMapper.writeValueAsString(Map.of("publicKey", publicKey));

        mockMvc.perform(put("/api/v1/communication/upload-identity-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.email").value("test@email.com"));
    }

    @Test
    void uploadIdentityKey_withoutRole_forbidden() throws Exception {
        String publicKey = "no-access-key";
        String requestJson = objectMapper.writeValueAsString(Map.of("publicKey", publicKey));

        mockMvc.perform(put("/api/v1/communication/upload-identity-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "prekeyuser@example.com")
    void uploadSignedPreKey_withRoleUser_returnsUserDetailDto() throws Exception {
        String publicPreKey = "my-pre-key";
        String signature = "signed-by-user";

        ApplicationUser returnedUser = new ApplicationUser();
        returnedUser.setEmail("prekeyuser@example.com");

        UserDetailDto detailDto = new UserDetailDto();
        detailDto.setEmail("prekeyuser@example.com");

        when(communicationService.uploadSignedPreKey(eq("prekeyuser@example.com"), eq(publicPreKey), eq(signature))).thenReturn(returnedUser);
        when(userMapper.applicationUserToDetailDto(returnedUser)).thenReturn(detailDto);

        String requestJson = objectMapper.writeValueAsString(Map.of(
            "publicPreKey", publicPreKey,
            "signature", signature
        ));

        mockMvc.perform(put("/api/v1/communication/upload-signed-pre-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.email").value("prekeyuser@example.com"));
    }

    @Test
    void uploadSignedPreKey_withoutRole_forbidden() throws Exception {
        String publicPreKey = "noprekey";
        String signature = "nosign";

        String requestJson = objectMapper.writeValueAsString(Map.of(
            "publicPreKey", publicPreKey,
            "signature", signature
        ));

        mockMvc.perform(put("/api/v1/communication/upload-signed-pre-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "otpuser@example.com")
    void getAmountOfOneTimePreKeys_withRoleUser_returnsCount() throws Exception {
        when(communicationService.countOneTimePreKeys(eq("otpuser@example.com"))).thenReturn(5L);

        mockMvc.perform(get("/api/v1/communication/amount-of-one-time-pre-keys"))
                .andExpect(status().isOk())
                .andExpect(content().string("5"));
    }

    @Test
    void getAmountOfOneTimePreKeys_withoutRole_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/communication/amount-of-one-time-pre-keys"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "otpuser2@example.com")
    void uploadOneTimePreKeys_withRoleUser_acceptsPayload() throws Exception {
        // create two one-time pre-keys payload
        var key1 = Map.of("uuid", UUID.randomUUID(), "publicKey", "ONE1");
        var key2 = Map.of("uuid", UUID.randomUUID(), "publicKey", "ONE2");
        var payload = Map.of("oneTimePreKeys", List.of(key1, key2));

        String requestJson = objectMapper.writeValueAsString(payload);

        mockMvc.perform(put("/api/v1/communication/upload-one-time-pre-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());
    }

    @Test
    void uploadOneTimePreKeys_withoutRole_forbidden() throws Exception {
        var key = Map.of("uuid", UUID.randomUUID(), "publicKey", "ONE");
        var payload = Map.of("oneTimePreKeys", List.of(key));
        String requestJson = objectMapper.writeValueAsString(payload);

        mockMvc.perform(put("/api/v1/communication/upload-one-time-pre-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden());
    }

}
