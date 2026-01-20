import {Injectable} from "@angular/core";
import {KeyManagementService} from "./key-management.service";
import {ChatSocketService} from "./chat-socket.service";

@Injectable({providedIn: 'root'})
export class ChatMessageService {

  constructor(
    private keyManagementService: KeyManagementService,
    private chatSocketService: ChatSocketService
  ) {
  }

  /**
   * Send text message
   *
   * @param messageText
   * @param friendEmail
   * @returns
   */
  async sendTextMessage(
    messageText: string,
    friendEmail: string,
  ) {
    if (!messageText.trim()) return;

    const messageJson = JSON.stringify({
      type: 'text',
      text: messageText,
    });

    await this.sendMessage(messageJson, friendEmail);
  }

  /**
   * Send location message
   *
   * @param messageText
   * @param latitude
   * @param longitude
   * @param friendEmail
   */
  async sendLocationMessage(
    messageText: string,
    latitude: number,
    longitude: number,
    friendEmail: string
  ) {
    const messageJson = JSON.stringify({
      type: 'location',
      text: messageText,
      latitude: latitude,
      longitude: longitude
    });

    await this.sendMessage(messageJson, friendEmail);
  }


  async sendRouteMessage(
    routeId: string,
    routeName: string,
    description: string,
    friendEmail: string
  ) {
    const messageJson = JSON.stringify({
      type: 'route',
      text: description,
      routeId: routeId,
      routeName: routeName
    });

    await this.sendMessage(messageJson, friendEmail);
  }

  private async sendMessage(messageJson: string, friendEmail: string) {
    const mySocketId = this.chatSocketService.getCurrentSocketId();
    const conversationMessageId = crypto.randomUUID(); // Generate ONCE

    // Send to all of friend's devices
    const sentMessages = await this.keyManagementService.sendMessageToFriend(
      friendEmail,
      messageJson,
      conversationMessageId,
      mySocketId
    );

    console.log(`Message sent to ${sentMessages.length} devices`);

    const sentMessagesMyDevices = await this.keyManagementService.sendMessageToMyDevices(
      friendEmail,
      messageJson,
      conversationMessageId,
      mySocketId
    )

    console.log(`Message sent to ${sentMessagesMyDevices.length} of my devices`);

  }

}
