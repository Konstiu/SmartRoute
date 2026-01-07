export interface OneTimePreKeyDto {
  uuid: string;
  publicKey: string;
}

export interface KeysDto {
  identityKey: string,
  identityDHKey: string,
  signedPreKey: string,
  signedPreKeySignature: string,
  oneTimePreKey: OneTimePreKeyDto | null
}

export interface EncryptedMessage {
  ciphertext: string;
  nonce: string;
  messageNumber: number;
  ratchetPublicKey: string;
}

export interface MessageDetailDto {
  id?: number;
  senderEmail?: string;
  recipientEmail: string;
  // on first message
  senderIdentityKey: string | null;
  senderIdentityDHKey: string | null;
  senderEphemeralKey: string | null;
  usedOneTimePreKeyId: string | null;
  // for every message
  encryptedMessage: EncryptedMessage;
  timestamp?: string;
}