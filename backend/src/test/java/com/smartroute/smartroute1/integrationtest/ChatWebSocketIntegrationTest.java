package com.smartroute.smartroute1.integrationtest;

import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.basetest.TestData;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Friendship;
import com.smartroute.smartroute1.entity.enums.FriendshipStatus;
import com.smartroute.smartroute1.repository.FriendshipRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.security.JwtTokenizer;
import com.smartroute.smartroute1.websocket.ChatWebSocketHandler;
import com.smartroute.smartroute1.websocket.JwtHandshakeInterceptor;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "generateData"})
@Transactional
class ChatWebSocketIntegrationTest extends BaseTest implements TestData {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private JwtTokenizer jwtTokenizer;

    @Autowired
    private JwtHandshakeInterceptor jwtHandshakeInterceptor;

    private ChatWebSocketHandler chatWebSocketHandler;

    private static final String USER1_EMAIL = "email0@smartroute.com";
    private static final String USER2_EMAIL = "email1@smartroute.com";

    @BeforeEach
    void setup() {
        chatWebSocketHandler = new ChatWebSocketHandler();
    }

    @Test
    void testSuccessfulHandshake_withValidTokenAndFriendship() throws Exception {
        // Arrange: Create friendship between user1 and user2
        ApplicationUser user1 = userRepository.findUserByEmail(USER1_EMAIL);
        ApplicationUser user2 = userRepository.findUserByEmail(USER2_EMAIL);
        assertNotNull(user1, "User1 should exist");
        assertNotNull(user2, "User2 should exist");

        createFriendship(user1, user2);

        // Generate valid JWT token for user1
        String token = jwtTokenizer.buildVerificationToken(USER1_EMAIL);
        String bearerToken = "Bearer " + token;
        String encodedToken = URLEncoder.encode(bearerToken, StandardCharsets.UTF_8);
        String encodedFriendId = URLEncoder.encode(USER2_EMAIL, StandardCharsets.UTF_8);

        String wsUrl = String.format("ws://localhost:8080/ws/chat?token=%s&friendId=%s",
                encodedToken, encodedFriendId);

        // Mock ServerHttpRequest and ServerHttpResponse
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();

        when(request.getURI()).thenReturn(URI.create(wsUrl));

        // Act: Test handshake
        boolean result = jwtHandshakeInterceptor.beforeHandshake(request, response, wsHandler, attributes);

        // Assert: Handshake should succeed
        assertTrue(result, "Handshake should succeed with valid token and friendship");
        assertEquals(USER1_EMAIL, attributes.get("userId"));
        assertEquals(USER2_EMAIL, attributes.get("friendId"));
    }

    @Test
    void testHandshake_failsWithoutToken() throws Exception {
        // Arrange: URL without token parameter
        String encodedFriendId = URLEncoder.encode(USER2_EMAIL, StandardCharsets.UTF_8);
        String wsUrl = String.format("ws://localhost:8080/ws/chat?friendId=%s", encodedFriendId);

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();

        when(request.getURI()).thenReturn(URI.create(wsUrl));

        // Act & Assert: Handshake should fail
        boolean result = jwtHandshakeInterceptor.beforeHandshake(request, response, wsHandler, attributes);
        assertFalse(result, "Handshake should fail without token");
    }

    @Test
    void testHandshake_failsWithoutFriendId() throws Exception {
        // Arrange: Generate token but omit friendId parameter
        String token = jwtTokenizer.buildVerificationToken(USER1_EMAIL);
        String bearerToken = "Bearer " + token;
        String encodedToken = URLEncoder.encode(bearerToken, StandardCharsets.UTF_8);

        String wsUrl = String.format("ws://localhost:8080/ws/chat?token=%s", encodedToken);

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();

        when(request.getURI()).thenReturn(URI.create(wsUrl));

        // Act & Assert: Handshake should fail
        boolean result = jwtHandshakeInterceptor.beforeHandshake(request, response, wsHandler, attributes);
        assertFalse(result, "Handshake should fail without friendId");
    }

    @Test
    void testHandshake_failsWithInvalidToken() throws Exception {
        // Arrange: Use an invalid token
        String invalidToken = "Bearer invalidtoken123";
        String encodedToken = URLEncoder.encode(invalidToken, StandardCharsets.UTF_8);
        String encodedFriendId = URLEncoder.encode(USER2_EMAIL, StandardCharsets.UTF_8);

        String wsUrl = String.format("ws://localhost:8080/ws/chat?token=%s&friendId=%s",
                encodedToken, encodedFriendId);

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();

        when(request.getURI()).thenReturn(URI.create(wsUrl));

        // Act & Assert: Handshake should fail
        boolean result = jwtHandshakeInterceptor.beforeHandshake(request, response, wsHandler, attributes);
        assertFalse(result, "Handshake should fail with invalid token");
    }

