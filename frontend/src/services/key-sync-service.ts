import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { db } from '../db/encryption';
import { Globals } from '../global/globals';
import { firstValueFrom } from 'rxjs';

export interface SyncSession {
  sessionId: string;
  sessionKey: string;
  version?: string;
  expiresAt: number | string;
}

export interface SyncPayload {
  sessionId: string;
  encryptedData: string;
  timestamp: number;
}

@Injectable({
  providedIn: 'root'
})
export class KeySyncService {
  private SYNC_API_URL: string;

  constructor(
    private http: HttpClient,
    private globals: Globals,
  ) {
    this.SYNC_API_URL = this.globals.backendUri + '/key-sync';
  }

  /**
   * Check if this device has encryption keys
   */
  async hasEncryptionKeys(): Promise<boolean> {
    try {
      // Check both IndexedDB and localStorage
      const ratchetStates = await db.ratchetStates.count();
      const hasLocalStorageKeys = !!(
        localStorage.getItem('cap_sec_identity_dh_private_key') &&
        localStorage.getItem('cap_sec_identity_dh_public_key')
      );

      return ratchetStates > 0 || hasLocalStorageKeys;
    } catch (error) {
      console.error('Error checking for keys:', error);
      return false;
    }
  }

  /**
   * DEVICE B (has keys): Generate a sync session and upload encrypted keys
   * This creates a new session WITH keys already uploaded
   */
  async generateSyncSession(): Promise<SyncSession> {
    try {
      // 1. Export entire encryption database
      const dbExport = await this.exportDatabase();

      // 2. Generate session credentials
      const sessionKey = this.generateSessionKey();
      const sessionId = this.generateSessionId();

      // 3. Encrypt the database export
      const encryptedData = await this.encryptData(dbExport, sessionKey);

      // 4. Upload to backend
      await this.uploadToBackend(sessionId, encryptedData);

      // 5. Return QR code payload
      return {
        sessionId,
        sessionKey,
        version: '1.0',
        expiresAt: Date.now() + 5 * 60 * 1000 // 5 minutes
      };
    } catch (error) {
      console.error('Error generating sync session:', error);
      throw error;
    }
  }

  /**
   * DEVICE A (no keys): Create an empty session to request keys
   * This creates a session WITHOUT keys - waiting for another device to upload
   */
  async createEmptySession(): Promise<SyncSession> {
    try {
      const sessionId = this.generateSessionId();
      const sessionKey = this.generateSessionKey();

      // Create empty session on backend
      const response = await firstValueFrom(
        this.http.post<SyncSession>(`${this.SYNC_API_URL}/create-session`, {
          sessionId,
          sessionKey
        })
      );

      return {
        ...response,
        version: '1.0',
        expiresAt: Date.now() + 5 * 60 * 1000
      };
    } catch (error) {
      console.error('Error creating empty session:', error);
      throw error;
    }
  }

  /**
   * Check if a session has keys uploaded (for polling)
   */
  async checkSessionHasKeys(sessionId: string): Promise<boolean> {
    try {
      const response = await firstValueFrom(
        this.http.get<{ hasKeys: boolean }>(`${this.SYNC_API_URL}/check/${sessionId}`)
      );
      return response.hasKeys;
    } catch (error) {
      console.error('Error checking session:', error);
      return false;
    }
  }

  /**
   * DEVICE B (has keys): Upload keys to an existing session (when scanning a request QR)
   */
  async uploadKeysToSession(sessionData: SyncSession): Promise<void> {
    try {
      // 1. Export entire encryption database
      const dbExport = await this.exportDatabase();

      // 2. Encrypt the database export using the session key from QR code
      const encryptedData = await this.encryptData(dbExport, sessionData.sessionKey);

      // 3. Upload to the specific session
      await firstValueFrom(
        this.http.put(`${this.SYNC_API_URL}/session/${sessionData.sessionId}/keys`, {
          encryptedData,
          timestamp: Date.now()
        })
      );
    } catch (error) {
      console.error('Error uploading keys to session:', error);
      throw error;
    }
  }

  /**
   * DEVICE A (no keys): Download and import keys from a session
   */
  async downloadAndImportKeys(sessionData: SyncSession): Promise<void> {
    try {
      // Check if session is expired
      const expiresAt = typeof sessionData.expiresAt === 'string'
        ? new Date(sessionData.expiresAt).getTime()
        : sessionData.expiresAt;

      if (Date.now() > expiresAt) {
        throw new Error('Sync session has expired');
      }

      // Download encrypted data from backend
      const encryptedData = await this.downloadFromBackend(sessionData.sessionId);

      // Decrypt the data using the session key from QR code
      const decryptedData = await this.decryptData(encryptedData, sessionData.sessionKey);

      // Import into local database
      await this.importDatabase(decryptedData);

      console.log('Keys imported successfully');
    } catch (error) {
      console.error('Error downloading keys:', error);
      throw error;
    }
  }

  /**
   * LEGACY: Import keys from a scanned session (old flow)
   * This is kept for backward compatibility
   */
  async importFromSession(sessionData: SyncSession): Promise<void> {
    return this.downloadAndImportKeys(sessionData);
  }

  /**
   * Export the entire encryption database
   */
  private async exportDatabase(): Promise<any> {
    const ratchetStates = await db.ratchetStates.toArray();
    const conversations = await db.conversations.toArray();
    const messages = await db.messages.toArray();

    // Also export localStorage keys if they exist
    const privateKey = localStorage.getItem('encryptionPrivateKey');
    const publicKey = localStorage.getItem('encryptionPublicKey');

    return {
      ratchetStates,
      conversations,
      messages,
      localStorage: {
        encryptionPrivateKey: privateKey,
        encryptionPublicKey: publicKey
      },
      exportedAt: new Date().toISOString()
    };
  }

