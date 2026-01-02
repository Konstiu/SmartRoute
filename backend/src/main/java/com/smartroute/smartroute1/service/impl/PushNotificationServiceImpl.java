package com.smartroute.smartroute1.service.impl;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.smartroute.smartroute1.endpoint.dto.subscription.NativeSubscriptionDto;
import com.smartroute.smartroute1.endpoint.dto.subscription.SubscriptionDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.PushSubscription;
import com.smartroute.smartroute1.repository.PushSubscriptionRepository;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import nl.martijndwars.webpush.PushService;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
public class PushNotificationServiceImpl {

    private final PushSubscriptionRepository subscriptionRepository;
    private final PushService webPushService;

    public PushNotificationServiceImpl(
            PushSubscriptionRepository subscriptionRepository,
            @Value("${vapid.public.key}") String publicKey,
            @Value("${vapid.private.key}") String privateKey,
            @Value("${vapid.subject}") String subject,
            @Value("${firebase.service.account.path:#{null}}") Resource firebaseConfig) // ← Make optional
            throws GeneralSecurityException, IOException {

        this.subscriptionRepository = subscriptionRepository;

        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        this.webPushService = new PushService(publicKey, privateKey, subject);

        // Initialize Firebase
        //        if (FirebaseApp.getApps().isEmpty()) {
        //            FirebaseOptions options = FirebaseOptions.builder()
        //                    .setCredentials(GoogleCredentials.fromStream(firebaseConfig.getInputStream()))
        //                    .build();
        //            FirebaseApp.initializeApp(options);
        //        }
    }

    // Subscribe web push
    public void subscribe(SubscriptionDto subscriptionDto, ApplicationUser user) {
        PushSubscription subscription = new PushSubscription();
        subscription.setUser(user);
        subscription.setPlatform("web");
        subscription.setEndpoint(subscriptionDto.getEndpoint());
        subscription.setP256dh(subscriptionDto.getKeys().getP256dh());
        subscription.setAuth(subscriptionDto.getKeys().getAuth());
        subscriptionRepository.save(subscription);
    }

    // Subscribe native push
    public void subscribeNative(NativeSubscriptionDto dto, ApplicationUser user) {
        PushSubscription subscription = new PushSubscription();
        subscription.setUser(user);
        subscription.setPlatform(dto.getPlatform());
        subscription.setFcmToken(dto.getToken());
        subscriptionRepository.save(subscription);
    }

    // Send to specific user (works for both web and native)
    public void senDtoUser(ApplicationUser user, String title, String body) {
        List<PushSubscription> subscriptions = subscriptionRepository.findPushSubscriptionByUser(user);

        for (PushSubscription sub : subscriptions) {
            if ("web".equals(sub.getPlatform())) {
                sendWebPush(sub, title, body);
            } else {
                sendNativePush(sub, title, body);
            }
        }
    }

    // Send to all users
    public void sendNotificationToAll(String title, String body) {
        List<PushSubscription> subscriptions = subscriptionRepository.findAll();

        for (PushSubscription sub : subscriptions) {
            if ("web".equals(sub.getPlatform())) {
                sendWebPush(sub, title, body);
            } else {
                sendNativePush(sub, title, body);
            }
        }
    }

    // Web Push
    private void sendWebPush(PushSubscription sub, String title, String body) {
        String payload = String.format(
                "{\"notification\":{\"title\":\"%s\",\"body\":\"%s\"}}",
                title, body
        );

        try {
            nl.martijndwars.webpush.Subscription subscription =
                    new nl.martijndwars.webpush.Subscription(
                            sub.getEndpoint(),
                            new nl.martijndwars.webpush.Subscription.Keys(
                                    sub.getP256dh(),
                                    sub.getAuth()
                            )
                    );

            nl.martijndwars.webpush.Notification notification =
                    new nl.martijndwars.webpush.Notification(subscription, payload);
            webPushService.send(notification);

        } catch (GeneralSecurityException | IOException | JoseException e) {
            if (e.getMessage() != null
                    && (e.getMessage().contains("410") || e.getMessage().contains("404"))) {
                subscriptionRepository.delete(sub);
            }
            System.err.println("Error sending web push: " + e.getMessage());
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // Native Push (FCM)
    private void sendNativePush(PushSubscription sub, String title, String body) {
        try {
            Message message = Message.builder()
                    .setToken(sub.getFcmToken())
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("Successfully sent FCM message: " + response);

        } catch (FirebaseMessagingException e) {
            String errorCode = String.valueOf(e.getErrorCode());
            if ("NOT_FOUND".equals(errorCode)
                    || "INVALID_ARGUMENT".equals(errorCode)
                    || "UNREGISTERED".equals(errorCode)) {
                subscriptionRepository.delete(sub);
            }
            System.err.println("Error sending FCM: " + e.getMessage());
        }
    }
}