import { Component, OnInit, OnDestroy } from '@angular/core';
import {interval, Subscription} from 'rxjs';
import { KeyManagementService } from '../../../services/key-management.service';
import { ChatSocketService } from '../../../services/chat-socket.service';
import { db, Message } from '../../../db/encryption';
import {IonicModule} from "@ionic/angular";
import {CommonModule} from "@angular/common";
import {FormsModule} from "@angular/forms";
import { ActivatedRoute } from '@angular/router';



@Component({
  selector: 'app-chat',
  templateUrl: './chat.page.html',
  styleUrls: ['./chat.page.scss'],
  standalone: true,
  imports: [IonicModule, CommonModule, FormsModule]
})
export class ChatPage implements OnInit, OnDestroy {
  friendEmail = 'friend@example.com';
  messages: Message[] = [];
  messageText = '';
  myDeviceId = '';
  friendDevices: string[] = [];

  showDeviceInfo = false;

  private pollingSub?: Subscription;

  private subscriptions: Subscription[] = [];

  constructor(
    private route: ActivatedRoute,
    private keyManagementService: KeyManagementService,
    private chatSocketService: ChatSocketService
  ) {}

  async ngOnInit() {
    this.route.queryParams.subscribe(params => {
      this.friendEmail = decodeURIComponent(params['friendEmail'] || '');
    });

    // Initialize device ID
    this.myDeviceId = await this.keyManagementService.getCurrentDeviceId();

    // Load friend's devices
    this.friendDevices = await this.keyManagementService.getDevicesOfFriend(
      this.friendEmail
    );

    // Load existing messages
    await this.loadMessages();

    await this.fetchNewMessages();

    this.pollingSub = interval(2000).subscribe(() => {
      this.fetchNewMessages();
    });

    // Connect to WebSocket
    //await this.chatSocketService.connect();

    // Listen for new messages
    /*const newMessageSub = this.chatSocketService.onNewMessage().subscribe(
      async (event) => {
        if (event.friendEmail === this.friendEmail) {
          // Fetch and decrypt the new message
          await this.fetchNewMessages();
        }
      }
    );
    this.subscriptions.push(newMessageSub);

    // Listen for connection status
    const statusSub = this.chatSocketService.onConnectionStatus().subscribe(
      (status) => {
        console.log('WebSocket status:', status);
        if (status === 'connected') {
          // Sync messages when reconnected
          this.fetchNewMessages();
        }
      }
    );
    this.subscriptions.push(statusSub);*/
  }

  ngOnDestroy() {
    this.subscriptions.forEach(sub => sub.unsubscribe());
    this.chatSocketService.disconnect();
  }

  /**
   * Load messages from local database
   */
  async loadMessages() {
    this.messages = await db.getMessagesWithFriend(this.friendEmail);
  }

  /**
   * Send message to all friend's devices
   */
  async sendMessage() {
    if (!this.messageText.trim()) return;

    try {
      // Send to all of friend's devices
      const sentMessages = await this.keyManagementService.sendMessageToFriend(
        this.friendEmail,
        this.messageText
      );

      console.log(`Message sent to ${sentMessages.length} devices`);

      // Add to local display (only show one copy)
      this.messages.push(sentMessages[0]);
      this.messageText = '';

    } catch (error) {
      console.error('Failed to send message:', error);
      alert('Failed to send message. Please try again.');
    }
  }

  /**
   * Fetch new messages from backend
   */
  async fetchNewMessages() {
    try {
      // Get timestamp of last message
      const lastMessage = this.messages[this.messages.length - 1];
      const since = lastMessage
        ? lastMessage.timestamp
        : new Date(0);

      // Fetch from backend
      const newMessages = await this.keyManagementService
        .getMessagesFromBackendAfter(this.friendEmail, since);

      // Decrypt each message
      for (const msgDto of newMessages) {
        try {
          const decryptedMsg = await this.keyManagementService
            .receiveMessageFromFriend(msgDto);

          // Add to display
          this.messages.push(decryptedMsg);
        } catch (error) {
          console.error('Failed to decrypt message:', error);
        }
      }

    } catch (error) {
      console.error('Failed to fetch new messages:', error);
    }
  }

  /**
   * Show device-specific message history
   */
  async showDevicePairMessages(theirDeviceId: string) {
    const deviceMessages = await db.getMessagesForDevicePair(
      this.friendEmail,
      this.myDeviceId,
      theirDeviceId
    );

    console.log(`Messages with device ${theirDeviceId}:`, deviceMessages);
  }

  /**
   * Initialize session with a specific device
   */
  async sendToSpecificDevice(deviceId: string) {
    if (!this.messageText.trim()) return;

    try {
      const message = await this.keyManagementService.sendMessageToFriendDevice(
        this.friendEmail,
        deviceId,
        this.messageText
      );

      this.messages.push(message);
      this.messageText = '';

    } catch (error) {
      console.error('Failed to send to specific device:', error);
    }
  }

  formatTime(ts: any): string {
    if (!ts) return '';
    const d = ts instanceof Date ? ts : new Date(ts);
    if (isNaN(d.getTime())) return '';
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  // optional helper if you want to toggle the info panel
  toggleDeviceInfo(): void {
    this.showDeviceInfo = !this.showDeviceInfo;
  }
}
