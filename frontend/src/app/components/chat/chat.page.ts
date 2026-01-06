import { Component, OnInit } from '@angular/core';
import { IonicModule } from '@ionic/angular';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { KeyManagementService } from 'src/services/key-management.service';
import { db } from 'src/db/encryption';

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
export class ChatPage implements OnInit {
  friendName: string = '';
  friendEmail: string = '';
  messageText: string = '';
  messages: ChatMessage[] = [];

  constructor(
    private route: ActivatedRoute,
    private keyManagementService: KeyManagementService
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

    // Get latest timestamp from local messages
    const latestLocalTimestamp = localMessages.length > 0
      ? localMessages[localMessages.length - 1].timestamp
      : new Date(0);
    const latestId = localMessages.length > 0
      ? localMessages[localMessages.length - 1].id
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

    

    // Mock messages for UI demonstration
    // this.messages = [
    //   {
    //     id: 1,
    //     text: 'Hey! How are you doing?',
    //     timestamp: new Date(Date.now() - 3600000),
    //     isOwnMessage: false
    //   },
    //   {
    //     id: 2,
    //     text: 'Hi! I\'m doing great, thanks! Just finished a long run.',
    //     timestamp: new Date(Date.now() - 3000000),
    //     isOwnMessage: true
    //   },
    //   {
    //     id: 3,
    //     text: 'That\'s awesome! How many kilometers?',
    //     timestamp: new Date(Date.now() - 2400000),
    //     isOwnMessage: false
    //   },
    //   {
    //     id: 4,
    //     text: 'About 10km in 50 minutes. Pretty happy with the pace!',
    //     timestamp: new Date(Date.now() - 1800000),
    //     isOwnMessage: true
    //   }
    // ];
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
