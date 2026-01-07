package com.smartroute.smartroute1.websocket;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.security.JwtTokenizer;
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
    private final UserService userService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
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
        String friendId = URLDecoder.decode(rawFriendId, StandardCharsets.UTF_8);

        // Check for valid token
        String extractedUser = jwtTokenizer.extractUsernameFromVerificationToken(token);
        if (extractedUser == null) {
            return false;
        }

        // Check that socket user is not the same as friendId
        if (extractedUser.equals(friendId)) {
            return false;
        }

        // Check that these users exist
        ApplicationUser user = userService.findApplicationUserByEmail(extractedUser);
        ApplicationUser friend = userService.findApplicationUserByEmail(friendId);
        if (user == null || friend == null) {
            return false;
        }

        // TODO: Check that they are friends


        attributes.put("userId", extractedUser);
        attributes.put("friendId", friendId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {

    }
}
