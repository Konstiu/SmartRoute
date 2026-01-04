package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.subscription.MessageDto;
import com.smartroute.smartroute1.endpoint.dto.subscription.NativeSubscriptionDto;
import com.smartroute.smartroute1.endpoint.dto.subscription.SubscriptionDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Friendship;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.FriendshipService;
import com.smartroute.smartroute1.service.PushNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@Slf4j
@Tag(
        name = "Push Notifications",
        description = "Endpoints to manage push notification subscriptions (web + native) and send test notifications."
)
public class PushNotificationEndpoint {

    private final PushNotificationService pushNotificationService;
    private final UserRepository userRepository;
    private final FriendshipService friendshipService;

    public PushNotificationEndpoint(PushNotificationService pushNotificationService, UserRepository userRepository, FriendshipService friendshipService) {
        this.pushNotificationService = pushNotificationService;
        this.userRepository = userRepository;
        this.friendshipService = friendshipService;
    }

    @PostMapping("/subscribe")
    @Secured("ROLE_USER")
    @Operation(
            summary = "Subscribe for Web Push notifications",
            description = "Stores/updates the authenticated user's **Web Push** subscription (e.g., browser Service Worker push subscription)."
    )
    public ResponseEntity<?> subscribeWeb(@RequestBody SubscriptionDto subscriptionDto) {
        log.info("POST /api/v1/notifications/subscribe {}", subscriptionDto);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ApplicationUser user = userRepository.findUserByEmail(authentication.getName());
        pushNotificationService.subscribe(subscriptionDto, user);
        return ResponseEntity.ok(Map.of(
                "message", "Web push subscription successful",
                "status", "subscribed"
        ));
    }

    @PostMapping("/subscribe-native")
    @Secured("ROLE_USER")
    @Operation(
            summary = "Subscribe for Native push notifications",
            description = "Stores/updates the authenticated user's **native push** token/subscription (e.g., iOS/Android token)."
    )
    public ResponseEntity<?> subscribeNative(@RequestBody NativeSubscriptionDto nativeSubscriptionDto) {
        log.info("POST /api/v1/notifications/subscribe-native {}", nativeSubscriptionDto);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ApplicationUser user = userRepository.findUserByEmail(authentication.getName());
        pushNotificationService.subscribeNative(nativeSubscriptionDto, user);
        return ResponseEntity.ok(Map.of(
                "message", "Native push subscription successful",
                "platform", nativeSubscriptionDto.getPlatform(),
                "status", "subscribed"
        ));
    }

    //@PostMapping("/test/me") // Disabled, for testing only
    @Secured("ROLE_USER")
    @Operation(
            summary = "[DEV] Send a test notification to the current user",
            description = "Sends a push notification to the authenticated user. Only available in the **dev** profile."
    )
    public ResponseEntity<?> testNotificationToMe(@RequestBody MessageDto payload) {
        log.info("POST /api/v1/notifications/test/me {}", payload);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ApplicationUser user = userRepository.findUserByEmail(authentication.getName());
        String title = payload.getTitle();
        String body = payload.getBody();
        pushNotificationService.sendToUser(user, title, body);
        return ResponseEntity.ok(Map.of(
                "message", "Notification sent successfully",
                "title", title,
                "body", body,
                "status", "sent"
        ));
    }

    @PostMapping("/friend")
    @Secured("ROLE_USER")
    @Operation(
            summary = "Send a test notification to all friends",
            description = "Fetches the authenticated user's friends and sends the notification to each friend."
    )
    public ResponseEntity<?> testNotificationToFriend(@RequestBody MessageDto payload) {
        log.info("POST /api/v1/notifications/friend {}", payload);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ApplicationUser user = userRepository.findUserByEmail(authentication.getName());
        String title = payload.getTitle();
        String body = payload.getBody();
        List<Friendship> l = friendshipService.getFriends(user.getEmail());
        //l.forEach(e -> log.info(e.getReceiver().getEmail()));
        l.forEach(e -> pushNotificationService.sendToUser(e.getReceiver(), title, body));

        return ResponseEntity.ok(Map.of(
                "message", "Notification sent successfully",
                "title", title,
                "body", body,
                "status", "sent"
        ));
    }

    //@GetMapping("/test/quick") // Disabled, for testing only
    @Secured("ROLE_USER")
    @Operation(
            summary = "[DEV] Quick test notification",
            description = "Sends a quick hardcoded test notification to the authenticated user. Only available in the **dev** profile."
    )
    public ResponseEntity<?> quickTest() {
        log.info("POST /api/v1/notifications/test/quick");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ApplicationUser user = userRepository.findUserByEmail(authentication.getName());
        pushNotificationService.sendToUser(
                user,
                "Quick Test",
                "If you see this, push notifications are working!"
        );
        return ResponseEntity.ok(Map.of(
                "message", "Quick test notification sent",
                "status", "sent"
        ));
    }
}