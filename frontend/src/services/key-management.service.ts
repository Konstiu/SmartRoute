import { Injectable } from '@angular/core';
import { SecureStoragePlugin } from 'capacitor-secure-storage-plugin';
import nacl from 'tweetnacl';
import naclUtil from 'tweetnacl-util';
import { Globals } from '../global/globals';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export interface OneTimePreKey {
  uuid: string;
  publicKey: string;
}

@Injectable({
  providedIn: 'root'
})
export class KeyManagementService {

  private authBaseUri: string = this.globals.backendUri + '/communication';

  constructor(
    private globals: Globals,
    private httpClient: HttpClient
  ) {}

  // constants for storage names
  static IDENTITY_PUBLIC_KEY = 'identity_public_key';
  static IDENTITY_PRIVATE_KEY = 'identity_private_key';
  static SIGNED_PRE_KEY_PUBLIC = 'signed_pre_key_public';
  static SIGNED_PRE_KEY_PRIVATE = 'signed_pre_key_private';
  static SIGNED_PRE_KEY_SIGNATURE = 'signed_pre_key_signature';
  static SIGNED_PRE_KEY_TIMESTAMP = 'signed_pre_key_timestamp';
  static ONE_TIME_PRE_KEY_PREFIX = 'one_time_pre_key_';

  static ONE_TIME_PRE_KEYS_BATCH_SIZE = 150;

  /**
   * Safely retrieves a value from secure storage by key.
   * Returns null if the key does not exist or an error occurs.
   * 
   * @param key The key to retrieve from secure storage
   * @returns A promise resolving to the stored value or null
   */
  private async getFromStorageSafe(key: string): Promise<{ value: string } | null> {
    try {
      const result = await SecureStoragePlugin.get({ key: key });
      return result;
    } catch {
      return null;
    }
  }

  /**
   * Get the identity key pair from secure storage
   * 
   * @returns The identity key pair or null if not found
   */
  async getIdentityKey(): Promise<{publicKey: Uint8Array, privateKey: Uint8Array} | null> {
    try {
      const publicKeyResult = await this.getFromStorageSafe(KeyManagementService.IDENTITY_PUBLIC_KEY);
      const privateKeyResult = await this.getFromStorageSafe(KeyManagementService.IDENTITY_PRIVATE_KEY);

      if (!publicKeyResult || !publicKeyResult.value || !privateKeyResult || !privateKeyResult.value) {
        console.log('Identity key not found');
        return null;
      }

      return {
        publicKey: naclUtil.decodeBase64(publicKeyResult.value),
        privateKey: naclUtil.decodeBase64(privateKeyResult.value)
      };
    } catch (_) {
      console.log('Identity key not found');
      return null;
    }
  }

  /**
   * Get the public identity key from secure storage
   * 
   * @returns The public identity key as a string or null if not found
   */
  async getPublicIdentityKey(): Promise<string | null> {
    try {
      const result = await this.getFromStorageSafe(KeyManagementService.IDENTITY_PUBLIC_KEY);
      if (!result || !result.value) {
        return null;
      }
      return result.value;
    } catch (_) {
      return null;
    }
  }

  /**
   * Generate a new Curve25519 identity key pair, store it securely.
   * 
   * @returns a Promise that resolves when the operation is complete
   */
  async generateAndStoreIdentityKey(): Promise<void> {

    // Generate new Curve25519 key pair
    const keyPair = nacl.sign.keyPair();
    
    // Convert to Base64 for storage
    const publicKey = naclUtil.encodeBase64(keyPair.publicKey);
    const privateKey = naclUtil.encodeBase64(keyPair.secretKey);

    // Store securely
    await SecureStoragePlugin.set({
      key: KeyManagementService.IDENTITY_PRIVATE_KEY,
      value: privateKey
    });

    await SecureStoragePlugin.set({
      key: KeyManagementService.IDENTITY_PUBLIC_KEY,
      value: publicKey
    });
  }

  /**
   * Upload the public identity key to the backend.
   */
  async uploadPublicIdentityKey(): Promise<void> {
    const publicKey = await this.getPublicIdentityKey();
    if (!publicKey) {
      throw new Error('No public identity key found');
    }
    const payload = { publicKey: publicKey };
    await firstValueFrom(this.httpClient.put<void>(`${this.authBaseUri}/upload-identity-key`, payload));
  }

  /**
   * Delete the identity key pair from secure storage.
   * This is used on account deletion
   */
  async deleteIdentityKey(): Promise<void> {
    try {
      await SecureStoragePlugin.remove({ key: KeyManagementService.IDENTITY_PRIVATE_KEY });
      await SecureStoragePlugin.remove({ key: KeyManagementService.IDENTITY_PUBLIC_KEY });
    } catch (error) {
      console.error('Error on deleteIdentityKey:', error);
    }
  }

