import { Injectable } from '@angular/core';
import { SecureStoragePlugin } from 'capacitor-secure-storage-plugin';
import nacl from 'tweetnacl';
import naclUtil from 'tweetnacl-util';
import { Globals } from '../global/globals';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, Observable } from 'rxjs';
import { KeysDto, OneTimePreKeyDto } from 'src/app/dtos/communication';

interface KeyPair {
  publicKey: Uint8Array,
  privateKey: Uint8Array
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
  async getIdentityKey(): Promise<KeyPair | null> {
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
  async generateOneTimePreKeys(numberOfKeys: number): Promise<OneTimePreKeyDto[]> {
    const oneTimePreKeys: OneTimePreKeyDto[] = [];
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
   * Delete a one-time pre-key from secure storage after it has been used.
   * 
   * @param uuid the UUID of the one-time pre-key to delete 
   */
  async deleteOneTimePreKey(uuid: string): Promise<void> {
    const keyName = KeyManagementService.ONE_TIME_PRE_KEY_PREFIX + uuid;
    try {
      await SecureStoragePlugin.remove({ key: keyName });
    }
    catch (_) {}
  }

  /**
   * Upload a batch of one-time pre-keys to the backend.
   * 
   * @param preKeys 
   */
  async uploadOneTimePreKeys(preKeys: OneTimePreKeyDto[]): Promise<void> {
    const payload = { oneTimePreKeys: preKeys };
    await firstValueFrom(this.httpClient.put<void>(`${this.authBaseUri}/upload-one-time-pre-keys`, payload));
  }

  /**
   * Get the communication keys of a friend by their email.
   * 
   * @param friendEmail the email of the friend whose keys are to be retrieved
   * @returns An observable that yields the friend's communication keys
   */
  getKeysOfFriend(friendEmail: string): Observable<KeysDto> {
    return this.httpClient.get<KeysDto>(`${this.authBaseUri}/keys-of-friend/${encodeURIComponent(friendEmail)}`);
  } 

  /**
   * Performs the X3DH (Extended Triple Diffie-Hellman) key agreement.
   *
   * This method derives a shared secret between the local client and a remote party
   * using the remote party's public keys and the local client's identity and
   * ephemeral key pairs, according to the X3DH protocol.
   *
   * If no ephemeral key pair is provided, a new one is generated internally.
   * The derived shared secret can be used as input for a symmetric ratchet
   * (e.g. Double Ratchet).
   *
   * @param friendKeysDto
   *   The remote party's public key material, including identity key,
   *   signed pre-key, and one-time pre-key (if available).
   *
   * @param myIdentityKeyPair
   *   The local client's long-term identity key pair.
   *
   * @param myEphemeralKeyPair
   *   Optional local ephemeral key pair. If omitted, a new ephemeral key pair
   *   will be generated for this session.
   *
   * @returns
   *   An object containing:
   *   - sharedSecret: The derived shared secret as a Uint8Array
   *   - ephemeralPublicKey: The public key of the ephemeral key pair used in the exchange
   */
  async performX3DH(friendKeysDto: KeysDto, myIdentityKeyPair: KeyPair, myEphemeralKeyPair?: KeyPair): 
    Promise<{ sharedSecret: Uint8Array, ephemeralPublicKey: Uint8Array }> {
      
    // verify the signature of the prekey
    const isValid = this.verifySignatureOfPreKey(
      friendKeysDto.signedPreKey,
      friendKeysDto.signedPreKeySignature,
      friendKeysDto.identityKey
    );

    if (!isValid) {
      throw new Error('Invalid signed prekey signature!');
    }

    // generate ephemeral key pair if not provided
    if (!myEphemeralKeyPair) {
      const generatedKeyPair = nacl.box.keyPair();
      myEphemeralKeyPair = {
        publicKey: generatedKeyPair.publicKey,
        privateKey: generatedKeyPair.secretKey
      };
    }

    // decode friend's keys from base64
    // and declare shorter variable names
    const IK_A = myIdentityKeyPair;
    const EK_A = myEphemeralKeyPair;
    const IK_B = naclUtil.decodeBase64(friendKeysDto.identityKey);
    const SPK_B = naclUtil.decodeBase64(friendKeysDto.signedPreKey);
    const OPK_B = friendKeysDto.oneTimePreKey ? naclUtil.decodeBase64(friendKeysDto.oneTimePreKey.publicKey) : null;

    // perform diffie hellmann operations
    const dh1 = nacl.scalarMult(IK_A.privateKey, SPK_B);  // IK_A * SPK_B
    const dh2 = nacl.scalarMult(EK_A.privateKey, IK_B);   // EK_A * IK_B
    const dh3 = nacl.scalarMult(EK_A.privateKey, SPK_B);  // EK_A * SPK_B

    let dhResult: Uint8Array;
    if (OPK_B) {
      // with One-Time Prekey
      const dh4 = nacl.scalarMult(EK_A.privateKey, OPK_B); // EK_A * OPK_B
      dhResult = this.concatenate([dh1, dh2, dh3, dh4]);
    } else {
      // without One-Time Prekey
      dhResult = this.concatenate([dh1, dh2, dh3]);
    }

    // derive shared secret from concatenated DH results
    // take first 32 bytes
    const sharedSecret = nacl.hash(dhResult).slice(0, 32);

    return {
      sharedSecret,
      ephemeralPublicKey: EK_A.publicKey
    };
  }

  /**
   * Completes the X3DH (Extended Triple Diffie-Hellman) key agreement
   * from the receiver's perspective.
   *
   * This method derives the shared secret using the remote party's
   * identity and ephemeral public keys together with the local client's
   * identity key pair, signed pre-key pair, and (optionally) one-time
   * pre-key pair, following the X3DH protocol.
   *
   * If a one-time pre-key was used by the initiator, it must be provided
   * and will typically be deleted after successful key derivation.
   *
   * The resulting shared secret can be used as input for a symmetric
   * ratchet (e.g. Double Ratchet).
   *
   * @param friendIdentityKey
   *   The remote party's public identity key (Base64-encoded).
   *
   * @param friendEphemeralKey
   *   The remote party's ephemeral public key used for this X3DH exchange
   *   (Base64-encoded).
   *
   * @param usedOneTimePreKey
   *   The one-time pre-key that was used by the initiator, or null if no
   *   one-time pre-key was used.
   *
   * @param myIdentityKeyPair
   *   The local client's long-term identity key pair.
   *
   * @param mySignedPreKeyPair
   *   The local client's signed pre-key pair.
   *
   * @param myOneTimePreKeyPair
   *   The local client's one-time pre-key pair, or null if none was used.
   *
   * @returns
   *   An object containing:
   *   - sharedSecret: The derived shared secret as a Uint8Array
   */
  async receiveX3DH(
    friendIdentityKey: string,
    friendEphemeralKey: string,
    usedOneTimePreKey: OneTimePreKeyDto | null,
    myIdentityKeyPair: KeyPair,
    mySignedPreKeyPair: KeyPair,
    myOneTimePreKeyPair: KeyPair | null
  ): Promise<{ sharedSecret: Uint8Array }> {

    // decode friend's keys from base64
    // and declare shorter variable names
    const IK_B = myIdentityKeyPair;
    const SPK_B = mySignedPreKeyPair;
    const OPK_B = myOneTimePreKeyPair;
    const IK_A = naclUtil.decodeBase64(friendIdentityKey);
    const EK_A = naclUtil.decodeBase64(friendEphemeralKey);

    // reverse diffie hellmann operations
    const dh1 = nacl.scalarMult(SPK_B.privateKey, IK_A);  // SPK_B * IK_A
    const dh2 = nacl.scalarMult(IK_B.privateKey, EK_A);   // IK_B * EK_A
    const dh3 = nacl.scalarMult(SPK_B.privateKey, EK_A);  // SPK_B * EK_A

    let dhResult: Uint8Array;
    if (usedOneTimePreKey && OPK_B) {
      // with One-Time Prekey
      const dh4 = nacl.scalarMult(OPK_B.privateKey, EK_A); // OPK_B * EK_A
      dhResult = this.concatenate([dh1, dh2, dh3, dh4]);

      // delete used one-time prekey from storage
      await this.deleteOneTimePreKey(usedOneTimePreKey.uuid);
    } else {
      // without One-Time Prekey
      dhResult = this.concatenate([dh1, dh2, dh3]);
    }

    // derive shared secret from concatenated DH results
    const sharedSecret = nacl.hash(dhResult).slice(0, 32);

    return { sharedSecret };
  }


  /**
   * Verify the signature of the signed pre key.
   * 
   * @param signedPreKey the friend's signed prekey in base64
   * @param signature the signature of the signed prekey in base64
   * @param publicIdentityKey the friend's public identity key
   * @returns true if the signature is valid, false otherwise
   */
  private verifySignatureOfPreKey(signedPreKey: string, signature: string, publicIdentityKey: string): boolean {
    try {
      const message = naclUtil.decodeBase64(signedPreKey);
      const sig = naclUtil.decodeBase64(signature);
      const publicKey = naclUtil.decodeBase64(publicIdentityKey);
      return nacl.sign.detached.verify(message, sig, publicKey);
    } catch (error) {
      console.error('Signature verification failed:', error);
      return false;
    }
  }

  /**
   * Concatenate multiple Uint8Array instances into one.
   * 
   * @param arrays An array of Uint8Array instances to concatenate 
   * @returns A single Uint8Array containing all input arrays concatenated 
   */
  private concatenate(arrays: Uint8Array[]): Uint8Array {
    const totalLength = arrays.reduce((sum, arr) => sum + arr.length, 0);
    const result = new Uint8Array(totalLength);
    let offset = 0;
    
    for (const arr of arrays) {
      result.set(arr, offset);
      offset += arr.length;
    }
    
    return result;
  }



}