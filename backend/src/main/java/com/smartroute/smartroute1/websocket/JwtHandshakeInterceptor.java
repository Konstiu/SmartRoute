package com.smartroute.smartroute1.websocket;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.exception.NotFoundException;
import com.smartroute.smartroute1.security.JwtTokenizer;
import com.smartroute.smartroute1.service.FriendshipService;
import com.smartroute.smartroute1.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenizer jwtTokenizer;
    private final FriendshipService friendshipService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        try {
            URI uri = request.getURI();

            // Parsing query params via Spring:
            MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();

            String rawToken = params.getFirst("token");
            String rawFriendId = params.getFirst("friendId");

            if (rawToken == null || rawFriendId == null) {
                return false;
            }

            // URL decode
            String token = URLDecoder.decode(rawToken, StandardCharsets.UTF_8);

            // parse token
            if (!token.startsWith("Bearer ")) {
                return false;
            }
            token = token.substring(7);
            if (token.isEmpty()) {
                return false;
            }

            // Check for valid token
            String extractedUser = jwtTokenizer.extractUsernameFromVerificationToken(token);
            if (extractedUser == null) {
                return false;
            }

            String friendId = URLDecoder.decode(rawFriendId, StandardCharsets.UTF_8);

            // Check that socket user is not the same as friendId
            if (extractedUser.equals(friendId)) {
                return false;
            }

            // Check that these users exist and are friends
            try {
                if (!friendshipService.areFriends(extractedUser, friendId)) {
                    return false;
                }
            } catch (NotFoundException e) {
                return false;
            }

            attributes.put("userId", extractedUser);
            attributes.put("friendId", friendId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {

    }
}