  /**
   * Checks if the signed pre-key is older than 7 days.
   * If so, a new one is generated, signed with the identity key and stored.
   * Returns true if a new signed pre-key was generated, false otherwise.
   */
  async updateSignedPreKeyIfNecessary(): Promise<boolean> {
    const signedPreKeyTimestampResult = await this.getFromStorageSafe(KeyManagementService.SIGNED_PRE_KEY_TIMESTAMP);
    const now = Date.now();
    if (signedPreKeyTimestampResult && signedPreKeyTimestampResult.value) {
      const timestamp = parseInt(signedPreKeyTimestampResult.value, 10);
      const sevenDaysInMs = 7 * 24 * 60 * 60 * 1000;
      if (now - timestamp < sevenDaysInMs) {
        // Signed pre-key is still valid
        return false;
      }
    }

    // Generate new signed pre-key
    const preKeyPair = nacl.box.keyPair();
    
    // Get identity key
    const identityKey = await this.getIdentityKey();
    if (!identityKey) {
      throw new Error('Identity key not found, cannot generate signed pre-key');
    }

    // Sign the pre-key's public key with the identity private key
    const preKeySignature = nacl.sign.detached(preKeyPair.publicKey, identityKey.privateKey);

    // Store signed pre-key and signature
    const preKeyPublicBase64 = naclUtil.encodeBase64(preKeyPair.publicKey);
    const preKeyPrivateBase64 = naclUtil.encodeBase64(preKeyPair.secretKey);
    const preKeySignatureBase64 = naclUtil.encodeBase64(preKeySignature);

    await SecureStoragePlugin.set({
      key: KeyManagementService.SIGNED_PRE_KEY_PUBLIC,
      value: preKeyPublicBase64
    });
    await SecureStoragePlugin.set({
      key: KeyManagementService.SIGNED_PRE_KEY_PRIVATE,
      value: preKeyPrivateBase64
    });
    await SecureStoragePlugin.set({
      key: KeyManagementService.SIGNED_PRE_KEY_SIGNATURE,
      value: preKeySignatureBase64
    });
    await SecureStoragePlugin.set({
      key: KeyManagementService.SIGNED_PRE_KEY_TIMESTAMP,
      value: now.toString()
    });

    return true;
  }

  /**
   * Upload the public signed pre-key to the backend.
   */
  async uploadPublicSignedPreKey(): Promise<void> {
    const publicKeyResult = await this.getFromStorageSafe(KeyManagementService.SIGNED_PRE_KEY_PUBLIC);
    const signatureResult = await this.getFromStorageSafe(KeyManagementService.SIGNED_PRE_KEY_SIGNATURE);

    if (!publicKeyResult || !publicKeyResult.value || !signatureResult || !signatureResult.value) {
      throw new Error('No signed pre-key found');
    }

    const payload = {
      publicPreKey: publicKeyResult.value,
      signature: signatureResult.value
    };
    await firstValueFrom(this.httpClient.put<void>(`${this.authBaseUri}/upload-signed-pre-key`, payload));
  }

  /**
   * Generate and store one-time pre-keys if the count on the backend is below the threshold.
   */
  async generateStoreAndUploadOneTimePreKeysIfNecessary(): Promise<void> {
    const count = await this.getOneTimePreKeysCount();
    if (count < KeyManagementService.ONE_TIME_PRE_KEYS_BATCH_SIZE) {
      const keysToGenerate = KeyManagementService.ONE_TIME_PRE_KEYS_BATCH_SIZE - count;
      const newPreKeys = await this.generateOneTimePreKeys(keysToGenerate);
      await this.uploadOneTimePreKeys(newPreKeys);
    }
  }

  /**
   * Get the count of one-time pre-keys stored on the backend.
   * 
   * @returns The number of one-time pre-keys stored on the backend 
   */
  async getOneTimePreKeysCount(): Promise<number> {
    return await firstValueFrom(this.httpClient.get<number>(`${this.authBaseUri}/amount-of-one-time-pre-keys`));
  }

  /**
   * Generate a batch of one-time pre-keys and store them securely.
   * These keys can be uploaded to the backend for use in establishing sessions.
   * 
   * @param numberOfKeys The number of one-time pre-keys to generate
   * @returns An array of generated one-time pre-keys with their UUIDs and public keys
   */
  async generateOneTimePreKeys(numberOfKeys: number): Promise<OneTimePreKey[]> {
    const oneTimePreKeys: OneTimePreKey[] = [];
    for (let i = 0; i < numberOfKeys; i++) {
      const keyPair = nacl.box.keyPair();
      const publicKeyBase64 = naclUtil.encodeBase64(keyPair.publicKey);
      const privateKeyBase64 = naclUtil.encodeBase64(keyPair.secretKey);
      const uuid = crypto.randomUUID();
      oneTimePreKeys.push({ uuid: uuid, publicKey: publicKeyBase64 });
      // Store each one-time pre-key securely with a unique key
      const keyName = KeyManagementService.ONE_TIME_PRE_KEY_PREFIX + uuid;
      await SecureStoragePlugin.set({
        key: keyName,
        value: JSON.stringify({ uuid: uuid, publicKey: publicKeyBase64, privateKey: privateKeyBase64 })
      });
    }

    return oneTimePreKeys;
  }

  /**
   * Upload a batch of one-time pre-keys to the backend.
   * 
   * @param preKeys 
   */
  async uploadOneTimePreKeys(preKeys: OneTimePreKey[]): Promise<void> {
    const payload = { oneTimePreKeys: preKeys };
    await firstValueFrom(this.httpClient.put<void>(`${this.authBaseUri}/upload-one-time-pre-keys`, payload));
  }

}