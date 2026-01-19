import {Component, OnInit, OnDestroy} from '@angular/core';
import {Subscription} from 'rxjs';
import {KeyManagementService} from '../../../services/key-management.service';
import {ChatSocketService} from '../../../services/chat-socket.service';
import {db, Message} from '../../../db/encryption';
import {IonicModule} from "@ionic/angular";
import {CommonModule} from "@angular/common";
import {FormsModule} from "@angular/forms";
import {ActivatedRoute} from '@angular/router';
import { ChatMessageService } from 'src/services/chat-message.service';
import { MapComponent } from '../map/map.component';
import { icon, Icon, latLng, LatLng, Layer, marker } from 'leaflet';
import {FriendInfoDto} from "../../dtos/friendship";
import {AuthService} from "../../../services/auth.service";
import {FriendshipService} from "../../../services/friendship.service";

export interface ParsedMessage {
  type: string;
  text: string;
  timestamp: Date;
  senderDeviceId: string;
  direction: 'sent' | 'received';
  conversationMessageId: string;
  latitude?: number;
  longitude?: number;
}

@Component({
  selector: 'app-chat',
  templateUrl: './chat.page.html',
  styleUrls: ['./chat.page.scss'],
  standalone: true,
  imports: [IonicModule, CommonModule, FormsModule, MapComponent]
})
export class ChatPage implements OnInit, OnDestroy {
  friendEmail = 'friend@example.com';
  messages: ParsedMessage[] = [];
  messageText = '';
  myDeviceId = '';
  friendDevices: string[] = [];
  myDevices: string[]= [];
  friendName: string = '';

  showDeviceInfo = false;

  private subscriptions: Subscription[] = [];

  constructor(
    private route: ActivatedRoute,
    private keyManagementService: KeyManagementService,
    private chatSocketService: ChatSocketService,
    private chatMessageService: ChatMessageService,
    private authService: AuthService,
    private friendService: FriendshipService
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

      this.setFriendNameByEmail(this.friendEmail);

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
    })
    .map(msg => this.parseMessage(msg));
  }

  /**
   * Send message to all friend's devices
   */
  async sendMessage() {
    try {
      await this.chatMessageService.sendTextMessage(this.messageText, this.friendEmail);

      // Add to local display (only show one copy)
      await this.loadMessages();

      // Clear input
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
          // Only decrypt and add if we don't already have this conversation message
          if (this.hasConversationMessage(msgDto.conversationMessageId)) {
            continue;
          }

          const decryptedMsg = await this.keyManagementService
            .receiveMessageFromFriend(msgDto);

          this.messages.push(this.parseMessage(decryptedMsg));

        } catch (error) {
          console.error('Failed to decrypt message:', error);
        }
      }

    } catch (error) {
      console.error('Failed to fetch new messages:', error);
    }
  }

  parseMessage(msg: Message): ParsedMessage {
    const messageObj = JSON.parse(msg.plaintext);
    if (messageObj.type === 'text') {
      return {
        type: 'text',
        text: messageObj.text,
        timestamp: msg.timestamp,
        senderDeviceId: msg.senderDeviceId,
        direction: msg.direction,
        conversationMessageId: msg.conversationMessageId
      }
    } else if (messageObj.type === 'location') {
      return {
        type: 'location',
        text: messageObj.text,
        timestamp: msg.timestamp,
        senderDeviceId: msg.senderDeviceId,
        direction: msg.direction,
        conversationMessageId: msg.conversationMessageId,
        longitude: messageObj.longitude,
        latitude: messageObj.latitude
      }
    }
    throw new Error('Unknown message type');
  }

  formatTime(ts: any): string {
    if (!ts) return '';
    const d = ts instanceof Date ? ts : new Date(ts);
    if (isNaN(d.getTime())) return '';
    return d.toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'});
  }

  private hasConversationMessage(id: string): boolean {
    // start from the end for efficiency
    for (let i = this.messages.length - 1; i >= 0; i--) {
      if (this.messages[i].conversationMessageId === id) {
        return true;
      }
    }
    return false;
  }

  markerOptions = {
    icon: icon({
      ...Icon.Default.prototype.options,
      iconUrl: 'assets/marker-icon.png',
      iconRetinaUrl: 'assets/marker-icon-2x.png',
      shadowUrl: 'assets/marker-shadow.png'
    })
  };

  getLocationLayers(latitude: number, longitude: number): Layer[] {
    return [marker(latLng(latitude, longitude), this.markerOptions)];
  }

  getLocationCenter(latitude: number, longitude: number): LatLng {
    return latLng(latitude, longitude);
  }


  setFriendNameByEmail(email: string) {
    const myEmail = this.authService.getUserEmail();

    this.friendService.getFriends().subscribe({
      next: (friendships) => {
        const friendship = friendships.find(f =>
          f.sender.email === email || f.receiver.email === email
        );

        const friend =
          !friendship ? undefined :
            friendship.sender.email !== myEmail ? friendship.sender : friendship.receiver;

        this.friendName = friend
          ? `${friend.firstName} ${friend.lastName}`
          : 'Unknown user';
      },
      error: (err) => {
        console.error('getFriends failed', err);
        this.friendName = 'Unknown user';
      }
    });
  }

}