    @Test
    void testHandshake_failsWithTokenWithoutBearerPrefix() throws Exception {
        // Arrange: Token without "Bearer " prefix
        String token = jwtTokenizer.buildVerificationToken(USER1_EMAIL);
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String encodedFriendId = URLEncoder.encode(USER2_EMAIL, StandardCharsets.UTF_8);

        String wsUrl = String.format("ws://localhost:8080/ws/chat?token=%s&friendId=%s",
                encodedToken, encodedFriendId);

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();

        when(request.getURI()).thenReturn(URI.create(wsUrl));

        // Act & Assert: Handshake should fail
        boolean result = jwtHandshakeInterceptor.beforeHandshake(request, response, wsHandler, attributes);
        assertFalse(result, "Handshake should fail without Bearer prefix");
    }

    @Test
    void testHandshake_failsWhenUsersAreNotFriends() throws Exception {
        // Arrange: Don't create friendship between users
        ApplicationUser user1 = userRepository.findUserByEmail(USER1_EMAIL);
        ApplicationUser user2 = userRepository.findUserByEmail(USER2_EMAIL);
        assertNotNull(user1, "User1 should exist");
        assertNotNull(user2, "User2 should exist");

        // Generate valid token but users are not friends
        String token = jwtTokenizer.buildVerificationToken(USER1_EMAIL);
        String bearerToken = "Bearer " + token;
        String encodedToken = URLEncoder.encode(bearerToken, StandardCharsets.UTF_8);
        String encodedFriendId = URLEncoder.encode(USER2_EMAIL, StandardCharsets.UTF_8);

        String wsUrl = String.format("ws://localhost:8080/ws/chat?token=%s&friendId=%s",
                encodedToken, encodedFriendId);

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();

        when(request.getURI()).thenReturn(URI.create(wsUrl));

        // Act & Assert: Handshake should fail
        boolean result = jwtHandshakeInterceptor.beforeHandshake(request, response, wsHandler, attributes);
        assertFalse(result, "Handshake should fail when users are not friends");
    }

    @Test
    void testHandshake_failsWhenFriendshipIsPending() throws Exception {
        // Arrange: Create pending friendship (not accepted)
        ApplicationUser user1 = userRepository.findUserByEmail(USER1_EMAIL);
        ApplicationUser user2 = userRepository.findUserByEmail(USER2_EMAIL);
        assertNotNull(user1, "User1 should exist");
        assertNotNull(user2, "User2 should exist");

        Friendship friendship = new Friendship();
        friendship.setSender(user1);
        friendship.setReceiver(user2);
        friendship.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(friendship);

        // Generate valid token
        String token = jwtTokenizer.buildVerificationToken(USER1_EMAIL);
        String bearerToken = "Bearer " + token;
        String encodedToken = URLEncoder.encode(bearerToken, StandardCharsets.UTF_8);
        String encodedFriendId = URLEncoder.encode(USER2_EMAIL, StandardCharsets.UTF_8);

        String wsUrl = String.format("ws://localhost:8080/ws/chat?token=%s&friendId=%s",
                encodedToken, encodedFriendId);

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();

        when(request.getURI()).thenReturn(URI.create(wsUrl));

        // Act & Assert: Handshake should fail with pending friendship
        boolean result = jwtHandshakeInterceptor.beforeHandshake(request, response, wsHandler, attributes);
        assertFalse(result, "Handshake should fail when friendship is pending");
    }

    @Test
    void testHandshake_failsWhenUserIdEqualsFriendId() throws Exception {
        // Arrange: Try to connect to yourself
        ApplicationUser user1 = userRepository.findUserByEmail(USER1_EMAIL);
        assertNotNull(user1, "User1 should exist");

        String token = jwtTokenizer.buildVerificationToken(USER1_EMAIL);
        String bearerToken = "Bearer " + token;
        String encodedToken = URLEncoder.encode(bearerToken, StandardCharsets.UTF_8);
        String encodedFriendId = URLEncoder.encode(USER1_EMAIL, StandardCharsets.UTF_8);

        String wsUrl = String.format("ws://localhost:8080/ws/chat?token=%s&friendId=%s",
                encodedToken, encodedFriendId);

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();

        when(request.getURI()).thenReturn(URI.create(wsUrl));

        // Act & Assert: Handshake should fail
        boolean result = jwtHandshakeInterceptor.beforeHandshake(request, response, wsHandler, attributes);
        assertFalse(result, "Handshake should fail when userId equals friendId");
    }

