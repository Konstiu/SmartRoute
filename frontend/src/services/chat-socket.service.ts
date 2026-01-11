import { inject, Injectable } from '@angular/core';
import { Globals } from 'src/global/globals';
import { AuthService } from './auth.service';
import { Observable, Subject } from 'rxjs';


@Injectable({ providedIn: 'root' })
export class ChatSocketService {
  private socket?: WebSocket;
  private globals = inject(Globals);
  private authService = inject(AuthService);

  private chatWsUri: string = this.globals.backendWsUri + '/chat';
  private newMessage$ = new Subject<void>();
  private connectionStatus$ = new Subject<'connected' | 'disconnected' | 'error'>();

  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 1000; // Start with 1 second

  private pingInterval?: ReturnType<typeof setInterval>;
  private readonly PING_INTERVAL_MS = 8000; // Send ping every 8 seconds (server timeout is 10s)

  private currentFriendId: string | null = null;

  
  async connect(friendId: string | null) {
    const token = this.authService.getToken();
    if (!token) {
      throw new Error('User is not authenticated');
    }
    if (!friendId) {
      throw new Error('Friend ID is required to connect to chat WebSocket');
    }

    this.currentFriendId = friendId;

    // Close existing connection if any
    this.disconnect();

    const wsUrl = `${this.chatWsUri}?token=${encodeURIComponent(token)}&friendId=${encodeURIComponent(friendId)}`;
    console.log('Connecting to WebSocket:', wsUrl);

    this.socket = new WebSocket(wsUrl);

    this.socket.onopen = () => {
      console.log('WebSocket connected');
      this.connectionStatus$.next('connected');
      this.reconnectAttempts = 0;
      this.reconnectDelay = 1000;

      // start pinging to keep the connection alive
      this.startPingInterval();
    };

    this.socket.onmessage = (msg) => {
      console.log('Message via WS:', msg.data);

      if (msg.data === 'PONG') {
        // consume pong response
        return;
      } else if (msg.data === 'NEW_MESSAGE') {
        // Notify subscribers of new message
        this.newMessage$.next();
      }

    };

    this.socket.onerror = (error) => {
      console.error('WebSocket error:', error);
      this.connectionStatus$.next('error');
    };

    this.socket.onclose = (event) => {
      console.log('WebSocket closed:', event.code, event.reason);
      this.connectionStatus$.next('disconnected');

      // Stop ping interval
      this.stopPingInterval();

      // Attempt to reconnect if not a normal closure
      if (event.code !== 1000 && this.reconnectAttempts < this.maxReconnectAttempts) {
        this.scheduleReconnect();
      }
    };
  }

  /**
   * Start sending ping messages at regular intervals
   */
  private startPingInterval() {
    // Clear any existing interval
    this.stopPingInterval();

    this.pingInterval = setInterval(() => {
      if (this.socket?.readyState === WebSocket.OPEN) {
        console.log('Sending ping to server');
        this.socket.send('ping');
      }
    }, this.PING_INTERVAL_MS);
  }

  /**
   * Stop the ping interval
   */
  private stopPingInterval() {
    if (this.pingInterval) {
      clearInterval(this.pingInterval);
      this.pingInterval = undefined;
    }
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
      this.connect(this.currentFriendId);
    }, delay);
  }

  /**
   * Returns an observable that emits whenever a new chat message is received.
   * Now includes device information.
   */
  onNewMessage(): Observable<void> {
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
    // Stop ping interval
    this.stopPingInterval()

    // Close the socket if it exists
    if (this.socket) {
      this.socket.close(1000, 'Client disconnecting');
      this.socket = undefined;
    }
  }
}
