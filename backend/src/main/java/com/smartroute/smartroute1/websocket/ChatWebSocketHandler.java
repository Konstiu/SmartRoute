package com.smartroute.smartroute1.websocket;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket handler for managing chat connections and heartbeat mechanism.
 */
public class ChatWebSocketHandler extends TextWebSocketHandler {
    // Map: sessionKey -> Set of WebSocketSessions
    private static final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    // Map: WebSocketSession -> ScheduledFuture (for timeout tasks)
    private static final Map<WebSocketSession, ScheduledFuture<?>> heartbeatTimeouts = new ConcurrentHashMap<>();

    // Scheduler for heartbeat timeouts
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    private static final int HEARTBEAT_TIMEOUT_SECONDS = 10;

    /**
     * Handles the establishment of a new WebSocket connection.
     * Adds the session to the tracking map and starts the heartbeat timer.
     *
     * @param session the newly established WebSocket session
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = (String) session.getAttributes().get("userId");
        String friendId = (String) session.getAttributes().get("friendId");
        String sessionKey = userId + "_" + friendId;

        // Generate a unique socket ID and store it in session attributes
        String socketId = UUID.randomUUID().toString();
        session.getAttributes().put("socketId", socketId);

        // Get or create the set for this sessionKey
        sessions.computeIfAbsent(sessionKey, k -> new CopyOnWriteArraySet<>()).add(session);

        // Start heartbeat timer for this session
        scheduleHeartbeatTimeout(session);

        // Send welcome message with socket ID
        sendWelcomeMessage(session);
    }

    /**
     * Handles the closing of a WebSocket connection.
     * Removes the session from the tracking map and cancels the heartbeat timer.
     *
     * @param session the WebSocket session that is closing
     * @param status  the close status
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        String friendId = (String) session.getAttributes().get("friendId");
        String sessionKey = userId + "_" + friendId;

        Set<WebSocketSession> sessionSet = sessions.get(sessionKey);
        if (sessionSet != null) {
            sessionSet.remove(session);
            // Remove the key if no sessions remain
            if (sessionSet.isEmpty()) {
                sessions.remove(sessionKey);
            }
        }

        // Stop and remove heartbeat timer
        cancelHeartbeatTimeout(session);
    }

    /**
     * Sends a welcome message with the generated socket id to the client upon connection establishment.
     *
     * @param session the WebSocket session
     */
    private void sendWelcomeMessage(WebSocketSession session) {
        try {
            session.sendMessage(new TextMessage("WELCOME: " + session.getAttributes().get("socketId")));
        } catch (IOException e) {
            // Ignore error
        }
    }

    /**
     * Handles incoming text messages.
     * Expects "ping" messages to respond with "pong".
     *
     * @param session the WebSocket session
     * @param message the incoming text message
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();

        if ("ping".equalsIgnoreCase(payload)) {
            // Send pong back
            try {
                session.sendMessage(new TextMessage("PONG"));
                // Reset heartbeat timer
                resetHeartbeatTimeout(session);
            } catch (IOException e) {
                // Error sending - close session
                closeSession(session);
            }
        }

        // ignore other messages
    }

    /**
     * Notifies all relevant devices with a "NEW_MESSAGE" event.
     *
     * @param receiverId the ID of the message receiver
     * @param senderId   the ID of the message sender
     */
    public static void notifyUser(String receiverId, String senderId, String senderSocketId) {
        String sessionKey = receiverId + "_" + senderId;
        String reverseSessionKey = senderId + "_" + receiverId;
        Set<WebSocketSession> sessionSet = sessions.get(sessionKey);
        Set<WebSocketSession> reverseSessionSet = sessions.get(reverseSessionKey);
        // concat
        if (reverseSessionSet != null) {
            if (sessionSet == null) {
                sessionSet = new CopyOnWriteArraySet<>();
            }
            sessionSet.addAll(reverseSessionSet);
        }

        if (sessionSet != null && !sessionSet.isEmpty()) {
            // Send message to all sessions of this user
            sessionSet.forEach(session -> {
                // Do not notify the sender's own socket
                var senderSocketIdAttr = session.getAttributes().get("socketId");
                if (senderSocketId != null && senderSocketId.equals(senderSocketIdAttr)) {
                    return;
                }
                if (session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage("NEW_MESSAGE"));
                    } catch (IOException e) {
                        // ignore error
                    }
                }
            });
        }
    }

    /**
     * Schedules a heartbeat timeout task for the given session.
     *
     * @param session the WebSocket session to schedule the timeout for
     */
    private void scheduleHeartbeatTimeout(WebSocketSession session) {
        ScheduledFuture<?> timeoutTask = scheduler.schedule(
            () -> {
                // Timeout reached - close session
                closeSession(session);
            },
            HEARTBEAT_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        );

        heartbeatTimeouts.put(session, timeoutTask);
    }

    /**
     * Resets the heartbeat timeout for the given session.
     *
     * @param session the WebSocket session to reset the timeout for
     */
    private void resetHeartbeatTimeout(WebSocketSession session) {
        // Stop old timer
        cancelHeartbeatTimeout(session);
        // Start new timer
        scheduleHeartbeatTimeout(session);
    }

    /**
     * Cancels the heartbeat timeout task for the given session.
     *
     * @param session the WebSocket session to cancel the timeout for
     */
    private void cancelHeartbeatTimeout(WebSocketSession session) {
        ScheduledFuture<?> timeoutTask = heartbeatTimeouts.remove(session);
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
        }
    }

    /**
     * Closes the given WebSocket session with a policy violation status.
     *
     * @param session the WebSocket session to close
     */
    private void closeSession(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Heartbeat timeout"));
            }
        } catch (IOException e) {
            // Ignore error on close
        }
    }
}