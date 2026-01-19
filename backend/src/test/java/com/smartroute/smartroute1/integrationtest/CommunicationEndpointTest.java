package com.smartroute.smartroute1.integrationtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.endpoint.dto.FriendDeviceBundlesDto;
import com.smartroute.smartroute1.endpoint.dto.MessageDetailDto;
import com.smartroute.smartroute1.endpoint.dto.OneTimePreKeyDto;
import com.smartroute.smartroute1.endpoint.dto.UserDetailDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Message;
import com.smartroute.smartroute1.entity.UserDevice;
import com.smartroute.smartroute1.service.impl.CommunicationServiceImpl;
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
import java.util.*;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
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
    private CommunicationServiceImpl communicationService;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private MessageMapper messageMapper;

    @Test
    @WithMockUser(username = "test@email.com")
    void uploadIdentityKey_withRoleUser_returnsUserDetailDto() throws Exception {
        String deviceId = "device123";
        String publicKey = "my-public-key";
        String publicDHKey = "my-public-dh-key";
        
        ApplicationUser returnedUser = new ApplicationUser();
        returnedUser.setEmail("test@email.com");

        UserDetailDto detailDto = new UserDetailDto();
        detailDto.setEmail("test@email.com");

        when(communicationService.uploadIdentityKey(
            eq("test@email.com"), 
            eq(deviceId), 
            eq(publicKey), 
            eq(publicDHKey)
        )).thenReturn(returnedUser);
        when(userMapper.applicationUserToDetailDto(returnedUser)).thenReturn(detailDto);

        String requestJson = objectMapper.writeValueAsString(Map.of(
                "deviceId", deviceId,
                "publicKey", publicKey,
                "publicDhKey", publicDHKey
        ));

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
        String requestJson = objectMapper.writeValueAsString(Map.of(
            "deviceId", "device123",
            "publicKey", publicKey,
            "publicDhKey", "dhkey"
        ));

        mockMvc.perform(put("/api/v1/communication/upload-identity-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "prekeyuser@example.com")
    void uploadSignedPreKey_withRoleUser_returnsUserDetailDto() throws Exception {
        String deviceId = "device456";
        String publicPreKey = "my-pre-key";
        String signature = "signed-by-user";

        ApplicationUser returnedUser = new ApplicationUser();
        returnedUser.setEmail("prekeyuser@example.com");

        UserDetailDto detailDto = new UserDetailDto();
        detailDto.setEmail("prekeyuser@example.com");

        when(communicationService.uploadSignedPreKey(
            eq("prekeyuser@example.com"), 
            eq(publicPreKey), 
            eq(signature),
            eq(deviceId)
        )).thenReturn(returnedUser);
        when(userMapper.applicationUserToDetailDto(returnedUser)).thenReturn(detailDto);

        String requestJson = objectMapper.writeValueAsString(Map.of(
            "deviceId", deviceId,
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
        String requestJson = objectMapper.writeValueAsString(Map.of(
            "deviceId", "device123",
            "publicPreKey", "noprekey",
            "signature", "nosign"
        ));

        mockMvc.perform(put("/api/v1/communication/upload-signed-pre-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "otpuser@example.com")
    void getAmountOfOneTimePreKeys_withRoleUser_returnsCount() throws Exception {
        String deviceId = "device789";
        when(communicationService.countOneTimePreKeys(eq("otpuser@example.com"), eq(deviceId)))
            .thenReturn(5L);

        mockMvc.perform(get("/api/v1/communication/amount-of-one-time-pre-keys")
                        .param("deviceId", deviceId))
                .andExpect(status().isOk())
                .andExpect(content().string("5"));
    }

    @Test
    void getAmountOfOneTimePreKeys_withoutRole_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/communication/amount-of-one-time-pre-keys")
                        .param("deviceId", "device123"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "otpuser2@example.com")
    void uploadOneTimePreKeys_withRoleUser_acceptsPayload() throws Exception {
        String deviceId = "device999";
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        var payload = Map.of(
                "deviceId", deviceId,
                "oneTimePreKeys", List.of(
                        Map.of("uuid", id1.toString(), "publicKey", "publicKey1"),
                        Map.of("uuid", id2.toString(), "publicKey", "publicKey2")
                )
        );

        doNothing().when(communicationService).uploadOneTimePreKeys(
                eq("otpuser2@example.com"),
                eq(deviceId),
                anyList()
        );

        String requestJson = objectMapper.writeValueAsString(payload);

        mockMvc.perform(put("/api/v1/communication/upload-one-time-pre-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());
    }

    @Test
    void uploadOneTimePreKeys_withoutRole_4xxError() throws Exception {
        var payload = Map.of(
            "deviceId", "device123",
            "oneTimePreKeys", List.of("key1")
        );
        String requestJson = objectMapper.writeValueAsString(payload);

        mockMvc.perform(put("/api/v1/communication/upload-one-time-pre-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = "requester@example.com")
    void getKeysOfFriendAllDevices_withRoleUser_returnsBundles() throws Exception {
        FriendDeviceBundlesDto bundles = new FriendDeviceBundlesDto();
        // Set up your FriendDeviceBundlesDto with appropriate data

        when(communicationService.getKeysOfFriendAllDevices(
            eq("friend@example.com"), 
            eq("requester@example.com")
        )).thenReturn(bundles);

        mockMvc.perform(get("/api/v1/communication/keys-of-friend-devices/{friendEmail}", 
                        "friend@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void getKeysOfFriendAllDevices_withoutRole_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/communication/keys-of-friend-devices/{friendEmail}", 
                        "friend@example.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "myuser@example.com")
    void getKeysOfAllMyDevices_withRoleUser_returnsBundles() throws Exception {
        FriendDeviceBundlesDto bundles = new FriendDeviceBundlesDto();

        when(communicationService.getKeysOfAllMyDevices(eq("myuser@example.com")))
            .thenReturn(bundles);

        mockMvc.perform(get("/api/v1/communication/keys-of-my-devices"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void getKeysOfAllMyDevices_withoutRole_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/communication/keys-of-my-devices"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "sender@example.com")
    void sendEncryptedMessageToMyDevices_withRoleUser_returnsMessageDetailDto() throws Exception {
        var payload = Map.of(
            "senderEmail", "sender@example.com",
            "recipientEmail", "sender@example.com",
            "senderDeviceId", "device1",
            "recipientDeviceId", "device2",
            "encryptedContent", "encrypted-data"
        );

        String requestJson = objectMapper.writeValueAsString(payload);

        Message returnedMessage = new Message();
        MessageDetailDto dto = new MessageDetailDto();
        dto.setSenderEmail("sender@example.com");
        dto.setRecipientEmail("sender@example.com");

        when(communicationService.sendEncryptedMessageToMyDevices(
            eq("sender@example.com"), 
            any(MessageDetailDto.class)
        )).thenReturn(returnedMessage);
        when(messageMapper.entityToMessageDetailDto(returnedMessage)).thenReturn(dto);

        mockMvc.perform(post("/api/v1/communication/messages_my_devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.senderEmail").value("sender@example.com"));
    }

    @Test
    void sendEncryptedMessageToMyDevices_withoutRole_forbidden() throws Exception {
        var payload = Map.of(
            "senderEmail", "sender@example.com",
            "encryptedContent", "data"
        );
        String requestJson = objectMapper.writeValueAsString(payload);

        mockMvc.perform(post("/api/v1/communication/messages_my_devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "sender@example.com")
    void sendEncryptedMessage_withRoleUser_returnsMessageDetailDto() throws Exception {
        var payload = Map.of(
            "senderEmail", "sender@example.com",
            "recipientEmail", "recipient@example.com",
            "senderDeviceId", "device1",
            "recipientDeviceId", "device2",
            "encryptedContent", "encrypted-data"
        );

        String requestJson = objectMapper.writeValueAsString(payload);

        Message returnedMessage = new Message();
        MessageDetailDto dto = new MessageDetailDto();
        dto.setSenderEmail("sender@example.com");
        dto.setRecipientEmail("recipient@example.com");

        when(communicationService.sendEncryptedMessage(
            eq("sender@example.com"), 
            any(MessageDetailDto.class)
        )).thenReturn(returnedMessage);
        when(messageMapper.entityToMessageDetailDto(returnedMessage)).thenReturn(dto);

        mockMvc.perform(post("/api/v1/communication/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.senderEmail").value("sender@example.com"))
                .andExpect(jsonPath("$.recipientEmail").value("recipient@example.com"));
    }

    @Test
    void sendEncryptedMessage_withoutRole_forbidden() throws Exception {
        var payload = Map.of(
            "senderEmail", "sender@example.com",
            "recipientEmail", "recipient@example.com",
            "encryptedContent", "data"
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
            "encryptedContent", "data"
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
        String deviceId = "device123";
        Instant since = Instant.now().minusSeconds(60);

        Message m1 = new Message();
        m1.setId(1L);
        ApplicationUser s = new ApplicationUser(); 
        s.setEmail("user@example.com");
        ApplicationUser r = new ApplicationUser(); 
        r.setEmail("friend@example.com");
        m1.setSender(s);
        m1.setRecipient(r);
        m1.setTimestamp(since.plusSeconds(10));

        Message m2 = new Message();
        m2.setId(2L);
        ApplicationUser s2 = new ApplicationUser(); 
        s2.setEmail("friend@example.com");
        ApplicationUser r2 = new ApplicationUser(); 
        r2.setEmail("user@example.com");
        m2.setSender(s2);
        m2.setRecipient(r2);
        m2.setTimestamp(since.plusSeconds(20));

        MessageDetailDto dto1 = new MessageDetailDto();
        dto1.setSenderEmail("user@example.com");
        dto1.setRecipientEmail("friend@example.com");

        MessageDetailDto dto2 = new MessageDetailDto();
        dto2.setSenderEmail("friend@example.com");
        dto2.setRecipientEmail("user@example.com");

        when(communicationService.retrieveMessagesByFriendAndTimestamp(
            eq("user@example.com"), 
            eq("friend@example.com"), 
            any(Instant.class),
            eq(deviceId)
        )).thenReturn(List.of(m1, m2));
        when(messageMapper.entityToMessageDetailDto(m1)).thenReturn(dto1);
        when(messageMapper.entityToMessageDetailDto(m2)).thenReturn(dto2);

        mockMvc.perform(get("/api/v1/communication/messages/{friendEmail}", "friend@example.com")
                        .param("timestamp", since.toString())
                        .param("deviceId", deviceId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].senderEmail").value("user@example.com"))
                .andExpect(jsonPath("$[1].senderEmail").value("friend@example.com"));
    }

    @Test
    @WithMockUser(username = "user2@example.com")
    void getMessages_withRoleUser_andNoMessages_returnsEmptyList() throws Exception {
        String deviceId = "device456";
        Instant since = Instant.now();
        
        when(communicationService.retrieveMessagesByFriendAndTimestamp(
            eq("user2@example.com"), 
            eq("friend2@example.com"), 
            any(Instant.class),
            eq(deviceId)
        )).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/communication/messages/{friendEmail}", "friend2@example.com")
                        .param("timestamp", since.toString())
                        .param("deviceId", deviceId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getMessages_withoutRole_forbidden() throws Exception {
        Instant since = Instant.now();
        mockMvc.perform(get("/api/v1/communication/messages/{friendEmail}", "friend3@example.com")
                        .param("timestamp", since.toString())
                        .param("deviceId", "device123"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void getDevices_withRoleUser_returnsDeviceList() throws Exception {
        List<String> devices = List.of("device1", "device2", "device3");

        when(communicationService.getDevicesOfFriend(
            eq("user@example.com"), 
            eq("friend@example.com")
        )).thenReturn(devices);

        mockMvc.perform(get("/api/v1/communication/devices/{friendEmail}", "friend@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0]").value("device1"))
                .andExpect(jsonPath("$[1]").value("device2"))
                .andExpect(jsonPath("$[2]").value("device3"));
    }

    @Test
    void getDevices_withoutRole_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/communication/devices/{friendEmail}", "friend@example.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void getMyDevices_withRoleUser_returnsMyDeviceList() throws Exception {
        List<String> myDevices = List.of("myDevice1", "myDevice2");

        when(communicationService.getMyDevices(eq("user@example.com")))
            .thenReturn(myDevices);

        mockMvc.perform(get("/api/v1/communication/my-devices"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("myDevice1"))
                .andExpect(jsonPath("$[1]").value("myDevice2"));
    }

    @Test
    void getMyDevices_withoutRole_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/communication/my-devices"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void uploadSignedPreKey_whenValidationExceptionThrown_returns422() throws Exception {
        String requestJson = objectMapper.writeValueAsString(Map.of(
            "deviceId", "device123",
            "publicPreKey", "invalidkey",
            "signature", "badsignature"
        ));

        when(communicationService.uploadSignedPreKey(
            eq("user@example.com"), 
            any(), 
            any(), 
            any()
        )).thenThrow(new com.smartroute.smartroute1.exception.ValidationException("Invalid signature"));

        mockMvc.perform(put("/api/v1/communication/upload-signed-pre-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void uploadOneTimePreKeys_whenValidationExceptionThrown_returns4xx() throws Exception {
        String requestJson = objectMapper.writeValueAsString(Map.of(
            "deviceId", "device123",
            "oneTimePreKeys", List.of("badkey1", "badkey2")
        ));

        doThrow(new com.smartroute.smartroute1.exception.ValidationException("Invalid keys"))
            .when(communicationService)
            .uploadOneTimePreKeys(eq("user@example.com"), any(), any());

        mockMvc.perform(put("/api/v1/communication/upload-one-time-pre-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void getAmountOfOneTimePreKeys_whenValidationExceptionThrown_returns422() throws Exception {
        String deviceId = "invalidDevice";
        
        when(communicationService.countOneTimePreKeys(eq("user@example.com"), eq(deviceId)))
            .thenThrow(new com.smartroute.smartroute1.exception.ValidationException("Invalid device"));

        mockMvc.perform(get("/api/v1/communication/amount-of-one-time-pre-keys")
                        .param("deviceId", deviceId))
                .andExpect(status().isUnprocessableEntity());
    }
}
