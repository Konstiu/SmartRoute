import { Platform } from '@ionic/angular';
import { KeyManagementService } from 'src/services/key-management.service';
import {AfterViewInit, Component, ElementRef, OnInit, ViewChild, ChangeDetectorRef} from '@angular/core';
import {PushNotificationService} from "../../services/push-notification.service";
import {Gesture, GestureController, AlertController, ToastController} from '@ionic/angular';
import {UserService} from 'src/services/user.service';
import {AuthService} from 'src/services/auth.service';
import { ChatMessageService } from 'src/services/chat-message.service';
import { FriendshipService } from 'src/services/friendship.service';
import { Geolocation } from "@capacitor/geolocation"

@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  styleUrls: ['app.component.scss'],
  standalone: false,
})
export class AppComponent implements OnInit, AfterViewInit {
  @ViewChild('alertFab', { read: ElementRef }) set alertFabElement(element: ElementRef<HTMLElement>) {
    if (element && this.emergencyAlertVisible) {
      this.alertFab = element;
      // Re-initialize gesture when element becomes available
      setTimeout(() => {
        this.initializeGesture();
      }, 0);
    }
  }

  alertFab!: ElementRef<HTMLElement>;
  isLoggedIn = false;

  // Position for dragging
  x = 0;
  y = 0;

  emergencyAlertVisible = true;


  // Drag state
  dragging = false;
  private startX = 0;
  private startY = 0;
  private originX = 0;
  private originY = 0;

  // Hold state
  private holdGesture?: Gesture;
  private holdTimeout?: any;
  protected holding = false;
  private progressInterval?: any;
  public holdProgress = 0;

  constructor(
    private pushService: PushNotificationService,
    private gestureCtrl: GestureController,
    private alertCtrl: AlertController,
    private toastCtrl: ToastController,
    private cdr: ChangeDetectorRef,
    private userService: UserService,
    private toastController: ToastController,
    private authService: AuthService,
    private platform: Platform,
    private keyManagementService: KeyManagementService,
    private chatMessageService: ChatMessageService,
    private friendshipService: FriendshipService
  ) {
  }

  ngOnInit() {
    this.initializeApp();

    // Listen for incoming notifications
    this.pushService.listenToNotifications();

    this.isLoggedIn = this.authService.isLoggedIn();

    this.pushService.emergencyAlertEnabled$.subscribe(enabled => {
      this.emergencyAlertVisible = enabled;
      this.cdr.detectChanges();
    });

    // Set initial position: bottom-right with safe area padding
    this.placeDefault();
  }

  shouldShowButton(): boolean {
    return this.authService.isLoggedIn() && this.emergencyAlertVisible;
  }

  ngAfterViewInit() {
    // Create gesture for hold + drag detection
    this.holdGesture = this.gestureCtrl.create({
      el: this.alertFab.nativeElement,
      threshold: 0,
      gestureName: 'alert-hold-drag',
      onStart: (ev) => this.onGestureStart(ev),
      onMove: (ev) => this.onGestureMove(ev),
      onEnd: () => this.onGestureEnd(),
    });

    this.holdGesture.enable(true);
  }


  private initializeGesture() {
    if (!this.alertFab?.nativeElement) {
      return;
    }

    // Destroy existing gesture if any
    if (this.holdGesture) {
      this.holdGesture.destroy();
    }

    // Create gesture for hold + drag detection
    this.holdGesture = this.gestureCtrl.create({
      el: this.alertFab.nativeElement,
      threshold: 0,
      gestureName: 'alert-hold-drag',
      onStart: (ev) => this.onGestureStart(ev),
      onMove: (ev) => this.onGestureMove(ev),
      onEnd: () => this.onGestureEnd(),
    });

    this.holdGesture.enable(true);
  }

  private placeDefault() {
    const pad = 16;
    const bubbleSize = 56;
    const vw = window.innerWidth;
    const vh = window.innerHeight;

    // Keep above tab bar (~56px) + safe area
    const bottomAvoid = 80;

    this.x = vw - bubbleSize - pad;
    this.y = vh - bubbleSize - pad - bottomAvoid;
  }

  private onGestureStart(ev: any) {
    this.dragging = false;
    this.startX = ev.currentX;
    this.startY = ev.currentY;
    this.originX = this.x;
    this.originY = this.y;

    // Start hold detection
    this.startHold();
  }

  private onGestureMove(ev: any) {
    const dx = ev.currentX - this.startX;
    const dy = ev.currentY - this.startY;

    // If moved more than threshold, it's a drag (cancel hold)
    if (Math.abs(dx) + Math.abs(dy) > 10) {
      if (!this.dragging) {
        this.dragging = true;
        this.cancelHold();
      }
    }

    if (this.dragging) {
      this.x = this.originX + dx;
      this.y = this.originY + dy;
      this.clampToViewport();
      this.cdr.detectChanges();
    }
  }

