package com.smartroute.smartroute1.integrationtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.endpoint.dto.subscription.KeyDto;
import com.smartroute.smartroute1.endpoint.dto.subscription.MessageDto;
import com.smartroute.smartroute1.endpoint.dto.subscription.NativeSubscriptionDto;
import com.smartroute.smartroute1.endpoint.dto.subscription.SubscriptionDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Friendship;
import com.smartroute.smartroute1.repository.FriendshipRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.FriendshipService;
import com.smartroute.smartroute1.service.impl.PushNotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles({"generateData", "test"})
class PushNotificationEndpointTest extends BaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PushNotificationServiceImpl pushNotificationService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private FriendshipService friendshipService;

    @Autowired
    private FriendshipRepository friendshipRepository;

    private ApplicationUser testUser;
    private ApplicationUser friendUser;

    @BeforeEach
    void setUp() {
        testUser = new ApplicationUser();
        testUser.setEmail("test@example.com");
        testUser.setId(1L);

        friendUser = new ApplicationUser();
        friendUser.setEmail("friend@example.com");
        friendUser.setId(2L);


    }

    // ==================== subscribeWeb Tests ====================

    @Test
    @WithMockUser(username = "test@example.com", roles = "USER")
    void subscribeWeb_Success_ReturnsOk() throws Exception {
        // Arrange
        SubscriptionDto subscriptionDto = new SubscriptionDto();
        subscriptionDto.setEndpoint("https://push.example.com/notify/test");
        KeyDto key = new KeyDto();
        key.setP256dh("test-public-key");
        key.setAuth("test-auth");
        subscriptionDto.setKeys(key);

        when(userRepository.findUserByEmail("test@example.com")).thenReturn(testUser);
        doNothing().when(pushNotificationService).subscribe(any(SubscriptionDto.class), any(ApplicationUser.class));

        // Act & Assert
        mockMvc.perform(post("/api/v1/notifications/subscribe")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subscriptionDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Web push subscription successful"))
                .andExpect(jsonPath("$.status").value("subscribed"));

        verify(userRepository, times(1)).findUserByEmail("test@example.com");
        verify(pushNotificationService, times(1)).subscribe(any(SubscriptionDto.class), eq(testUser));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = "USER")
    void subscribeWeb_WithNullFields_StillProcesses() throws Exception {
        // Arrange
        SubscriptionDto subscriptionDto = new SubscriptionDto();
        // Null fields to test robustness

        when(userRepository.findUserByEmail("test@example.com")).thenReturn(testUser);
        doNothing().when(pushNotificationService).subscribe(any(SubscriptionDto.class), any(ApplicationUser.class));

        // Act & Assert
        mockMvc.perform(post("/api/v1/notifications/subscribe")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subscriptionDto)))
                .andExpect(status().isOk());

        verify(pushNotificationService, times(1)).subscribe(any(SubscriptionDto.class), any(ApplicationUser.class));
    }

    @Test
    void subscribeWeb_Unauthorized_Returns401() throws Exception {
        // Arrange
        SubscriptionDto subscriptionDto = new SubscriptionDto();
        subscriptionDto.setEndpoint("https://push.example.com/notify/test");

        // Act & Assert - No @WithMockUser, so should be unauthorized
        mockMvc.perform(post("/api/v1/notifications/subscribe")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subscriptionDto)))
                .andExpect(status().isForbidden());

        verify(pushNotificationService, never()).subscribe(any(), any());
    }

    @Test
    @WithMockUser(username = "nonexistent@example.com", roles = "USER")
    void subscribeWeb_UserNotFound_HandlesGracefully() throws Exception {
        // Arrange
        SubscriptionDto subscriptionDto = new SubscriptionDto();
        subscriptionDto.setEndpoint("https://push.example.com/notify/test");

        when(userRepository.findUserByEmail("nonexistent@example.com")).thenReturn(null);

        // Act & Assert - Should handle null user
        mockMvc.perform(post("/api/v1/notifications/subscribe")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subscriptionDto)))
                .andExpect(status().isOk());
    }

    // ==================== subscribeNative Tests ====================

    @Test
    @WithMockUser(username = "test@example.com", roles = "USER")
    void subscribeNative_IOS_Success() throws Exception {
        // Arrange
        NativeSubscriptionDto nativeDto = new NativeSubscriptionDto();
        nativeDto.setPlatform("ios");
        nativeDto.setToken("ios-device-token-123");

        when(userRepository.findUserByEmail("test@example.com")).thenReturn(testUser);
        doNothing().when(pushNotificationService).subscribeNative(any(NativeSubscriptionDto.class), any(ApplicationUser.class));

        // Act & Assert
        mockMvc.perform(post("/api/v1/notifications/subscribe-native")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nativeDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Native push subscription successful"))
                .andExpect(jsonPath("$.platform").value("ios"))
                .andExpect(jsonPath("$.status").value("subscribed"));

        verify(userRepository, times(1)).findUserByEmail("test@example.com");
        verify(pushNotificationService, times(1)).subscribeNative(any(NativeSubscriptionDto.class), eq(testUser));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = "USER")
    void subscribeNative_Android_Success() throws Exception {
        // Arrange
        NativeSubscriptionDto nativeDto = new NativeSubscriptionDto();
        nativeDto.setPlatform("android");
        nativeDto.setToken("android-device-token-456");

        when(userRepository.findUserByEmail("test@example.com")).thenReturn(testUser);
        doNothing().when(pushNotificationService).subscribeNative(any(NativeSubscriptionDto.class), any(ApplicationUser.class));

        // Act & Assert
        mockMvc.perform(post("/api/v1/notifications/subscribe-native")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nativeDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platform").value("android"));

        verify(pushNotificationService, times(1)).subscribeNative(any(NativeSubscriptionDto.class), eq(testUser));
    }

    @Test
    void subscribeNative_Unauthorized_Returns401() throws Exception {
        // Arrange
        NativeSubscriptionDto nativeDto = new NativeSubscriptionDto();
        nativeDto.setPlatform("ios");
        nativeDto.setToken("token");

        // Act & Assert
        mockMvc.perform(post("/api/v1/notifications/subscribe-native")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nativeDto)))
                .andExpect(status().isForbidden());

        verify(pushNotificationService, never()).subscribeNative(any(), any());
    }

    // ==================== testNotificationToFriend Tests ====================

    @Test
    @WithMockUser(username = "test@example.com", roles = "USER")
    void testNotificationToFriend_WithMultipleFriends_Success() throws Exception {
        // Arrange
        MessageDto messageDto = new MessageDto();
        messageDto.setTitle("Friend Notification");
        messageDto.setBody("Hello friends!");

        Friendship friendship1 = new Friendship();
        friendship1.setReceiver(friendUser);
        friendship1.setSender(testUser);

        ApplicationUser friend2 = new ApplicationUser();
        friend2.setEmail("friend2@example.com");
        Friendship friendship2 = new Friendship();
        friendship2.setReceiver(friend2);
        friendship2.setSender(testUser);

        List<Friendship> friendships = Arrays.asList(friendship1, friendship2);

        when(userRepository.findUserByEmail("test@example.com")).thenReturn(testUser);
        when(friendshipService.getFriends("test@example.com")).thenReturn(friendships);
        doNothing().when(pushNotificationService).sendToUser(any(ApplicationUser.class), anyString(), anyString());

        // Act & Assert
        mockMvc.perform(post("/api/v1/notifications/friend")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Notification sent successfully"))
                .andExpect(jsonPath("$.title").value("Friend Notification"))
                .andExpect(jsonPath("$.body").value("Hello friends!"))
                .andExpect(jsonPath("$.status").value("sent"));

        verify(friendshipService, times(1)).getFriends("test@example.com");
        verify(pushNotificationService, times(2)).sendToUser(any(ApplicationUser.class), eq("Friend Notification"), eq("Hello friends!"));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = "USER")
    void testNotificationToFriend_WithNoFriends_Success() throws Exception {
        // Arrange
        MessageDto messageDto = new MessageDto();
        messageDto.setTitle("Friend Notification");
        messageDto.setBody("Hello friends!");

        when(userRepository.findUserByEmail("test@example.com")).thenReturn(testUser);
        when(friendshipService.getFriends("test@example.com")).thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(post("/api/v1/notifications/friend")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sent"));

        verify(friendshipService, times(1)).getFriends("test@example.com");
        verify(pushNotificationService, never()).sendToUser(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = "USER")
    void testNotificationToFriend_WithOneFriend_Success() throws Exception {
        // Arrange
        MessageDto messageDto = new MessageDto();
        messageDto.setTitle("Single Friend");
        messageDto.setBody("Hey!");

        Friendship friendship = new Friendship();
        friendship.setReceiver(friendUser);
        friendship.setSender(testUser);

        when(userRepository.findUserByEmail("test@example.com")).thenReturn(testUser);
        when(friendshipService.getFriends("test@example.com")).thenReturn(Collections.singletonList(friendship));
        doNothing().when(pushNotificationService).sendToUser(any(ApplicationUser.class), anyString(), anyString());

        // Act & Assert
        mockMvc.perform(post("/api/v1/notifications/friend")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageDto)))
                .andExpect(status().isOk());

        verify(pushNotificationService, times(1)).sendToUser(friendUser, "Single Friend", "Hey!");
    }

    @Test
    void testNotificationToFriend_Unauthorized_Returns401() throws Exception {
        // Arrange
        MessageDto messageDto = new MessageDto();
        messageDto.setTitle("Test");
        messageDto.setBody("Test");

        // Act & Assert
        mockMvc.perform(post("/api/v1/notifications/friend")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageDto)))
                .andExpect(status().isForbidden());

        verify(friendshipService, never()).getFriends(any());
        verify(pushNotificationService, never()).sendToUser(any(), any(), any());
    }
}