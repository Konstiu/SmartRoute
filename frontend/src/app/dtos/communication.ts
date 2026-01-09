/**
 * Encrypted message structure used in Double Ratchet
 */
export interface EncryptedMessage {
  ciphertext: string;  // Base64
  nonce: string;  // Base64
  messageNumber: number;
  ratchetPublicKey: string;  // Base64
}

/**
 * One-time pre-key with unique identifier
 */
export interface OneTimePreKeyDto {
  uuid: string;
  publicKey: string;  // Base64
}


/**
 * Key bundle for a single device
 */
export interface DeviceKeyBundleDto {
  deviceId: string;

  identityKey: string;  // Base64 - Ed25519 signing key
  identityDhKey: string;  // Base64 - Curve25519 DH key
  signedPreKey: string;  // Base64
  signedPreKeySignature: string;  // Base64
  oneTimePreKey: OneTimePreKeyDto | null;
}

/**
 * All key bundles for all devices of a friend
 */
export interface FriendDeviceBundlesDto {
  friendEmail: string;
  devices: DeviceKeyBundleDto[];
}

/**
 * Complete message details for sending/receiving
 */
export interface MessageDetailDto {
  id?: number;
  senderEmail?: string;
  senderDeviceId?: string;
  recipientEmail: string;
  recipientDeviceId: string;

  // Only present in first message (X3DH initialization)
  senderIdentityKey: string | null;  // Base64
  senderIdentityDhKey: string | null;  // Base64
  senderEphemeralKey: string | null;  // Base64
  usedOneTimePreKeyId: string | null;

  // Always present
  encryptedMessage: EncryptedMessage;
  timestamp?: string;  // ISO 8601
}

/**
 * Upload identity key payload
 */
export interface UploadIdentityDto {
  deviceId: string;
  publicKey: string;  // Base64 - Ed25519
  publicDhKey: string;  // Base64 - Curve25519
}

/**
 * Upload signed pre-key payload
 */
export interface UploadPreKeyDto {
  deviceId: string;
  publicPreKey: string;  // Base64
  signature: string;  // Base64
}

/**
 * Upload one-time pre-keys payload
 */
export interface UploadOneTimePreKeysDto {
  deviceId: string;
  oneTimePreKeys: OneTimePreKeyDto[];
}
