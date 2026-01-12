import {Injectable} from '@angular/core';
import {SwPush} from '@angular/service-worker';
import {HttpClient} from '@angular/common/http';
import {Globals} from '../global/globals';
import {Capacitor} from '@capacitor/core';
import {
  PushNotifications,
  Token,
  PushNotificationSchema,
  ActionPerformed
} from '@capacitor/push-notifications';
import {Observable, BehaviorSubject} from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class PushNotificationService {

  private subscriptionStatus = new BehaviorSubject<boolean>(false);

  private emergencyAlertEnabledSubject = new BehaviorSubject<boolean>(this.loadFromLocalStorage());
  public emergencyAlertEnabled$ = this.emergencyAlertEnabledSubject.asObservable();

  // Track if listeners are already registered (to prevent duplicates)
  private nativeListenersRegistered = false;

  constructor(
    private swPush: SwPush,
    private http: HttpClient,
    private globals: Globals
  ) {
    // Don't auto-initialize - wait for explicit call after login
    // this.autoInitialize();
  }


  private loadFromLocalStorage(): boolean {
    const stored = localStorage.getItem('emergencyAlertEnabled');
    // Default to true if not set
    return stored !== 'false';
  }

  async setEmergencyAlertEnabled(enabled: boolean) {
    localStorage.setItem('emergencyAlertEnabled', enabled.toString());

    // Update observable
    this.emergencyAlertEnabledSubject.next(enabled);
  }

  getEmergencyAlertEnabled(): boolean {
    return this.emergencyAlertEnabledSubject.value;
  }


  /**
   * Automatically initialize push notifications on service startup
   * ALWAYS attempts to subscribe without checking first
   */
  async autoInitialize() {
    console.log('Force auto-subscribing to push notifications...');

    try {
      // ALWAYS try to subscribe - skip the check
      const success = await this.subscribeToNotifications();

    } catch (error) {
      console.warn('Auto-initialization failed:', error);
      // Fail silently - don't break the app
    }
  }

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
      // Register listeners BEFORE requesting permissions (only once)
      if (!this.nativeListenersRegistered) {
        this.registerNativeListeners();
        this.nativeListenersRegistered = true;
      }

      // Request permission
      const result = await PushNotifications.requestPermissions();

      if (result.receive !== 'granted') {
        console.log('Push notification permission denied');
        return false;
      }

      // Register for push
      await PushNotifications.register();

      // Return true immediately - the 'registration' listener will handle the backend call
      return true;
    } catch (error) {
      console.error('Native push initialization error:', error);
      return false;
    }
  }

  /**
   * Register native push notification listeners
   */
  private registerNativeListeners(): void {
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
      // Set up message listeners
      this.listenToNotifications();

      // Send subscription to backend
      return new Promise((resolve) => {
        this.http.post(`${this.globals.backendUri}/notifications/subscribe`, subscription)
          .subscribe({
            next: (response) => {
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
          // Handle notification payload
        }
      );

      // Listen for notification clicks
      this.swPush.notificationClicks.subscribe(
        ({action, notification}) => {
          console.log('Web notification clicked:', action, notification);
          // Navigate to relevant page
        }
      );
    }
  }

  /**
   * Send notification to all friend
   */
  sendTestNotification(title?: string, body?: string): Observable<any> {
    const payload = {
      title: title || 'Test Notification',
      body: body || 'This is a test notification!'
    };

    return this.http.post(`${this.globals.backendUri}/notifications/friend`, payload);
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