  private onGestureEnd() {
    if (this.dragging) {
      this.snapToEdge();
      this.dragging = false;
    } else {
      this.endHold();
    }
    this.cdr.detectChanges();
  }

  private clampToViewport() {
    const pad = 8;
    const bubbleSize = 56;
    const bottomAvoid = 80;

    const maxX = window.innerWidth - bubbleSize - pad;
    const maxY = window.innerHeight - bubbleSize - pad - bottomAvoid;

    this.x = Math.max(pad, Math.min(maxX, this.x));
    this.y = Math.max(pad, Math.min(maxY, this.y));
  }

  private snapToEdge() {
    const pad = 16;
    const bubbleSize = 56;
    const vw = window.innerWidth;

    const leftDist = this.x;
    const rightDist = (vw - bubbleSize) - this.x;

    // Snap to nearest horizontal edge
    this.x = leftDist < rightDist ? pad : (vw - bubbleSize - pad);
  }

  private startHold() {
    this.holding = true;
    this.holdProgress = 0;

    // Animate progress indicator
    this.progressInterval = setInterval(() => {
      this.holdProgress += 2;
      this.cdr.detectChanges();
      if (this.holdProgress >= 100) {
        clearInterval(this.progressInterval);
      }
    }, 30);

    // Haptic feedback (if available)
    if ('vibrate' in navigator) {
      navigator.vibrate(50);
    }

    this.holdTimeout = setTimeout(async () => {
      if (!this.holding) return;
      this.holding = false;

      // Success haptic
      if ('vibrate' in navigator) {
        navigator.vibrate([100, 50, 100]);
      }

      await this.openConfirm();
    }, 1500);
  }

  private cancelHold() {
    this.holding = false;
    this.holdProgress = 0;
    clearTimeout(this.holdTimeout);
    clearInterval(this.progressInterval);
  }

  private endHold() {
    this.holding = false;
    this.holdProgress = 0;
    clearTimeout(this.holdTimeout);
    clearInterval(this.progressInterval);
    this.cdr.detectChanges();
  }

  private async openConfirm() {
    const alert = await this.alertCtrl.create({
      header: '!! Emergency Alert !!',
      message: 'Send emergency notification to all your friends?',
      cssClass: 'emergency-alert-modal',
      buttons: [
        {
          text: 'Cancel',
          role: 'cancel',
          cssClass: 'alert-button-cancel'
        },
        {
          text: 'Send Alert',
          role: 'confirm',
          cssClass: 'alert-button-confirm',
          handler: () =>     this.alertFriends(),
        },
      ],
    });

    await alert.present();
  }


  alertFriends() {
    // Your existing push notification logic
    this.userService.getUserData()
      .subscribe({
        next: user => {
          const name = user.firstname ?? 'A friend';

          // send push notifications to all friends
          this.pushService
            .sendTestNotification(
              '!!ALERT!!',
              `${name} needs your attention.`
            )
            .subscribe({
              next: () => {
                this.showToast('Notification sent successfully', 'success');
              },
              error: () => {
                this.showToast('Failed to send notification', 'danger');
              }
            });

          // send chat message with alert to all friends
          this.friendshipService.getFriends().subscribe(async friendships => {
            try {
              // get current location
              const location = await Geolocation.getCurrentPosition({
                enableHighAccuracy: true,
                maximumAge: 0
              });

              friendships.forEach(friendship => {
                const friendEmail: string = friendship.sender.email === user.email ? friendship.receiver.email : friendship.sender.email;
                this.chatMessageService.sendLocationMessage(
                  '!!EMERGENCY ALERT!! Please check on me.',
                  location.coords.latitude,
                  location.coords.longitude,
                  friendEmail,
                );
              });
            } catch (e) {
              // send text-only alert if location fetch fails
              console.error("ERROR: unable to determine position:", e);
              friendships.forEach(friendship => {
                const friendEmail: string = friendship.sender.email === user.email ? friendship.receiver.email : friendship.sender.email;
                this.chatMessageService.sendTextMessage(
                  '!!EMERGENCY ALERT!! Please check on me.',
                  friendEmail,
                );
              });
            }
          });
        }
      });
  }

  requestNotificationPermission() {
    this.pushService.subscribeToNotifications();
  }

  private async showToast(message: string, color: string = 'primary', duration: number = 2000) {
    const toast = await this.toastController.create({
      message: message,
      duration: duration,
      position: 'top',
      color: color,
      buttons: [
        {
          text: 'Dismiss',
          role: 'cancel'
        }
      ]
    });
    await toast.present();
  }

  initializeApp() {
    this.platform.ready().then(async () => {
      // if the user is logged in, ensure that the signed pre-key is up to date
      // and that there are enough one-time pre-keys available
      if (this.authService.isLoggedIn()) {
        const updated = await this.keyManagementService.updateSignedPreKeyIfNecessary();
        if (updated) {
          await this.keyManagementService.uploadPublicSignedPreKey();
        };
        await this.keyManagementService.generateStoreAndUploadOneTimePreKeysIfNecessary();
      }
    });
  }
}
