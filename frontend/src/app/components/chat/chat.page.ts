import {Component, OnInit, OnDestroy} from '@angular/core';
import {interval, Subscription} from 'rxjs';
import {KeyManagementService} from '../../../services/key-management.service';
import {ChatSocketService} from '../../../services/chat-socket.service';
import {db, Message} from '../../../db/encryption';
import {IonicModule} from "@ionic/angular";
import {CommonModule} from "@angular/common";
import {FormsModule} from "@angular/forms";
import {ActivatedRoute} from '@angular/router';
import {UserService} from "../../../services/user.service";


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
  myDevices: string[]= [];

  showDeviceInfo = false;

  private subscriptions: Subscription[] = [];

  constructor(
    private route: ActivatedRoute,
    private keyManagementService: KeyManagementService,
    private chatSocketService: ChatSocketService,
  ) {
  }

  async ngOnInit() {
    this.route.queryParams.subscribe(async params => {
      this.friendEmail = decodeURIComponent(params['friendEmail'] || '');

      // Initialize device ID
      this.myDeviceId = await this.keyManagementService.getCurrentDeviceId();

      // Load friend's devices
      this.friendDevices = await this.keyManagementService.getDevicesOfFriend(
        this.friendEmail
      );

      this.myDevices = await this.keyManagementService.getMyDevices();

      // Load existing messages
      await this.loadMessages();

      // Fetch new messages from backend
      await this.fetchNewMessages();

      // Connect to WebSocket
      await this.chatSocketService.connect(this.friendEmail);

      // Listen for new messages
      const newMessageSub = this.chatSocketService.onNewMessage().subscribe(
        async () => {
          await this.fetchNewMessages();
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
      this.subscriptions.push(statusSub);
    });
  }

  ngOnDestroy() {
    this.subscriptions.forEach(sub => sub.unsubscribe());
    this.chatSocketService.disconnect();
  }

  /**
   * Load messages from local database
   */
  async loadMessages() {
    const allMessages = await db.getMessagesWithFriend(this.friendEmail);

    // Deduplicate by conversationMessageId - keep only first occurrence
    const seen = new Set<string>();
    this.messages = allMessages.filter(msg => {
      if (seen.has(msg.conversationMessageId)) {
        return false; // Skip duplicates
      }
      seen.add(msg.conversationMessageId);
      return true;
    });
  }

  /**
   * Send message to all friend's devices
   */
  async sendMessage() {
    if (!this.messageText.trim()) return;
    const conversationMessageId = crypto.randomUUID(); // Generate ONCE

    try {
      // Send to all of friend's devices
      const sentMessages = await this.keyManagementService.sendMessageToFriend(
        this.friendEmail,
        this.messageText,
        conversationMessageId
      );

      console.log(`Message sent to ${sentMessages.length} devices`);

      const sentMessagesMyDevices = await this.keyManagementService.sendMessageToMyDevices(this.friendEmail,
        this.messageText,
        conversationMessageId
      )

      console.log(`Message sent to ${sentMessagesMyDevices.length} of my devices`);


      // Add to local display (only show one copy)
      await this.loadMessages();
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

      console.log("New messags");
      console.log(newMessages);

      // Decrypt each message
      for (const msgDto of newMessages) {
        try {
          const decryptedMsg = await this.keyManagementService
            .receiveMessageFromFriend(msgDto);

          // Only add if we don't already have this conversation message
          if (!this.hasConversationMessage(decryptedMsg.conversationMessageId)) {
            this.messages.push(decryptedMsg);
          }
        } catch (error) {
          console.error('Failed to decrypt message:', error);
        }
      }

    } catch (error) {
      console.error('Failed to fetch new messages:', error);
    }
  }

  formatTime(ts: any): string {
    if (!ts) return '';
    const d = ts instanceof Date ? ts : new Date(ts);
    if (isNaN(d.getTime())) return '';
    return d.toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'});
  }

  private hasConversationMessage(id: string): boolean {
    return this.messages.some(
      m => m.conversationMessageId === id
    );
  }
}
