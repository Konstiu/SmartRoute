package com.smartroute.smartroute1.integrationtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.endpoint.dto.KeysDto;
import com.smartroute.smartroute1.endpoint.dto.MessageDetailDto;
import com.smartroute.smartroute1.endpoint.dto.UserDetailDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Message;
import com.smartroute.smartroute1.service.CommunicationService;
import com.smartroute.smartroute1.endpoint.mapper.UserMapper;
import com.smartroute.smartroute1.endpoint.mapper.MessageMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @MockitoBean
    private MessageMapper messageMapper;

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

    @Test
    @WithMockUser(username = "requester@example.com")
    void getKeysOfFriend_withRoleUser_returnsKeysWithoutOneTimePreKey() throws Exception {
        KeysDto keys = new KeysDto();
        keys.setIdentityKey("IDK");
        keys.setSignedPreKey("SPK");
        keys.setSignedPreKeySignature("SIG");
        keys.setOneTimePreKey(null);

        when(communicationService.getKeysOfFriend(eq("friend@example.com"), eq("requester@example.com"))).thenReturn(keys);

        mockMvc.perform(get("/api/v1/communication/keys-of-friend/{friendEmail}", "friend@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.identityKey").value("IDK"))
                .andExpect(jsonPath("$.signedPreKey").value("SPK"))
                .andExpect(jsonPath("$.signedPreKeySignature").value("SIG"))
                .andExpect(jsonPath("$.oneTimePreKey").doesNotExist());
    }

    @Test
    @WithMockUser(username = "requester2@example.com")
    void getKeysOfFriend_withRoleUser_returnsKeysWithOneTimePreKey() throws Exception {
        KeysDto keys = new KeysDto();
        keys.setIdentityKey("IDK2");
        keys.setSignedPreKey("SPK2");
        keys.setSignedPreKeySignature("SIG2");

        // use DTO for oneTimePreKey
        com.smartroute.smartroute1.endpoint.dto.OneTimePreKeyDto ot = new com.smartroute.smartroute1.endpoint.dto.OneTimePreKeyDto();
        ot.setUuid(UUID.randomUUID());
        ot.setPublicKey("OTPK2");
        keys.setOneTimePreKey(ot);

        when(communicationService.getKeysOfFriend(eq("friend2@example.com"), eq("requester2@example.com"))).thenReturn(keys);

        mockMvc.perform(get("/api/v1/communication/keys-of-friend/{friendEmail}", "friend2@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.identityKey").value("IDK2"))
                .andExpect(jsonPath("$.signedPreKey").value("SPK2"))
                .andExpect(jsonPath("$.signedPreKeySignature").value("SIG2"))
                .andExpect(jsonPath("$.oneTimePreKey.publicKey").value("OTPK2"));
    }

    @Test
    void getKeysOfFriend_withoutRole_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/communication/keys-of-friend/{friendEmail}", "friend@example.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "sender@example.com")
    void sendEncryptedMessage_withRoleUser_returnsMessageDetailDto() throws Exception {
        UUID usedId = UUID.randomUUID();
        var payload = Map.of(
            "senderEmail", "sender@example.com",
            "recipientEmail", "recipient@example.com",
            "senderIdentityKey", "S_ID",
            "senderEphemeralKey", "S_EPH",
            "usedOneTimePreKeyId", usedId,
            "encryptedMessage", Map.of(
                "ciphertext", "CIPH",
                "nonce", "NON",
                "messageNumber", 1,
                "ratchetPublicKey", "RPK"
            )
        );

        String requestJson = objectMapper.writeValueAsString(payload);

        Message returnedMessage = new Message();
        ApplicationUser s = new ApplicationUser(); s.setEmail("sender@example.com");
        ApplicationUser r = new ApplicationUser(); r.setEmail("recipient@example.com");
        returnedMessage.setSender(s);
        returnedMessage.setRecipient(r);
        returnedMessage.setSenderIdentityKey("S_ID");
        returnedMessage.setSenderEphemeralKey("S_EPH");
        returnedMessage.setUsedOneTimePreKeyId(usedId);
        returnedMessage.setCiphertext("CIPH");
        returnedMessage.setNonce("NON");
        returnedMessage.setMessageNumber(1L);
        returnedMessage.setRatchetPublicKey("RPK");

        MessageDetailDto dto = new MessageDetailDto();
        dto.setSenderEmail("sender@example.com");
        dto.setRecipientEmail("recipient@example.com");
        dto.setSenderIdentityKey("S_ID");
        dto.setSenderEphemeralKey("S_EPH");
        dto.setUsedOneTimePreKeyId(usedId);
        com.smartroute.smartroute1.endpoint.dto.EncryptedMessageDto enc = new com.smartroute.smartroute1.endpoint.dto.EncryptedMessageDto();
        enc.setCiphertext("CIPH"); enc.setNonce("NON"); enc.setMessageNumber(1L); enc.setRatchetPublicKey("RPK");
        dto.setEncryptedMessage(enc);

        when(communicationService.sendEncryptedMessage(eq("sender@example.com"), any())).thenReturn(returnedMessage);
        when(messageMapper.entityToMessageDetailDto(returnedMessage)).thenReturn(dto);

        mockMvc.perform(post("/api/v1/communication/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.senderEmail").value("sender@example.com"))
                .andExpect(jsonPath("$.recipientEmail").value("recipient@example.com"))
                .andExpect(jsonPath("$.encryptedMessage.ciphertext").value("CIPH"));
    }

    @Test
    void sendEncryptedMessage_withoutRole_forbidden() throws Exception {
        var payload = Map.of(
            "senderEmail", "sender@example.com",
            "recipientEmail", "recipient@example.com",
            "encryptedMessage", Map.of("ciphertext","C","nonce","N","messageNumber",1,"ratchetPublicKey","R")
        );
        String requestJson = objectMapper.writeValueAsString(payload);

        mockMvc.perform(post("/api/v1/communication/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "sender2@example.com")
    void sendEncryptedMessage_whenServiceThrowsValidationException_returns422() throws Exception {
        var payload = Map.of(
            "senderEmail", "sender2@example.com",
            "recipientEmail", "recipient2@example.com",
            "encryptedMessage", Map.of("ciphertext","C2","nonce","N2","messageNumber",2,"ratchetPublicKey","R2")
        );
        String requestJson = objectMapper.writeValueAsString(payload);

        when(communicationService.sendEncryptedMessage(eq("sender2@example.com"), any()))
                .thenThrow(new com.smartroute.smartroute1.exception.ValidationException("invalid"));

        mockMvc.perform(post("/api/v1/communication/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void getMessages_withRoleUser_returnsMappedMessages() throws Exception {
        Instant since = Instant.now().minusSeconds(60);

        // prepare messages returned by service
        Message m1 = new Message();
        ApplicationUser s = new ApplicationUser(); s.setEmail("user@example.com");
        ApplicationUser r = new ApplicationUser(); r.setEmail("friend@example.com");
        m1.setSender(s);
        m1.setRecipient(r);
        m1.setCiphertext("C1");
        m1.setTimestamp(since.plusSeconds(10));

        Message m2 = new Message();
        ApplicationUser s2 = new ApplicationUser(); s2.setEmail("friend@example.com");
        ApplicationUser r2 = new ApplicationUser(); r2.setEmail("user@example.com");
        m2.setSender(s2);
        m2.setRecipient(r2);
        m2.setCiphertext("C2");
        m2.setTimestamp(since.plusSeconds(20));

        // DTOs produced by mapper
        MessageDetailDto dto1 = new MessageDetailDto();
        dto1.setSenderEmail("user@example.com");
        dto1.setRecipientEmail("friend@example.com");
        com.smartroute.smartroute1.endpoint.dto.EncryptedMessageDto enc1 = new com.smartroute.smartroute1.endpoint.dto.EncryptedMessageDto();
        enc1.setCiphertext("C1"); dto1.setEncryptedMessage(enc1);

        MessageDetailDto dto2 = new MessageDetailDto();
        dto2.setSenderEmail("friend@example.com");
        dto2.setRecipientEmail("user@example.com");
        com.smartroute.smartroute1.endpoint.dto.EncryptedMessageDto enc2 = new com.smartroute.smartroute1.endpoint.dto.EncryptedMessageDto();
        enc2.setCiphertext("C2"); dto2.setEncryptedMessage(enc2);

        when(communicationService.retrieveMessagesByFriendAndTimestamp(eq("user@example.com"), eq("friend@example.com"), any()))
                .thenReturn(List.of(m1, m2));
        when(messageMapper.entityToMessageDetailDto(m1)).thenReturn(dto1);
        when(messageMapper.entityToMessageDetailDto(m2)).thenReturn(dto2);

        mockMvc.perform(get("/api/v1/communication/messages/{friendEmail}", "friend@example.com")
                        .param("timestamp", since.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].senderEmail").value("user@example.com"))
                .andExpect(jsonPath("$[1].senderEmail").value("friend@example.com"));
    }

    @Test
    @WithMockUser(username = "user2@example.com")
    void getMessages_withRoleUser_andNoMessages_returnsEmptyList() throws Exception {
        Instant since = Instant.now();
        when(communicationService.retrieveMessagesByFriendAndTimestamp(eq("user2@example.com"), eq("friend2@example.com"), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/communication/messages/{friendEmail}", "friend2@example.com")
                        .param("timestamp", since.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getMessages_withoutRole_forbidden() throws Exception {
        Instant since = Instant.now();
        mockMvc.perform(get("/api/v1/communication/messages/{friendEmail}", "friend3@example.com")
                        .param("timestamp", since.toString()))
                .andExpect(status().isForbidden());
    }

}
