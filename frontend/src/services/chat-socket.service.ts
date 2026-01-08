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

  /**
   * Connects to the chat WebSocket for a specific friend.
   * 
   * @param friendId The email of the friend to connect the chat socket with.
   */
  connect(friendId: string) {
    const token = this.authService.getToken();
    if (!token) {
      throw new Error('User is not authenticated');
    }
    this.socket = new WebSocket(`${this.chatWsUri}?token=${encodeURIComponent(token)}&friendId=${encodeURIComponent(friendId)}`);

    this.socket.onmessage = msg => {
      console.log('Message via WS:', msg.data);
      if (msg.data === 'NEW_MESSAGE') {
        // Notify subscribers about the new message
        this.newMessage$.next();
      }
    };
  }

  /**
   * Returns an observable that emits whenever a new chat message is received.
   * 
   * @returns An observable that emits void when a new message is received.
   */
  onNewMessage(): Observable<void> {
    return this.newMessage$.asObservable();
  }

  /**
   * Disconnects the chat WebSocket.
   */
  disconnect() {
    this.socket?.close();
    this.socket = undefined;
  }
}
