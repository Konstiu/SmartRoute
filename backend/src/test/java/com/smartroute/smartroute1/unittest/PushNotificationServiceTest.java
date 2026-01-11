package com.smartroute.smartroute1.unittest;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.basetest.WebPushTestKeys;
import com.smartroute.smartroute1.datagenerator.InjuryDataGenerator;
import com.smartroute.smartroute1.endpoint.dto.subscription.KeyDto;
import com.smartroute.smartroute1.endpoint.dto.subscription.NativeSubscriptionDto;
import com.smartroute.smartroute1.endpoint.dto.subscription.SubscriptionDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.PushSubscription;
import com.smartroute.smartroute1.repository.*;
import com.smartroute.smartroute1.service.PushNotificationService;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for PushNotificationServiceImpl.
 * <p>
 * Mocks only the external push services (PushService, FirebaseMessaging) while
 * using real database and service logic.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "vapid.public.key=BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U",
        "vapid.private.key=UUxI4O8-FbRouAevSmBQ6o18hgE4nSG3qwvJTfKc-ls",
        "vapid.subject=mailto:test@example.com"
})
@ActiveProfiles({"test", "generateData"})
class PushNotificationServiceTest {

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private PushSubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private StravaAccountRepository stravaAccountRepository;

    @Autowired
    private InjuryRepository injuryRepository;

    @MockBean
    private PushService webPushService;

    private ApplicationUser testUser;
    private SubscriptionDto webSubscriptionDto;
    private NativeSubscriptionDto nativeSubscriptionDto;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(
                pushNotificationService,
                "webPushService",
                webPushService
        );

        // Clean up before each test
        activityRepository.deleteAllInBatch();
        injuryRepository.deleteAll();
        stravaAccountRepository.deleteAll();
        subscriptionRepository.deleteAll();
        userRepository.deleteAll();

        // Create and save test user
        testUser = new ApplicationUser();
        testUser.setEmail("test@example.com");
        testUser.setPassword("password123");
        testUser.setFirstname("Test");
        testUser.setLastname("User");
        testUser = userRepository.save(testUser);

        // Setup web subscription DTO
        webSubscriptionDto = new SubscriptionDto();
        webSubscriptionDto.setEndpoint("https://push.example.com/subscription/123");
        KeyDto keys = new KeyDto();
        keys.setP256dh(WebPushTestKeys.p256dh());
        keys.setAuth(WebPushTestKeys.auth());
        webSubscriptionDto.setKeys(keys);

