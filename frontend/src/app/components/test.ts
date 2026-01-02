import {Component, OnInit} from '@angular/core';
import {PushNotificationService} from "../../services/push-notification.service";
import {NgIf} from "@angular/common";
import {SubscriptionStatus} from "../dtos/pushNotificationsDto/PushNotificationsDto";

@Component({
  selector: 'app-push-notification-test',
  template: `
    <div class="container">
      <div class="card">
        <h1>🔔 Push Notification Test</h1>
        <p class="subtitle">Test your push notification integration</p>

        <!-- Status Display -->
        <div *ngIf="statusMessage"
             [class]="'status-message ' + statusType">
          {{ statusMessage }}
        </div>

        <!-- Platform Info -->
        <div class="info-box">
          <strong>Platform:</strong> {{ platform }}
          <br>
          <strong>Subscription Status:</strong>
          <span [class.subscribed]="isSubscribed">
            {{ isSubscribed ? '✅ Subscribed' : '❌ Not Subscribed' }}
          </span>
        </div>

        <!-- Action Buttons -->
        <div class="button-group">
          <button class="btn btn-primary"
                  (click)="subscribe()"
                  [disabled]="loading || isSubscribed">
            {{ isSubscribed ? '✅ Already Subscribed' : '📱 Subscribe to Notifications' }}
          </button>

          <button class="btn btn-success"
                  (click)="sendTestNotification()"
                  [disabled]="loading || !isSubscribed">
            🧪 Send Test Notification
          </button>

          <button class="btn btn-info"
                  (click)="sendQuickTest()"
                  [disabled]="loading || !isSubscribed">
            ⚡ Quick Test
          </button>

          <button class="btn btn-warning"
                  (click)="checkStatus()"
                  [disabled]="loading">
            🔍 Check Status
          </button>

          <button class="btn btn-danger"
                  (click)="unsubscribe()"
                  [disabled]="loading || !isSubscribed">
            🔕 Unsubscribe
          </button>
        </div>

        <!-- Loading Indicator -->
        <div *ngIf="loading" class="loading">
          <div class="spinner"></div>
          Processing...
        </div>

        <!-- Instructions -->
        <div class="instructions">
          <h3>Testing Instructions:</h3>
          <ol>
            <li>Click "Subscribe to Notifications" to enable push notifications</li>
            <li>Grant permission when prompted by your browser/device</li>
            <li>Click "Send Test Notification" to test the integration</li>
            <li>Check if you receive the notification</li>
            <li>On mobile, try backgrounding the app to test background notifications</li>
          </ol>
        </div>
      </div>
    </div>
  `,
  imports: [
    NgIf
  ],
  styles: [`
    .container {
      max-width: 600px;
      margin: 40px auto;
      padding: 20px;
    }

    .card {
      background: white;
      border-radius: 12px;
      padding: 30px;
      box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
    }

    h1 {
      color: #333;
      margin-bottom: 8px;
      font-size: 28px;
    }

    .subtitle {
      color: #666;
      margin-bottom: 24px;
    }

    .status-message {
      padding: 12px 16px;
      border-radius: 8px;
      margin-bottom: 20px;
      font-size: 14px;
    }

    .status-message.success {
      background: #d4edda;
      color: #155724;
      border: 1px solid #c3e6cb;
    }

    .status-message.error {
      background: #f8d7da;
      color: #721c24;
      border: 1px solid #f5c6cb;
    }

    .status-message.info {
      background: #d1ecf1;
      color: #0c5460;
      border: 1px solid #bee5eb;
    }

    .info-box {
      background: #f8f9fa;
      border-radius: 8px;
      padding: 16px;
      margin-bottom: 20px;
      font-size: 14px;
    }

    .info-box strong {
      color: #333;
    }

    .subscribed {
      color: #28a745;
      font-weight: bold;
    }

    .button-group {
      display: flex;
      flex-direction: column;
      gap: 10px;
      margin-bottom: 20px;
    }

    .btn {
      padding: 12px 20px;
      border: none;
      border-radius: 6px;
      font-size: 16px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.3s;
      color: white;
    }

    .btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .btn-primary {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    }

    .btn-primary:hover:not(:disabled) {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
    }

    .btn-success {
      background: #28a745;
    }

    .btn-success:hover:not(:disabled) {
      background: #218838;
    }

    .btn-info {
      background: #17a2b8;
    }

    .btn-info:hover:not(:disabled) {
      background: #138496;
    }

    .btn-warning {
      background: #ffc107;
      color: #333;
    }

    .btn-warning:hover:not(:disabled) {
      background: #e0a800;
    }

    .btn-danger {
      background: #dc3545;
    }

    .btn-danger:hover:not(:disabled) {
      background: #c82333;
    }

    .loading {
      text-align: center;
      padding: 20px;
      color: #666;
    }

    .spinner {
      border: 3px solid #f3f3f3;
      border-top: 3px solid #667eea;
      border-radius: 50%;
      width: 40px;
      height: 40px;
      animation: spin 1s linear infinite;
      margin: 0 auto 10px;
    }

    @keyframes spin {
      0% {
        transform: rotate(0deg);
      }
      100% {
        transform: rotate(360deg);
      }
    }

    .instructions {
      margin-top: 30px;
      padding: 20px;
      background: #fff3cd;
      border-radius: 8px;
      border: 1px solid #ffc107;
    }

    .instructions h3 {
      margin-top: 0;
      color: #856404;
    }

    .instructions ol {
      margin: 10px 0 0 20px;
      color: #856404;
    }

    .instructions li {
      margin-bottom: 8px;
    }
  `]
})
export class PushNotificationTestComponent implements OnInit {

