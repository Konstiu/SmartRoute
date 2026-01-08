import { Component, OnDestroy, OnInit } from '@angular/core';
import { IonicModule } from '@ionic/angular';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { KeyManagementService } from 'src/services/key-management.service';
import { db } from 'src/db/encryption';
import { ChatSocketService } from 'src/services/chat-socket.service';

// Temporary interface for chat messages - will be replaced with actual DTO later
interface ChatMessage {
  id: number;
  text: string;
  timestamp: Date;
  isOwnMessage: boolean;
}

@Component({
  selector: 'app-chat',
  templateUrl: './chat.page.html',
  styleUrls: ['./chat.page.scss'],
  standalone: true,
  imports: [IonicModule, CommonModule, FormsModule]
})
export class ChatPage implements OnInit, OnDestroy {
  friendName: string = '';
  friendEmail: string = '';
  messageText: string = '';
  messages: ChatMessage[] = [];

  constructor(
    private route: ActivatedRoute,
    private keyManagementService: KeyManagementService,
    private chatSocketService: ChatSocketService
  ) {}

  async ngOnInit(): Promise<void> {
    // Get friend name from route parameters
    this.route.queryParams.subscribe(params => {
      this.friendName = decodeURIComponent(params['friendName'] || 'Friend');
      this.friendEmail = decodeURIComponent(params['friendEmail'] || '');
    });

    // Get message history from local database
    const localMessages = await db.getMessagesWithFriend(this.friendEmail);

    // Map local messages to ChatMessage format
    this.messages = localMessages.map((msg, index) => ({
      id: index + 1,
      text: msg.plaintext,
      timestamp: msg.timestamp,
      isOwnMessage: msg.direction === 'sent'
    }));

    // Fetch new messages from backend
    await this.fetchNewMessages();

    // Connect websocket for real-time updates
    this.chatSocketService.connect(this.friendEmail);

    // Subscribe to new message notifications
    this.chatSocketService.onNewMessage().subscribe(async () => {
      console.log('New message notification received via WebSocket');
      await this.fetchNewMessages();
    });

  }

  ngOnDestroy(): void {
    this.chatSocketService.disconnect();
  }

  async fetchNewMessages() {
    // Get latest timestamp from local messages
    const latestLocalTimestamp = this.messages.length > 0
      ? this.messages[this.messages.length - 1].timestamp
      : new Date(0);
    const latestId = this.messages.length > 0
      ? this.messages[this.messages.length - 1].id
      : 0;

    // Get messages from backend
    const newMessages = await this.keyManagementService.getMessagesFromBackendAfter(
      this.friendEmail,
      latestLocalTimestamp
    );

    // filter out sent messages and duplicates if any
    const newMessagesFiltered = newMessages.filter(msg => {
      return msg.senderEmail === this.friendEmail && msg.id! > latestId;
    });

    console.log('New messages from backend:', newMessagesFiltered);

    for (const messageDetail of newMessagesFiltered) {
      // Process and store each new message
      const message = await this.keyManagementService.receiveMessageFromFriend(messageDetail);
      this.messages.push({
        id: this.messages.length + 1,
        text: message.plaintext,
        timestamp: message.timestamp ? new Date(message.timestamp) : new Date(),
        isOwnMessage: false
      });
    }
  }



  // Placeholder method for sending messages
  async sendMessage(): Promise<void> {
    if (this.messageText.trim()) {
      // TODO: Implement actual message sending logic
      console.log('Sending message:', this.messageText);
      
      // Send the message using KeyManagementService
      const messageDetail = await this.keyManagementService.sendMessageToFriend(this.friendEmail, this.messageText);

      // Add the message to the local messages array for UI update
      this.messages.push({
        id: this.messages.length + 1,
        text: messageDetail.plaintext,
        timestamp: messageDetail.timestamp ? new Date(messageDetail.timestamp) : new Date(),
        isOwnMessage: true
      });
      
      this.messageText = '';
    }
  }

  // Helper method to format timestamp
  formatTime(date: Date): string {
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays === 1) return 'Yesterday';
    if (diffDays < 7) return `${diffDays}d ago`;
    
    return date.toLocaleDateString();
  }
}
