package com.smartroute.smartroute1.websocket;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.exception.NotFoundException;
import com.smartroute.smartroute1.security.JwtTokenizer;
import com.smartroute.smartroute1.service.FriendshipService;
import com.smartroute.smartroute1.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final JwtTokenizer jwtTokenizer;
    private final FriendshipService friendshipService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        LOGGER.info("beforeHandshake");
        try {
            URI uri = request.getURI();

            // Parsing query params via Spring:
            MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();

            String rawToken = params.getFirst("token");
            String rawFriendId = params.getFirst("friendId");

            if (rawToken == null || rawFriendId == null) {
                LOGGER.error("token or friendId is null");
                return false;
            }

            // URL decode
            String token = URLDecoder.decode(rawToken, StandardCharsets.UTF_8);

            // parse token
            if (!token.startsWith("Bearer ")) {
                LOGGER.error("Bearer token is invalid");
                return false;
            }
            token = token.substring(7);
            if (token.isEmpty()) {
                LOGGER.error("Bearer token is empty");
                return false;
            }

            // Check for valid token
            String extractedUser = jwtTokenizer.extractUsernameFromVerificationToken(token);
            if (extractedUser == null) {
                LOGGER.error("Bearer token is empty");
                return false;
            }

            String friendId = URLDecoder.decode(rawFriendId, StandardCharsets.UTF_8);

            // Check that socket user is not the same as friendId
            if (extractedUser.equals(friendId)) {
                LOGGER.error("UserId cannot be the same as friendId");
                return false;
            }

            // Check that these users exist and are friends
            try {
                if (!friendshipService.areFriends(extractedUser, friendId)) {
                    LOGGER.error("User {} and User {} are not friends", extractedUser, friendId);
                    return false;
                }
            } catch (NotFoundException e) {
                LOGGER.error("One of the users was not found");
                return false;
            }

            attributes.put("userId", extractedUser);
            attributes.put("friendId", friendId);
            LOGGER.info("Handshake successful");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {

    }
}
