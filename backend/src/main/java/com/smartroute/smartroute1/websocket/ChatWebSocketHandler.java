package com.smartroute.smartroute1.websocket;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = (String) session.getAttributes().get("userId");
        String friendId = (String) session.getAttributes().get("friendId");

        String sessionKey = userId + "_" + friendId;

        sessions.put(sessionKey, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        String friendId = (String) session.getAttributes().get("friendId");

        String sessionKey = userId + "_" + friendId;

        sessions.remove(sessionKey);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {

    }

    public static void notifyUser(String userId, String friendId) {
        String sessionKey = userId + "_" + friendId;
        WebSocketSession session = sessions.get(sessionKey);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage("NEW_MESSAGE"));
            } catch (IOException ignored) {
                // ignore
            }
        }
    }


}
