import Dexie, { Table } from 'dexie';

export interface KeyPair {
  publicKey: Uint8Array,
  privateKey: Uint8Array
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

export interface RatchetStateWithContactId extends RatchetState {
  contactId: string;  // Primary Key
}

export interface Conversation {
  conversationId: string;  // Primary Key
  contactId: string;
  contactName: string;
  lastMessageTimestamp: Date;
  lastMessagePreview: string;
  unreadCount: number;
  hasRatchetState: boolean;
}

export interface Message {
  id: number;  // Primary Key
  conversationId: string;  // Indexed
  senderId: string;
  recipientId: string;
  plaintext: string;
  timestamp: Date;  // Indexed
  direction: 'sent' | 'received';
}

class EncryptionDatabase extends Dexie {
  ratchetStates!: Table<RatchetStateWithContactId, string>;
  messages!: Table<Message, string>;
  conversations!: Table<Conversation, string>;

  constructor() {
    super('EncryptionDB');
    
    this.version(1).stores({
      ratchetStates: 'contactId',
      messages: 'id, timestamp, [senderId+recipientId], [recipientId+senderId]',
      conversations: 'conversationId, contactId, lastMessageTimestamp',
    });
  }

  async getMessagesWithFriend(contactId: string): Promise<Message[]> {
    const myId = 'me';
    
    const sent = db.messages
      .where('[senderId+recipientId]')
      .equals([myId, contactId])
      .toArray();

    const received = db.messages
      .where('[recipientId+senderId]')
      .equals([myId, contactId])
      .toArray();

    const [s, r] = await Dexie.Promise.all([sent, received]);
    return [...s, ...r].sort(
      (a, b) => a.timestamp.getTime() - b.timestamp.getTime()
    );
  }

}


export const db = new EncryptionDatabase();