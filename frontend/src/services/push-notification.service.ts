import { Injectable } from '@angular/core';
import { SwPush } from '@angular/service-worker';
import { HttpClient } from '@angular/common/http';
import { Globals } from '../global/globals';
import { Capacitor } from '@capacitor/core';
import {
  PushNotifications,
  Token,
  PushNotificationSchema,
  ActionPerformed
} from '@capacitor/push-notifications';
import { Observable, BehaviorSubject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class PushNotificationService {

  private subscriptionStatus = new BehaviorSubject<boolean>(false);
  public subscriptionStatus$ = this.subscriptionStatus.asObservable();

  constructor(
    private swPush: SwPush,
    private http: HttpClient,
    private globals: Globals
  ) {}

  /**
   * Main method to subscribe to push notifications
   * Automatically detects platform (web/native)
   */
  async subscribeToNotifications(): Promise<boolean> {
    try {
      if (Capacitor.isNativePlatform()) {
        return await this.initNativePush();
      } else {
        return await this.initWebPush();
      }
    } catch (error) {
      console.error('Error subscribing to notifications:', error);
      return false;
    }
  }

  /**
   * Initialize native push notifications (iOS/Android)
   */
  private async initNativePush(): Promise<boolean> {
    try {
      // Request permission
      const result = await PushNotifications.requestPermissions();

      if (result.receive !== 'granted') {
        console.log('Push notification permission denied');
        return false;
      }

      // Register for push
      await PushNotifications.register();

      // Listen for registration token
      PushNotifications.addListener('registration', (token: Token) => {
        console.log('FCM Token received:', token.value);

        // Send FCM token to backend
        this.http.post(`${this.globals.backendUri}/notifications/subscribe-native`, {
          token: token.value,
          platform: Capacitor.getPlatform()
        }).subscribe({
          next: (response) => {
            console.log('Native subscription saved:', response);
            this.subscriptionStatus.next(true);
          },
          error: (err) => {
            console.error('Failed to save native subscription:', err);
            this.subscriptionStatus.next(false);
          }
        });
      });

      // Listen for registration errors
      PushNotifications.addListener('registrationError', (error: any) => {
        console.error('Registration error:', error);
        this.subscriptionStatus.next(false);
      });

      // Listen for incoming notifications (foreground)
      PushNotifications.addListener('pushNotificationReceived',
        (notification: PushNotificationSchema) => {
          console.log('Notification received (foreground):', notification);
          // You can show a custom notification UI here
        }
      );

      // Listen for notification tap (background)
      PushNotifications.addListener('pushNotificationActionPerformed',
        (action: ActionPerformed) => {
          console.log('Notification action performed:', action);
          // Handle notification tap - navigate to specific screen
        }
      );

      return true;
    } catch (error) {
      console.error('Native push initialization error:', error);
      return false;
    }
  }

  /**
   * Initialize web push notifications (PWA)
   */
  private async initWebPush(): Promise<boolean> {
    try {
      if (!this.swPush.isEnabled) {
        console.warn('Service Worker is not enabled');
        return false;
      }

      const subscription = await this.swPush.requestSubscription({
        serverPublicKey: this.globals.vapidPublicKey
      });

      console.log('Web push subscription created:', subscription);

      // Send subscription to backend
      return new Promise((resolve) => {
        this.http.post(`${this.globals.backendUri}/notifications/subscribe`, subscription)
          .subscribe({
            next: (response) => {
              console.log('Web subscription saved to backend:', response);
              this.subscriptionStatus.next(true);
              resolve(true);
            },
            error: (err) => {
              console.error('Failed to save web subscription:', err);
              this.subscriptionStatus.next(false);
              resolve(false);
            }
          });
      });
    } catch (error) {
      console.error('Web push initialization error:', error);
      return false;
    }
  }

  /**
   * Listen to notification events (web only)
   */
  listenToNotifications() {
    if (!Capacitor.isNativePlatform() && this.swPush.isEnabled) {
      // Listen for incoming messages
      this.swPush.messages.subscribe(
        (notification: any) => {
          console.log('Received web notification:', notification);
          // Handle notification payload
        }
      );

      // Listen for notification clicks
      this.swPush.notificationClicks.subscribe(
        ({ action, notification }) => {
          console.log('Web notification clicked:', action, notification);
          // Navigate to relevant page
        }
      );
    }
  }

  /**
   * Unsubscribe from push notifications
   */
  async unsubscribe(): Promise<boolean> {
    try {
      if (Capacitor.isNativePlatform()) {
        // For native, you might want to call a backend endpoint to remove the token
        await PushNotifications.removeAllListeners();
        this.subscriptionStatus.next(false);
        return true;
      } else {
        await this.swPush.unsubscribe();
        console.log('Unsubscribed from web push');
        this.subscriptionStatus.next(false);
        return true;
      }
    } catch (error) {
      console.error('Failed to unsubscribe:', error);
      return false;
    }
  }

  /**
   * Check if user is currently subscribed
   */
  async isSubscribed(): Promise<boolean> {
    if (Capacitor.isNativePlatform()) {
      const result = await PushNotifications.checkPermissions();
      return result.receive === 'granted';
    } else {
      return this.swPush.isEnabled &&
        (this.swPush.subscription) !== null;
    }
  }

  /**
   * TEST METHOD: Send test notification to current user
   */
  sendTestNotification(title?: string, body?: string): Observable<any> {
    const payload = {
      title: title || 'Test Notification',
      body: body || 'This is a test notification! 🎉'
    };

    return this.http.post(`${this.globals.backendUri}/notifications/test/me`, payload);
  }

  /**
   * TEST METHOD: Quick test notification
   */
  sendQuickTest(): Observable<any> {
    return this.http.get(`${this.globals.backendUri}/notifications/test/quick`);
  }

  /**
   * Check subscription status from backend
   */
  checkSubscriptionStatus(): Observable<any> {
    return this.http.get(`${this.globals.backendUri}/notifications/status`);
  }

  /**
   * Get current platform
   */
  getPlatform(): string {
    return Capacitor.isNativePlatform()
      ? Capacitor.getPlatform()
      : 'web';
  }
}
