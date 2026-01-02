package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.subscription.NativeSubscriptionDto;
import com.smartroute.smartroute1.endpoint.dto.subscription.SubscriptionDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.impl.PushNotificationServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;

import javax.annotation.security.PermitAll;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class PushNotificationEndpoint {

    private final PushNotificationServiceImpl pushNotificationService;
    private final UserRepository userRepository;


    public PushNotificationEndpoint(PushNotificationServiceImpl pushNotificationService, UserRepository userRepository) {
        this.pushNotificationService = pushNotificationService;
        this.userRepository = userRepository;
    }

    // Web push subscription endpoint (matches your Angular service)
    @PostMapping("/subscribe")
    @Secured("ROLE_USER")
    public ResponseEntity<?> subscribeWeb(@RequestBody SubscriptionDto subscriptionDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ApplicationUser user = userRepository.findUserByEmail(authentication.getName());

        try {
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }

            pushNotificationService.subscribe(subscriptionDto, user);
            return ResponseEntity.ok(Map.of(
                    "message", "Web push subscription successful",
                    "status", "subscribed"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // Native push subscription endpoint (matches your Angular service)
    @PostMapping("/subscribe-native")
    @Secured("ROLE_USER")
    public ResponseEntity<?> subscribeNative(@RequestBody NativeSubscriptionDto nativeSubscriptionDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ApplicationUser user = userRepository.findUserByEmail(authentication.getName());
        try {
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }
            pushNotificationService.subscribeNative(nativeSubscriptionDto, user);
            return ResponseEntity.ok(Map.of(
                    "message", "Native push subscription successful",
                    "platform", nativeSubscriptionDto.getPlatform(),
                    "status", "subscribed"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // Test endpoint: Send notification to yourself
    @PostMapping("/test/me")
    @Secured("ROLE_USER")
    public ResponseEntity<?> testNotificationToMe(@RequestBody(required = false) Map<String, String> payload) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ApplicationUser user = userRepository.findUserByEmail(authentication.getName());
        try {
            String title = payload != null && payload.containsKey("title")
                    ? payload.get("title")
                    : "Test Notification";
            String body = payload != null && payload.containsKey("body")
                    ? payload.get("body")
                    : "This is a test notification from SmartRoute! 🎉";

            pushNotificationService.senDtoUser(user, title, body);
            return ResponseEntity.ok(Map.of(
                    "message", "Test notification sent successfully",
                    "title", title,
                    "body", body,
                    "status", "sent"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // Test endpoint: Quick test with default message
    @GetMapping("/test/quick")
    @Secured("ROLE_USER")
    public ResponseEntity<?> quickTest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ApplicationUser user = userRepository.findUserByEmail(authentication.getName());
        try {
            pushNotificationService.senDtoUser(
                    user,
                    "Quick Test",
                    "If you see this, push notifications are working! 🎉"
            );
            return ResponseEntity.ok(Map.of(
                    "message", "Quick test notification sent",
                    "status", "sent"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // Test endpoint: Send to all users (admin only - add proper authorization)
    @PostMapping("/test/broadcast")
    @Secured("ROLE_USER")
    public ResponseEntity<?> testBroadcast(@RequestBody(required = false) Map<String, String> payload) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ApplicationUser user = userRepository.findUserByEmail(authentication.getName());

        try {
            String title = payload != null && payload.containsKey("title")
                    ? payload.get("title")
                    : "Broadcast Test";
            String body = payload != null && payload.containsKey("body")
                    ? payload.get("body")
                    : "This is a test broadcast notification!";

            pushNotificationService.sendNotificationToAll(title, body);
            return ResponseEntity.ok(Map.of(
                    "message", "Broadcast notification sent to all users",
                    "title", title,
                    "body", body,
                    "status", "sent"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // Check subscription status
    @GetMapping("/status")
    @Secured("ROLE_USER")
    public ResponseEntity<?> checkSubscriptionStatus(@AuthenticationPrincipal ApplicationUser user) {
        try {
            // You'll need to add this method to your service
            // boolean hasSubscriptions = pushNotificationService.hasSubscriptions(user);

            return ResponseEntity.ok(Map.of(
                    "subscribed", true, // Replace with actual check
                    "message", "Subscription status retrieved"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}