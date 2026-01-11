import Dexie, { Table } from 'dexie';

export interface KeyPair {
  publicKey: Uint8Array;
  privateKey: Uint8Array;
}

export interface RatchetState {
  rootKey: Uint8Array;                    // root key
  sendingChainKey: Uint8Array;            // for outgoing messages
  receivingChainKey: Uint8Array;          // for incoming messages
  myCurrentRatchetKeyPair: KeyPair;       // own current ratchet key pair
  theirCurrentRatchetPublicKey: Uint8Array | null; // partner's public key
  sendMessageNumber: number;              // counter for sent messages
  receiveMessageNumber: number;           // counter for received messages
  previousSendingChainLength: number;     // for out-of-order messages
}

// Updated to include device IDs
export interface RatchetStateWithDevices extends RatchetState {
  sessionId: string;  // Primary Key: "contactId:myDeviceId:theirDeviceId"
  contactId: string;  // Indexed for queries
  myDeviceId: string;
  theirDeviceId: string;
}

export interface Conversation {
  conversationId: string;  // Primary Key (contactId)
  contactId: string;
  contactName: string;
  lastMessageTimestamp: Date;
  lastMessagePreview: string;
  unreadCount: number;
  deviceSessions: DeviceSession[];  // Track all device pairs
}



export interface DeviceSession {
  myDeviceId: string;
  theirDeviceId: string;
  hasRatchetState: boolean;
  lastMessageTimestamp?: Date;
}

export interface Message {
  id: number;  // Primary Key (from backend)
  conversationId: string;  // contactId for grouping
  senderId: string;  // email
  senderDeviceId: string;
  recipientId: string;  // email
  recipientDeviceId: string;
  plaintext: string;
  timestamp: Date;  // Indexed
  direction: 'sent' | 'received';
  conversationMessageId: string;
}

// Store current device ID
export interface DeviceInfo {
  id: string;  // userEmail as Primary Key
  deviceId: string;
}

class EncryptionDatabase extends Dexie {
  ratchetStates!: Table<RatchetStateWithDevices, string>;
  messages!: Table<Message, number>;
  conversations!: Table<Conversation, string>;
  deviceInfo!: Table<DeviceInfo, string>;

  constructor() {
    super('EncryptionDB');

    this.version(2).stores({
      // sessionId as primary key, contactId indexed for queries
      ratchetStates: 'sessionId, contactId, [contactId+myDeviceId+theirDeviceId]',

      // Compound indexes for efficient queries
      messages: 'id, timestamp, conversationId, [conversationId+timestamp], [senderId+senderDeviceId+recipientId+recipientDeviceId]',

      conversations: 'conversationId, lastMessageTimestamp',

      deviceInfo: 'id'
    });
  }

  /**
   * Get or create the current device ID for a specific user
   * @param userEmail The email of the current user
   */
  async getCurrentDeviceId(userEmail: string): Promise<string> {
    if (!userEmail) {
      throw new Error('User email is required to get device ID');
    }

    let info = await this.deviceInfo.get(userEmail);

    console.log("previous device ID for", userEmail, ":", info?.deviceId);
    if (!info) {
      // Generate new device ID for this user
      const deviceId = crypto.randomUUID();
      await this.deviceInfo.put({ id: userEmail, deviceId });
      console.log("generated new device ID for", userEmail, ":", deviceId);
      return deviceId;
    }

    return info.deviceId;
  }

  /**
   * Get ratchet state for a specific device pair
   */
  async getRatchetState(
    contactId: string,
    myDeviceId: string,
    theirDeviceId: string
  ): Promise<RatchetStateWithDevices | undefined> {
    const sessionId = `${contactId}:${myDeviceId}:${theirDeviceId}`;
    return this.ratchetStates.get(sessionId);
  }

  /**
   * Save or update ratchet state for a device pair
   */
  async saveRatchetState(state: RatchetStateWithDevices): Promise<void> {
    const sessionId = `${state.contactId}:${state.myDeviceId}:${state.theirDeviceId}`;
    await this.ratchetStates.put({ ...state, sessionId });
  }

  /**
   * Get all messages with a friend (across all device pairs)
   */
  async getMessagesWithFriend(contactId: string): Promise<Message[]> {
    return this.messages
      .where('conversationId')
      .equals(contactId)
      .sortBy('timestamp');
  }

  /**
   * Get messages for a specific device pair
   */
  async getMessagesForDevicePair(
    contactId: string,
    myDeviceId: string,
    theirDeviceId: string
  ): Promise<Message[]> {
    const messages = await this.getMessagesWithFriend(contactId);

    return messages.filter(msg =>
      (msg.senderDeviceId === myDeviceId && msg.recipientDeviceId === theirDeviceId) ||
      (msg.senderDeviceId === theirDeviceId && msg.recipientDeviceId === myDeviceId)
    );
  }

  /**
   * Get all ratchet states for a contact (all device pairs)
   */
  async getRatchetStatesForContact(contactId: string): Promise<RatchetStateWithDevices[]> {
    return this.ratchetStates
      .where('contactId')
      .equals(contactId)
      .toArray();
  }
}

export const db = new EncryptionDatabase();