        // Setup native subscription DTO
        nativeSubscriptionDto = new NativeSubscriptionDto();
        nativeSubscriptionDto.setToken("fcm-device-token-123");
        nativeSubscriptionDto.setPlatform("android");
    }

    // ==================== Web Subscription Tests ====================

    @Test
    void subscribe_WithNewWebSubscription_ShouldSaveToDatabase() {
        // Act
        pushNotificationService.subscribe(webSubscriptionDto, testUser);

        // Assert
        List<PushSubscription> subscriptions = subscriptionRepository.findPushSubscriptionByUser(testUser);
        assertEquals(1, subscriptions.size());

        PushSubscription saved = subscriptions.get(0);
        assertEquals(testUser.getId(), saved.getUser().getId());
        assertEquals("web", saved.getPlatform());
        assertEquals(webSubscriptionDto.getEndpoint(), saved.getEndpoint());
        assertEquals(webSubscriptionDto.getKeys().getP256dh(), saved.getP256dh());
        assertEquals(webSubscriptionDto.getKeys().getAuth(), saved.getAuth());
        assertNull(saved.getFcmToken());
    }

    @Test
    void subscribe_WithDuplicateWebSubscription_ShouldNotCreateDuplicate() {
        // Act - subscribe twice with same data
        pushNotificationService.subscribe(webSubscriptionDto, testUser);
        pushNotificationService.subscribe(webSubscriptionDto, testUser);

        // Assert - should only have one subscription
        List<PushSubscription> subscriptions = subscriptionRepository.findPushSubscriptionByUser(testUser);
        assertEquals(1, subscriptions.size());
    }

    @Test
    void subscribe_WithDifferentEndpoint_ShouldCreateNewSubscription() {
        // Arrange
        SubscriptionDto secondSubscription = new SubscriptionDto();
        secondSubscription.setEndpoint("https://push.example.com/subscription/456");
        KeyDto keys = new KeyDto();
        keys.setP256dh("different-public-key");
        keys.setAuth("different-auth-secret");
        secondSubscription.setKeys(keys);

        // Act
        pushNotificationService.subscribe(webSubscriptionDto, testUser);
        pushNotificationService.subscribe(secondSubscription, testUser);

        // Assert
        List<PushSubscription> subscriptions = subscriptionRepository.findPushSubscriptionByUser(testUser);
        assertEquals(2, subscriptions.size());
    }

    @Test
    void subscribe_MultipleUsersWithSameEndpoint_ShouldCreateSeparateSubscriptions() {
        // Arrange
        ApplicationUser secondUser = new ApplicationUser();
        secondUser.setEmail("user2@example.com");
        secondUser.setPassword("password456");
        secondUser.setFirstname("Test");
        secondUser.setLastname("User2");
        secondUser = userRepository.save(secondUser);

        // Act
        pushNotificationService.subscribe(webSubscriptionDto, testUser);
        pushNotificationService.subscribe(webSubscriptionDto, secondUser);

        // Assert
        List<PushSubscription> user1Subs = subscriptionRepository.findPushSubscriptionByUser(testUser);
        List<PushSubscription> user2Subs = subscriptionRepository.findPushSubscriptionByUser(secondUser);

        assertEquals(1, user1Subs.size());
        assertEquals(1, user2Subs.size());
        assertNotEquals(user1Subs.get(0).getUser().getId(), user2Subs.get(0).getUser().getId());
    }

    // ==================== Native Subscription Tests ====================

    @Test
    void subscribeNative_WithNewSubscription_ShouldSaveToDatabase() {
        // Act
        pushNotificationService.subscribeNative(nativeSubscriptionDto, testUser);

        // Assert
        List<PushSubscription> subscriptions = subscriptionRepository.findPushSubscriptionByUser(testUser);
        assertEquals(1, subscriptions.size());

        PushSubscription saved = subscriptions.get(0);
        assertEquals(testUser.getId(), saved.getUser().getId());
        assertEquals(nativeSubscriptionDto.getPlatform(), saved.getPlatform());
        assertEquals(nativeSubscriptionDto.getToken(), saved.getFcmToken());
        assertNull(saved.getEndpoint());
        assertNull(saved.getP256dh());
        assertNull(saved.getAuth());
    }

    @Test
    void subscribeNative_WithAndroidPlatform_ShouldSaveCorrectly() {
        // Arrange
        nativeSubscriptionDto.setPlatform("android");

        // Act
        pushNotificationService.subscribeNative(nativeSubscriptionDto, testUser);

        // Assert
        List<PushSubscription> subscriptions = subscriptionRepository.findPushSubscriptionByUser(testUser);
        assertEquals("android", subscriptions.get(0).getPlatform());
    }

    @Test
    void subscribeNative_WithIosPlatform_ShouldSaveCorrectly() {
        // Arrange
        nativeSubscriptionDto.setPlatform("ios");

        // Act
        pushNotificationService.subscribeNative(nativeSubscriptionDto, testUser);

        // Assert
        List<PushSubscription> subscriptions = subscriptionRepository.findPushSubscriptionByUser(testUser);
        assertEquals("ios", subscriptions.get(0).getPlatform());
    }

    @Test
    void subscribeNative_MultipleTimes_ShouldCreateMultipleEntries() {
        // Arrange
        NativeSubscriptionDto secondSubscription = new NativeSubscriptionDto();
        secondSubscription.setToken("new-fcm-token-456");
        secondSubscription.setPlatform("android");

        // Act
        pushNotificationService.subscribeNative(nativeSubscriptionDto, testUser);
        pushNotificationService.subscribeNative(secondSubscription, testUser);

        // Assert
        List<PushSubscription> subscriptions = subscriptionRepository.findPushSubscriptionByUser(testUser);
        assertEquals(2, subscriptions.size());
    }

    // ==================== Mixed Subscription Tests ====================

    @Test
    void subscribe_WithBothWebAndNative_ShouldSaveBoth() {
        // Act
        pushNotificationService.subscribe(webSubscriptionDto, testUser);
        pushNotificationService.subscribeNative(nativeSubscriptionDto, testUser);

        // Assert
        List<PushSubscription> subscriptions = subscriptionRepository.findPushSubscriptionByUser(testUser);
        assertEquals(2, subscriptions.size());

        long webCount = subscriptions.stream()
                .filter(s -> "web".equals(s.getPlatform()))
                .count();
        long nativeCount = subscriptions.stream()
                .filter(s -> !"web".equals(s.getPlatform()))
                .count();

        assertEquals(1, webCount);
        assertEquals(1, nativeCount);
    }

    @Test
    void subscribe_UserWithMultipleDevices_ShouldTrackAll() {
        // Arrange
        NativeSubscriptionDto iosSubscription = new NativeSubscriptionDto();
        iosSubscription.setToken("ios-token-789");
        iosSubscription.setPlatform("ios");

        SubscriptionDto secondWebSub = new SubscriptionDto();
        secondWebSub.setEndpoint("https://push.example.com/subscription/different");
        KeyDto keys2 = new KeyDto();
        keys2.setP256dh("different-key");
        keys2.setAuth("different-auth");
        secondWebSub.setKeys(keys2);

        // Act
        pushNotificationService.subscribe(webSubscriptionDto, testUser);
        pushNotificationService.subscribeNative(nativeSubscriptionDto, testUser);
        pushNotificationService.subscribeNative(iosSubscription, testUser);
        pushNotificationService.subscribe(secondWebSub, testUser);

        // Assert
        List<PushSubscription> subscriptions = subscriptionRepository.findPushSubscriptionByUser(testUser);
        assertEquals(4, subscriptions.size());
    }

    // ==================== Send Notification Tests (Web Push) ====================

    @Test
    void sendToUser_WithNoSubscriptions_ShouldNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() ->
                pushNotificationService.sendToUser(testUser, "Test Title", "Test Body")
        );

        // Verify no push attempts were made
        verifyNoInteractions(webPushService);
    }

    @Test
    void sendToUser_WhenWebPushFails410_ShouldDeleteSubscription() throws Exception {
        // Arrange
        pushNotificationService.subscribe(webSubscriptionDto, testUser);

        IOException exception = new IOException("410 Gone");
        doThrow(exception).when(webPushService).send(any(Notification.class), eq(Encoding.AES128GCM));

        // Act
        pushNotificationService.sendToUser(testUser, "Test Title", "Test Body");

        // Assert - subscription should be deleted due to 410 error
        List<PushSubscription> subscriptions = subscriptionRepository.findPushSubscriptionByUser(testUser);
        assertEquals(0, subscriptions.size());
    }

    @Test
    void sendToUser_WhenWebPushFails404_ShouldDeleteSubscription() throws Exception {
        // Arrange
        pushNotificationService.subscribe(webSubscriptionDto, testUser);

        IOException exception = new IOException("404 Not Found");
        doThrow(exception).when(webPushService).send(any(Notification.class), eq(Encoding.AES128GCM));

        // Act
        pushNotificationService.sendToUser(testUser, "Test Title", "Test Body");

        // Assert - subscription should be deleted due to 404 error
        List<PushSubscription> subscriptions = subscriptionRepository.findPushSubscriptionByUser(testUser);
        assertEquals(0, subscriptions.size());
    }

    @Test
    void sendToUser_WhenWebPushFailsOtherError_ShouldKeepSubscription() throws Exception {
        // Arrange
        pushNotificationService.subscribe(webSubscriptionDto, testUser);

        IOException exception = new IOException("500 Internal Server Error");
        doThrow(exception).when(webPushService).send(any(Notification.class));

        // Act
        pushNotificationService.sendToUser(testUser, "Test Title", "Test Body");

        // Assert - subscription should NOT be deleted for other errors
        List<PushSubscription> subscriptions = subscriptionRepository.findPushSubscriptionByUser(testUser);
        assertEquals(1, subscriptions.size());
    }

    @Test
    void sendToUser_WithMultipleWebSubscriptions_ShouldSendToAll() throws Exception {
        // Arrange
        SubscriptionDto secondWebSub = new SubscriptionDto();
        secondWebSub.setEndpoint("https://push.example.com/different");
        KeyDto keys = new KeyDto();
        keys.setP256dh(WebPushTestKeys.p256dh());
        keys.setAuth(WebPushTestKeys.auth());
        secondWebSub.setKeys(keys);

        pushNotificationService.subscribe(webSubscriptionDto, testUser);
        pushNotificationService.subscribe(secondWebSub, testUser);

        when(webPushService.send(any(Notification.class))).thenReturn(null);

        // Act
        pushNotificationService.sendToUser(testUser, "Test Title", "Test Body");

        // Assert - should send twice (once per subscription)
        verify(webPushService, times(2)).send(any(nl.martijndwars.webpush.Notification.class), eq(Encoding.AES128GCM));
    }

    @Test
    void sendToUser_WithEmptyTitleAndBody_ShouldStillProcess() throws Exception {
        // Arrange
        pushNotificationService.subscribe(webSubscriptionDto, testUser);
        when(webPushService.send(any(Notification.class))).thenReturn(null);

        // Act & Assert
        assertDoesNotThrow(() ->
                pushNotificationService.sendToUser(testUser, "", "")
        );

        verify(webPushService, times(1)).send(any(nl.martijndwars.webpush.Notification.class), eq(Encoding.AES128GCM));
    }

    @Test
    void sendToUser_WithSpecialCharacters_ShouldHandleCorrectly() throws Exception {
        // Arrange
        pushNotificationService.subscribe(webSubscriptionDto, testUser);
        when(webPushService.send(any(Notification.class))).thenReturn(null);

        String title = "Test 🔔 Notification";
        String body = "Special: €, £, ¥, 你好";

        // Act & Assert
        assertDoesNotThrow(() ->
                pushNotificationService.sendToUser(testUser, title, body)
        );

        verify(webPushService, times(1)).send(any(nl.martijndwars.webpush.Notification.class), eq(Encoding.AES128GCM));
    }

    // ==================== Send Notification Tests (Native Push) ====================

    @Test
    void sendToUser_WithNativeSubscription_ShouldAttemptFCMSend() throws FirebaseMessagingException {
        // Arrange
        pushNotificationService.subscribeNative(nativeSubscriptionDto, testUser);

        // Use MockedStatic to mock FirebaseMessaging.getInstance()
        try (MockedStatic<FirebaseMessaging> mockedFirebase = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging mockMessaging = mock(FirebaseMessaging.class);
            mockedFirebase.when(FirebaseMessaging::getInstance).thenReturn(mockMessaging);

            when(mockMessaging.send(any(Message.class))).thenReturn("message-id-123");

            // Act
            pushNotificationService.sendToUser(testUser, "Test Title", "Test Body");

            // Assert
            verify(mockMessaging, times(1)).send(any(Message.class));

            // Subscription should still exist
            List<PushSubscription> subscriptions = subscriptionRepository.findPushSubscriptionByUser(testUser);
            assertEquals(1, subscriptions.size());
        }
    }

    @Test
    void sendToUser_WithMixedSubscriptions_ShouldSendToBoth() throws Exception {
        // Arrange
        pushNotificationService.subscribe(webSubscriptionDto, testUser);
        pushNotificationService.subscribeNative(nativeSubscriptionDto, testUser);

        when(webPushService.send(any(Notification.class))).thenReturn(null);

        try (MockedStatic<FirebaseMessaging> mockedFirebase = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging mockMessaging = mock(FirebaseMessaging.class);
            mockedFirebase.when(FirebaseMessaging::getInstance).thenReturn(mockMessaging);
            when(mockMessaging.send(any(Message.class))).thenReturn("message-id");

            // Act
            pushNotificationService.sendToUser(testUser, "Test Title", "Test Body");

            // Assert
            verify(webPushService, times(1)).send(any(nl.martijndwars.webpush.Notification.class), eq(Encoding.AES128GCM));
            verify(mockMessaging, times(1)).send(any(Message.class));
        }
    }