  /**
   * Import data into the encryption database
   */
  private async importDatabase(data: any): Promise<void> {
    // Import ratchet states
    if (data.ratchetStates && data.ratchetStates.length > 0) {
      await db.ratchetStates.bulkPut(data.ratchetStates);
    }

    // Import conversations
    if (data.conversations && data.conversations.length > 0) {
      await db.conversations.bulkPut(data.conversations);
    }

    // Import messages
    if (data.messages && data.messages.length > 0) {
      await db.messages.bulkPut(data.messages);
    }

    // Import localStorage keys if they exist
    if (data.localStorage) {
      if (data.localStorage.encryptionPrivateKey) {
        localStorage.setItem('encryptionPrivateKey', data.localStorage.encryptionPrivateKey);
      }
      if (data.localStorage.encryptionPublicKey) {
        localStorage.setItem('encryptionPublicKey', data.localStorage.encryptionPublicKey);
      }
    }
  }

  /**
   * Encrypt data using Web Crypto API (AES-GCM)
   */
  private async encryptData(data: any, keyHex: string): Promise<string> {
    // Convert data to JSON string
    const jsonString = JSON.stringify(data, this.replacer);
    const dataBuffer = new TextEncoder().encode(jsonString);

    // Convert hex key to CryptoKey
    const keyBuffer = this.hexToBuffer(keyHex);
    const cryptoKey = await crypto.subtle.importKey(
      'raw',
      keyBuffer,
      { name: 'AES-GCM' },
      false,
      ['encrypt']
    );

    // Generate random IV
    const iv = crypto.getRandomValues(new Uint8Array(12));

    // Encrypt
    const encryptedBuffer = await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv },
      cryptoKey,
      dataBuffer
    );

    // Combine IV + encrypted data
    const combined = new Uint8Array(iv.length + encryptedBuffer.byteLength);
    combined.set(iv, 0);
    combined.set(new Uint8Array(encryptedBuffer), iv.length);

    // Convert to base64
    return this.bufferToBase64(combined);
  }

  /**
   * Decrypt data using Web Crypto API (AES-GCM)
   */
  private async decryptData(encryptedBase64: string, keyHex: string): Promise<any> {
    // Convert base64 to buffer
    const combined = this.base64ToBuffer(encryptedBase64);

    // Extract IV and encrypted data
    const iv = combined.slice(0, 12);
    const encryptedData = combined.slice(12);

    // Convert hex key to CryptoKey
    const keyBuffer = this.hexToBuffer(keyHex);
    const cryptoKey = await crypto.subtle.importKey(
      'raw',
      keyBuffer,
      { name: 'AES-GCM' },
      false,
      ['decrypt']
    );

    // Decrypt
    const decryptedBuffer = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv },
      cryptoKey,
      encryptedData
    );

    // Convert back to object
    const jsonString = new TextDecoder().decode(decryptedBuffer);
    return JSON.parse(jsonString, this.reviver);
  }

  /**
   * Upload encrypted data to backend
   */
  private async uploadToBackend(sessionId: string, encryptedData: string): Promise<void> {
    const payload: SyncPayload = {
      sessionId,
      encryptedData,
      timestamp: Date.now()
    };

    await firstValueFrom(
      this.http.post(`${this.SYNC_API_URL}/upload`, payload)
    );
  }

  /**
   * Download encrypted data from backend
   */
  private async downloadFromBackend(sessionId: string): Promise<string> {
    const response = await firstValueFrom(
      this.http.get<{ encryptedData: string }>(`${this.SYNC_API_URL}/download/${sessionId}`)
    );

    return response.encryptedData;
  }

  /**
   * Generate a random 256-bit session key
   */
  private generateSessionKey(): string {
    const array = new Uint8Array(32);
    crypto.getRandomValues(array);
    return Array.from(array)
      .map(b => b.toString(16).padStart(2, '0'))
      .join('');
  }

  /**
   * Generate a unique session ID
   */
  private generateSessionId(): string {
    return `sync_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  // Utility functions for serialization
  private replacer(key: string, value: any): any {
    if (value instanceof Uint8Array) {
      return {
        __type: 'Uint8Array',
        data: Array.from(value)
      };
    }
    if (value instanceof Date) {
      return {
        __type: 'Date',
        data: value.toISOString()
      };
    }
    return value;
  }

  private reviver(key: string, value: any): any {
    if (value && value.__type === 'Uint8Array') {
      return new Uint8Array(value.data);
    }
    if (value && value.__type === 'Date') {
      return new Date(value.data);
    }
    return value;
  }

  // Utility functions for crypto operations
  private hexToBuffer(hex: string): Uint8Array {
    const bytes = new Uint8Array(hex.length / 2);
    for (let i = 0; i < bytes.length; i++) {
      bytes[i] = parseInt(hex.substr(i * 2, 2), 16);
    }
    return bytes;
  }

  private bufferToBase64(buffer: Uint8Array): string {
    let binary = '';
    for (let i = 0; i < buffer.length; i++) {
      binary += String.fromCharCode(buffer[i]);
    }
    return btoa(binary);
  }

  private base64ToBuffer(base64: string): Uint8Array {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
  }
}
