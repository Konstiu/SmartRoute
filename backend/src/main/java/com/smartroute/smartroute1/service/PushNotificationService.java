package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.subscription.NativeSubscriptionDto;
import com.smartroute.smartroute1.endpoint.dto.subscription.SubscriptionDto;
import com.smartroute.smartroute1.entity.ApplicationUser;

/**
 * Service for managing push notification subscriptions and sending push notifications
 * to users.
 *
 * <p>This service supports both:
 * <ul>
 *   <li>Web Push notifications (browser-based)</li>
 *   <li>Native Push notifications (iOS / Android)</li>
 * </ul>
 *
 * <p>The actual delivery mechanism and persistence of subscriptions are handled by
 * the implementing class.
 */
public interface PushNotificationService {

    /**
     * Registers or updates a web push subscription for the given user.
     *
     * <p>If the user already has an existing web push subscription, it will be
     * replaced with the provided one.
     *
     * @param subscriptionDto the web push subscription data (endpoint, keys, etc.)
     * @param user            the authenticated user the subscription belongs to
     */
    void subscribe(SubscriptionDto subscriptionDto, ApplicationUser user);

    /**
     * Registers or updates a native push subscription for the given user.
     *
     * <p>This is typically used for mobile devices (e.g. Android FCM or iOS APNs)
     * and may include platform-specific identifiers or device tokens.
     *
     * @param dto  the native push subscription data
     * @param user the authenticated user the subscription belongs to
     */
    void subscribeNative(NativeSubscriptionDto dto, ApplicationUser user);

    /**
     * Sends a push notification to the given user.
     *
     * <p>The notification will be delivered to all registered push subscriptions
     * (web and/or native) associated with the user.
     *
     * @param user  the recipient of the notification
     * @param title the notification title
     * @param body  the notification body text
     */
    void sendToUser(ApplicationUser user, String title, String body);

}