//    // ==================== Send to All Tests ====================
//
//    @Test
//    void sendNotificationToAll_WithNoSubscriptions_ShouldNotThrowException() {
//        // Act & Assert
//        assertDoesNotThrow(() ->
//                pushNotificationService.sendNotificationToAll("Test", "Message")
//        );
//
//        verifyNoInteractions(webPushService);
//    }
//
//    @Test
//    void sendNotificationToAll_WithMultipleUsers_ShouldSendToAll() throws Exception {
//        // Arrange
//        ApplicationUser user2 = new ApplicationUser();
//        user2.setEmail("user2@example.com");
//        user2.setPassword("password");
//        user2 = userRepository.save(user2);
//
//        ApplicationUser user3 = new ApplicationUser();
//        user3.setEmail("user3@example.com");
//        user3.setPassword("password");
//        user3 = userRepository.save(user3);
//
//        pushNotificationService.subscribe(webSubscriptionDto, testUser);
//        pushNotificationService.subscribeNative(nativeSubscriptionDto, user2);
//
//        SubscriptionDto webSub2 = new SubscriptionDto();
//        webSub2.setEndpoint("https://push.example.com/user3");
//        KeyDto keys = new KeyDto();
//        keys.setP256dh("user3-key");
//        keys.setAuth("user3-auth");
//        webSub2.setKeys(keys);
//        pushNotificationService.subscribe(webSub2, user3);
//
//        doNothing().when(webPushService).send(any(Notification.class));
//
//        try (MockedStatic<FirebaseMessaging> mockedFirebase = mockStatic(FirebaseMessaging.class)) {
//            FirebaseMessaging mockMessaging = mock(FirebaseMessaging.class);
//            mockedFirebase.when(FirebaseMessaging::getInstance).thenReturn(mockMessaging);
//            when(mockMessaging.send(any(Message.class))).thenReturn("message-id");
//
//            // Act
//            pushNotificationService.sendNotificationToAll("Broadcast", "Message to all");
//
//            // Assert
//            verify(webPushService, times(2)).send(any(Notification.class)); // 2 web subscriptions
//            verify(mockMessaging, times(1)).send(any(Message.class)); // 1 native subscription
//        }
//    }

    // ==================== Data Integrity Tests ====================

    @Test
    void subscribe_ShouldPersistAcrossTransactions() {
        // Act
        pushNotificationService.subscribe(webSubscriptionDto, testUser);

        // Assert - force flush to ensure DB persistence
        subscriptionRepository.flush();
        List<PushSubscription> subscriptions = subscriptionRepository.findPushSubscriptionByUser(testUser);

        assertEquals(1, subscriptions.size());
        assertNotNull(subscriptions.get(0).getId());
    }

    @Test
    void subscribe_ShouldMaintainUserRelationship() {
        // Act
        pushNotificationService.subscribe(webSubscriptionDto, testUser);

        // Assert
        List<PushSubscription> subscriptions = subscriptionRepository.findPushSubscriptionByUser(testUser);
        PushSubscription saved = subscriptions.get(0);

        assertNotNull(saved.getUser());
        assertEquals(testUser.getId(), saved.getUser().getId());
        assertEquals(testUser.getEmail(), saved.getUser().getEmail());
    }

    @Test
    void findPushSubscriptionByUser_AfterMultipleSubscriptions_ShouldReturnAll() {
        // Arrange
        SubscriptionDto secondWebSub = new SubscriptionDto();
        secondWebSub.setEndpoint("https://push.example.com/different");
        KeyDto keys = new KeyDto();
        keys.setP256dh("different-key");
        keys.setAuth("different-auth");
        secondWebSub.setKeys(keys);

        // Act
        pushNotificationService.subscribe(webSubscriptionDto, testUser);
        pushNotificationService.subscribe(secondWebSub, testUser);
        pushNotificationService.subscribeNative(nativeSubscriptionDto, testUser);

        // Assert
        List<PushSubscription> subscriptions = subscriptionRepository.findPushSubscriptionByUser(testUser);
        assertEquals(3, subscriptions.size());
    }
}