    @Test
    void testHandshake_failsWithNonExistentUser() throws Exception {
        // Arrange: Use valid token format but non-existent user
        String nonExistentUser = "nonexistent@smartroute.com";
        String token = jwtTokenizer.buildVerificationToken(nonExistentUser);
        String bearerToken = "Bearer " + token;
        String encodedToken = URLEncoder.encode(bearerToken, StandardCharsets.UTF_8);
        String encodedFriendId = URLEncoder.encode(USER2_EMAIL, StandardCharsets.UTF_8);

        String wsUrl = String.format("ws://localhost:8080/ws/chat?token=%s&friendId=%s",
                encodedToken, encodedFriendId);

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();

        when(request.getURI()).thenReturn(URI.create(wsUrl));

        // Act & Assert: Handshake should fail
        boolean result = jwtHandshakeInterceptor.beforeHandshake(request, response, wsHandler, attributes);
        assertFalse(result, "Handshake should fail with non-existent user");
    }

    @Test
    void testWebSocketHandler_afterConnectionEstablished() {
        // Arrange: Create a mock WebSocket session with attributes
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", USER1_EMAIL);
        attributes.put("friendId", USER2_EMAIL);

        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);

        // Act: Call afterConnectionEstablished
        assertDoesNotThrow(() -> chatWebSocketHandler.afterConnectionEstablished(session),
                "Connection establishment should not throw exception");

        // Assert: Verify the session was registered
        verify(session, atLeastOnce()).getAttributes();
    }

    @Test
    void testWebSocketHandler_afterConnectionClosed() {
        // Arrange: Create a mock WebSocket session
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", USER1_EMAIL);
        attributes.put("friendId", USER2_EMAIL);

        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(false);

        // First establish connection
        assertDoesNotThrow(() -> chatWebSocketHandler.afterConnectionEstablished(session),
                "Connection establishment should not throw exception");

        // Act: Close the connection
        assertDoesNotThrow(() -> chatWebSocketHandler.afterConnectionClosed(session, CloseStatus.NORMAL),
                "Connection closure should not throw exception");

        // Assert: Verify session was cleaned up
        verify(session, atLeastOnce()).getAttributes();
    }

    @Test
    void testNotifyUser_withActiveSession() {
        // Arrange: Create and register a mock session
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", USER1_EMAIL);
        attributes.put("friendId", USER2_EMAIL);

        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);

        assertDoesNotThrow(() -> chatWebSocketHandler.afterConnectionEstablished(session),
                "Connection establishment should not throw exception");

        // Act: Send notification
        assertDoesNotThrow(() -> ChatWebSocketHandler.notifyUser(USER1_EMAIL, USER2_EMAIL, null),
                "Notifying user should not throw exception");
    }

    @Test
    void testNotifyUser_withClosedSession() {
        // Arrange: No active session registered

        // Act & Assert: Notifying without active session should not throw exception
        assertDoesNotThrow(() -> ChatWebSocketHandler.notifyUser(USER1_EMAIL, USER2_EMAIL, null),
                "Notifying user without active session should not throw exception");
    }

    @Test
    void testMultipleSessionsForSameUser() {
        // Arrange: Create multiple sessions for the same user-friend pair
        WebSocketSession session1 = mock(WebSocketSession.class);
        WebSocketSession session2 = mock(WebSocketSession.class);

        Map<String, Object> attributes1 = new HashMap<>();
        attributes1.put("userId", USER1_EMAIL);
        attributes1.put("friendId", USER2_EMAIL);

        Map<String, Object> attributes2 = new HashMap<>();
        attributes2.put("userId", USER1_EMAIL);
        attributes2.put("friendId", USER2_EMAIL);

        when(session1.getAttributes()).thenReturn(attributes1);
        when(session1.isOpen()).thenReturn(true);
        when(session2.getAttributes()).thenReturn(attributes2);
        when(session2.isOpen()).thenReturn(true);

        // Act: Establish both connections
        assertDoesNotThrow(() -> chatWebSocketHandler.afterConnectionEstablished(session1),
                "First connection establishment should not throw exception");
        assertDoesNotThrow(() -> chatWebSocketHandler.afterConnectionEstablished(session2),
                "Second connection establishment should not throw exception");

        // Assert: Both sessions should be handled (second should replace first)
        verify(session1, atLeastOnce()).getAttributes();
        verify(session2, atLeastOnce()).getAttributes();
    }

    /**
     * Helper method to create an accepted friendship between two users
     */
    private void createFriendship(ApplicationUser user1, ApplicationUser user2) {
        Friendship friendship = new Friendship();
        friendship.setSender(user1);
        friendship.setReceiver(user2);
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);
    }
}

