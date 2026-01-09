import { inject, Injectable } from '@angular/core';
import { Globals } from 'src/global/globals';
import { AuthService } from './auth.service';
import { Observable, Subject } from 'rxjs';
import { KeyManagementService } from './key-management.service';

interface NewMessageEvent {
  friendEmail: string;
  messageId: number;
  senderDeviceId: string;
  recipientDeviceId: string;
}

@Injectable({ providedIn: 'root' })
export class ChatSocketService {
  private socket?: WebSocket;
  private globals = inject(Globals);
  private authService = inject(AuthService);
  private keyManagementService = inject(KeyManagementService);

  private chatWsUri: string = this.globals.backendWsUri + '/chat';
  private newMessage$ = new Subject<NewMessageEvent>();
  private connectionStatus$ = new Subject<'connected' | 'disconnected' | 'error'>();

  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 1000; // Start with 1 second

  /**
   * Connects to the chat WebSocket.
   * With multi-device support, we don't connect to a specific friend,
   * but listen for messages to our current device from any friend.
   */
  async connect() {
    const token = this.authService.getToken();
    if (!token) {
      throw new Error('User is not authenticated');
    }

    const deviceId = await this.keyManagementService.getCurrentDeviceId();

    // Close existing connection if any
    this.disconnect();

    const wsUrl = `${this.chatWsUri}?token=${encodeURIComponent(token)}&deviceId=${encodeURIComponent(deviceId)}`;
    console.log('Connecting to WebSocket:', wsUrl);

    this.socket = new WebSocket(wsUrl);

    this.socket.onopen = () => {
      console.log('WebSocket connected');
      this.connectionStatus$.next('connected');
      this.reconnectAttempts = 0;
      this.reconnectDelay = 1000;
    };

    this.socket.onmessage = (msg) => {
      console.log('Message via WS:', msg.data);

      try {
        const data = JSON.parse(msg.data);

        if (data.type === 'NEW_MESSAGE') {
          // Notify subscribers about the new message with details
          this.newMessage$.next({
            friendEmail: data.friendEmail,
            messageId: data.messageId,
            senderDeviceId: data.senderDeviceId,
            recipientDeviceId: data.recipientDeviceId
          });
        }
      } catch (error) {
        console.error('Error parsing WebSocket message:', error);
      }
    };

    this.socket.onerror = (error) => {
      console.error('WebSocket error:', error);
      this.connectionStatus$.next('error');
    };

    this.socket.onclose = (event) => {
      console.log('WebSocket closed:', event.code, event.reason);
      this.connectionStatus$.next('disconnected');

      // Attempt to reconnect if not a normal closure
      if (event.code !== 1000 && this.reconnectAttempts < this.maxReconnectAttempts) {
        this.scheduleReconnect();
      }
    };
  }

  /**
   * Schedule a reconnection attempt with exponential backoff
   */
  private scheduleReconnect() {
    this.reconnectAttempts++;
    const delay = this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1);

    console.log(`Scheduling reconnect attempt ${this.reconnectAttempts} in ${delay}ms`);

    setTimeout(() => {
      console.log(`Reconnecting (attempt ${this.reconnectAttempts})...`);
      this.connect();
    }, delay);
  }

  /**
   * Returns an observable that emits whenever a new chat message is received.
   * Now includes device information.
   */
  onNewMessage(): Observable<NewMessageEvent> {
    return this.newMessage$.asObservable();
  }

  /**
   * Returns an observable for connection status changes
   */
  onConnectionStatus(): Observable<'connected' | 'disconnected' | 'error'> {
    return this.connectionStatus$.asObservable();
  }

  /**
   * Check if WebSocket is currently connected
   */
  isConnected(): boolean {
    return this.socket?.readyState === WebSocket.OPEN;
  }

  /**
   * Send a message through the WebSocket (for typing indicators, read receipts, etc.)
   */
  send(message: any): void {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(message));
    } else {
      console.warn('WebSocket is not connected, cannot send message');
    }
  }

  /**
   * Disconnects the chat WebSocket.
   */
  disconnect() {
    if (this.socket) {
      this.socket.close(1000, 'Client disconnecting');
      this.socket = undefined;
    }
  }
}