  isSubscribed = false;
  loading = false;
  statusMessage = '';
  statusType: 'success' | 'error' | 'info' = 'info';
  platform = '';

  constructor(private pushService: PushNotificationService) {
  }

  ngOnInit() {
    this.platform = this.pushService.getPlatform();

    // Listen to subscription status changes
    this.pushService.subscriptionStatus$.subscribe(status => {
      this.isSubscribed = status;
    });

    // Start listening to notifications
    this.pushService.listenToNotifications();

    // Check initial subscription status
    this.checkInitialStatus();
  }

  async checkInitialStatus() {
    this.isSubscribed = await this.pushService.isSubscribed();
  }

  async subscribe() {
    this.loading = false;
    this.statusMessage = 'Subscribing to notifications...';
    this.statusType = 'info';

    try {
      const success = await this.pushService.subscribeToNotifications();

      if (success) {
        this.statusMessage = '✅ Successfully subscribed to push notifications!';
        this.statusType = 'success';
        this.isSubscribed = true;
      } else {
        this.statusMessage = '❌ Failed to subscribe. Please check console for details.';
        this.statusType = 'error';
      }
    } catch (error) {
      this.statusMessage = '❌ Error: ' + error;
      this.statusType = 'error';
    } finally {
      this.loading = false;
    }
  }

  sendTestNotification() {
    this.loading = false;
    this.statusMessage = 'Sending test notification...';
    this.statusType = 'info';

    this.pushService.sendTestNotification(
      'Urgent Alert, please open right away',
      'This is a my alert you got and you should open the app right away.'
    ).subscribe({
      next: (response) => {
        console.log('Test notification response:', response);
        this.statusMessage = '✅ Test notification sent! Check your notifications.';
        this.statusType = 'success';
        this.loading = false;
      },
      error: (error) => {
        console.error('Test notification error:', error);
        this.statusMessage = '❌ Failed to send test notification: ' +
          (error.error?.error || error.message);
        this.statusType = 'error';
        this.loading = false;
      }
    });
  }

  sendQuickTest() {
    this.loading = false;
    this.statusMessage = 'Sending quick test...';
    this.statusType = 'info';

    this.pushService.sendQuickTest().subscribe({
      next: (response) => {
        console.log('Quick test response:', response);
        this.statusMessage = '✅ Quick test sent! Check your notifications.';
        this.statusType = 'success';
        this.loading = false;
      },
      error: (error) => {
        console.error('Quick test error:', error);
        this.statusMessage = '❌ Quick test failed: ' +
          (error.error?.error || error.message);
        this.statusType = 'error';
        this.loading = false;
      }
    });
  }

  checkStatus() {
    this.loading = false;
    this.statusMessage = 'Checking subscription status...';
    this.statusType = 'info';

    this.pushService.checkSubscriptionStatus().subscribe({
      next: (response:SubscriptionStatus) => {
        console.log('Status check response:', response);
        this.statusMessage = '✅ Status: ' +
          (response.subscribed ? 'Subscribed' : 'Not subscribed');
        this.statusType = 'success';
        console.log(this.loading +  " test123");
        this.loading = false;
        console.log(this.loading);
        },
      error: (error) => {
        console.error('Status check error:', error);
        this.statusMessage = '❌ Could not check status: ' +
          (error.error?.error || error.message);
        this.statusType = 'error';
        this.loading = false;
      }
    });
  }

  async unsubscribe() {
    this.loading = false;
    this.statusMessage = 'Unsubscribing...';
    this.statusType = 'info';

    try {
      const success = await this.pushService.unsubscribe();

      if (success) {
        this.statusMessage = '✅ Successfully unsubscribed from notifications.';
        this.statusType = 'success';
        this.isSubscribed = false;
      } else {
        this.statusMessage = '❌ Failed to unsubscribe.';
        this.statusType = 'error';
      }
    } catch (error) {
      this.statusMessage = '❌ Error unsubscribing: ' + error;
      this.statusType = 'error';
    } finally {
      this.loading = false;
    }
  }
}
