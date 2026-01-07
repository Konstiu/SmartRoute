import { Injectable } from '@angular/core';
import { SecureStoragePlugin } from 'capacitor-secure-storage-plugin';
import nacl from 'tweetnacl';
import naclUtil from 'tweetnacl-util';
import { Globals } from '../global/globals';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, Observable } from 'rxjs';
import { EncryptedMessage, KeysDto, MessageDetailDto, OneTimePreKeyDto } from 'src/app/dtos/communication';
import { db, KeyPair, Message, RatchetState } from 'src/db/encryption';



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
  static IDENTITY_DH_PUBLIC_KEY = 'identity_dh_public_key';
  static IDENTITY_DH_PRIVATE_KEY = 'identity_dh_private_key';
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
   * Get the DH identity key pair from secure storage
   * 
   * @returns The DH identity key pair or null if not found
   */
  async getIdentityDHKey(): Promise<KeyPair | null> {
    try {
      const publicKeyResult = await this.getFromStorageSafe(KeyManagementService.IDENTITY_DH_PUBLIC_KEY);
      const privateKeyResult = await this.getFromStorageSafe(KeyManagementService.IDENTITY_DH_PRIVATE_KEY);

      if (!publicKeyResult || !publicKeyResult.value || !privateKeyResult || !privateKeyResult.value) {
        console.log('DH identity key not found');
        return null;
      }

      return {
        publicKey: naclUtil.decodeBase64(publicKeyResult.value),
        privateKey: naclUtil.decodeBase64(privateKeyResult.value)
      };
    } catch (_) {
      console.log('DH identity key not found');
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
   * Get the public DH identity key from secure storage
   * 
   * @returns The public DH identity key as a string or null if not found
   */
  async getPublicIdentityDHKey(): Promise<string | null> {
    try {
      const result = await this.getFromStorageSafe(KeyManagementService.IDENTITY_DH_PUBLIC_KEY);
      if (!result || !result.value) {
        return null;
      }
      return result.value;
    } catch (_) {
      return null;
    }
  }

  /**
   * Generate new identity key pairs (both sign and DH), store them securely.
   * Sign key pair is used for signatures, DH key pair is used for X3DH.
   * 
   * @returns a Promise that resolves when the operation is complete
   */
  async generateAndStoreIdentityKey(): Promise<void> {

    // Generate new Ed25519 key pair for signatures
    const signKeyPair = nacl.sign.keyPair();
    
    // Generate new Curve25519 key pair for DH operations
    const dhKeyPair = nacl.box.keyPair();
    
    // Convert to Base64 for storage
    const signPublicKey = naclUtil.encodeBase64(signKeyPair.publicKey);
    const signPrivateKey = naclUtil.encodeBase64(signKeyPair.secretKey);
    const dhPublicKey = naclUtil.encodeBase64(dhKeyPair.publicKey);
    const dhPrivateKey = naclUtil.encodeBase64(dhKeyPair.secretKey);

    // Store sign keys securely
    await SecureStoragePlugin.set({
      key: KeyManagementService.IDENTITY_PRIVATE_KEY,
      value: signPrivateKey
    });

    await SecureStoragePlugin.set({
      key: KeyManagementService.IDENTITY_PUBLIC_KEY,
      value: signPublicKey
    });

    // Store DH keys securely
    await SecureStoragePlugin.set({
      key: KeyManagementService.IDENTITY_DH_PRIVATE_KEY,
      value: dhPrivateKey
    });

    await SecureStoragePlugin.set({
      key: KeyManagementService.IDENTITY_DH_PUBLIC_KEY,
      value: dhPublicKey
    });
  }

  /**
   * Upload the public identity keys (both sign and DH) to the backend.
   */
  async uploadPublicIdentityKey(): Promise<void> {
    const publicSignKey = await this.getPublicIdentityKey();
    const publicDHKey = await this.getPublicIdentityDHKey();
    if (!publicSignKey || !publicDHKey) {
      throw new Error('No public identity keys found');
    }
    const payload = { 
      publicKey: publicSignKey,
      publicDHKey: publicDHKey
    };
    await firstValueFrom(this.httpClient.put<void>(`${this.authBaseUri}/upload-identity-key`, payload));
  }

  /**
   * Delete the identity key pairs (both sign and DH) from secure storage.
   * This is used on account deletion
   */
  async deleteIdentityKey(): Promise<void> {
    try {
      await SecureStoragePlugin.remove({ key: KeyManagementService.IDENTITY_PRIVATE_KEY });
      await SecureStoragePlugin.remove({ key: KeyManagementService.IDENTITY_PUBLIC_KEY });
      await SecureStoragePlugin.remove({ key: KeyManagementService.IDENTITY_DH_PRIVATE_KEY });
      await SecureStoragePlugin.remove({ key: KeyManagementService.IDENTITY_DH_PUBLIC_KEY });
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
   * Get the signed pre-key pair from secure storage
   * @returns 
   */
  async getSignedPreKeyPair(): Promise<KeyPair | null> {
    try {
      const publicKeyResult = await this.getFromStorageSafe(KeyManagementService.SIGNED_PRE_KEY_PUBLIC);
      const privateKeyResult = await this.getFromStorageSafe(KeyManagementService.SIGNED_PRE_KEY_PRIVATE);
      if (!publicKeyResult || !publicKeyResult.value || !privateKeyResult || !privateKeyResult.value) {
        return null;
      }
      return {
        publicKey: naclUtil.decodeBase64(publicKeyResult.value),
        privateKey: naclUtil.decodeBase64(privateKeyResult.value)
      };
    } catch (_) {
      return null;
    }
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
   * Get a one-time pre-key pair by its UUID from secure storage.
   * 
   * @param uuid 
   * @returns 
   */
  async getOneTimePreKeyPairByUuid(uuid: string): Promise<KeyPair | null> {
    const keyName = KeyManagementService.ONE_TIME_PRE_KEY_PREFIX + uuid;
    try {
      const result = await this.getFromStorageSafe(keyName);
      if (!result || !result.value) {
        return null;
      }
      const storedKey = JSON.parse(result.value);
      return {
        publicKey: naclUtil.decodeBase64(storedKey.publicKey),
        privateKey: naclUtil.decodeBase64(storedKey.privateKey)
      };
    } catch {
      return null;
    }
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
   * @param myIdentityDHKeyPair
   *   The local client's long-term DH identity key pair (Curve25519).
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
  async performX3DH(friendKeysDto: KeysDto, myIdentityDHKeyPair: KeyPair, myEphemeralKeyPair?: KeyPair): 
    Promise<{ sharedSecret: Uint8Array, ephemeralPublicKey: Uint8Array }> {
      
    // verify the signature of the prekey using the friend's sign identity key
    console.log("Initiator performing X3DH with friend's keys:", friendKeysDto);

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
    const IK_A = myIdentityDHKeyPair;
    const EK_A = myEphemeralKeyPair;
    const IK_B = naclUtil.decodeBase64(friendKeysDto.identityDHKey);
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
   * @param friendIdentityDHKey
   *   The remote party's public DH identity key (Base64-encoded, Curve25519).
   *
   * @param friendEphemeralKey
   *   The remote party's ephemeral public key used for this X3DH exchange
   *   (Base64-encoded).
   *
   * @param usedOneTimePreKey
   *   The one-time pre-key that was used by the initiator, or null if no
   *   one-time pre-key was used.
   *
   * @param myIdentityDHKeyPair
   *   The local client's long-term DH identity key pair (Curve25519).
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
    friendIdentityDHKey: string,
    friendEphemeralKey: string,
    usedOneTimePreKey: OneTimePreKeyDto | null,
    myIdentityDHKeyPair: KeyPair,
    mySignedPreKeyPair: KeyPair,
    myOneTimePreKeyPair: KeyPair | null
  ): Promise<{ sharedSecret: Uint8Array }> {

    // decode friend's keys from base64
    // and declare shorter variable names
    const IK_B = myIdentityDHKeyPair;
    const SPK_B = mySignedPreKeyPair;
    const OPK_B = myOneTimePreKeyPair;
    const IK_A = naclUtil.decodeBase64(friendIdentityDHKey);
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


  /**
   * Initializes the Double Ratchet state for the sender after X3DH key agreement.
   * 
   * @param sharedSecret 
   * @param theirSignedPrekeyPublicKey 
   * @returns 
   */
  async initializeForSender(
    sharedSecret: Uint8Array,
    theirSignedPrekeyPublicKey: Uint8Array
  ): Promise<RatchetState> {

    // Derive initial root key and chain keys from shared secret
    const rootKey = await this.hkdf(sharedSecret, new Uint8Array(32), 'WhisperRatchet', 32);

    // Generate own ratchet key pair
    const myRatchetKeyPair = nacl.box.keyPair();

    // First DH with their signed prekey
    const dhOutput = nacl.scalarMult(myRatchetKeyPair.secretKey, theirSignedPrekeyPublicKey);

    // Derive new root key and sending chain key
    const { newRootKey, chainKey } = this.deriveRatchetKeys(rootKey, dhOutput);

    return {
      rootKey: newRootKey,
      sendingChainKey: chainKey,
      receivingChainKey: new Uint8Array(0), // is set on first receive
      myCurrentRatchetKeyPair: {
        publicKey: myRatchetKeyPair.publicKey,
        privateKey: myRatchetKeyPair.secretKey
      },
      theirCurrentRatchetPublicKey: theirSignedPrekeyPublicKey,
      sendMessageNumber: 0,
      receiveMessageNumber: 0,
      previousSendingChainLength: 0
    };
  }

  /**
   * Initializes the Double Ratchet state for the receiver after X3DH key agreement.
   * 
   * @param sharedSecret 
   * @param mySignedPreKeyPair 
   * @returns 
   */
  async initializeForReceiver(
    sharedSecret: Uint8Array,
    mySignedPreKeyPair: KeyPair
  ): Promise<RatchetState> {

    // Derive initial root key and chain keys from shared secret
    const rootKey = await this.hkdf(sharedSecret, new Uint8Array(32), 'WhisperRatchet', 32);

    // Use signed pre-key pair as initial ratchet key pair
    // This is important because the sender encrypted with this public key

    return {
      rootKey,
      sendingChainKey: new Uint8Array(0), // is set on first send
      receivingChainKey: new Uint8Array(0), // is set on first receive
      myCurrentRatchetKeyPair: mySignedPreKeyPair,
      theirCurrentRatchetPublicKey: null, // extracted from first message
      sendMessageNumber: 0,
      receiveMessageNumber: 0,
      previousSendingChainLength: 0
    };
  }

  /**
   * Encrypts a message using the Double Ratchet algorithm.
   * 
   * @param state 
   * @param plaintext 
   * @returns 
   */
  async encryptMessage(state: RatchetState, plaintext: string): Promise<EncryptedMessage> {
    // Derive message key from sending chain key
    const { messageKey, newChainKey } = await this.deriveMessageKey(state.sendingChainKey);

    // Encrypt the plaintext
    const nonce = nacl.randomBytes(nacl.secretbox.nonceLength);
    const plaintextBytes = naclUtil.decodeUTF8(plaintext);
    const ciphertext = nacl.secretbox(
      plaintextBytes,
      nonce,
      messageKey
    );

    // Update state
    const messageNumber = state.sendMessageNumber;
    state.sendingChainKey = newChainKey;
    state.sendMessageNumber++;

    // Delete used message key from memory
    messageKey.fill(0);

    return {
      ciphertext: naclUtil.encodeBase64(ciphertext),
      nonce: naclUtil.encodeBase64(nonce),
      messageNumber,
      ratchetPublicKey: naclUtil.encodeBase64(state.myCurrentRatchetKeyPair.publicKey)
    };
  }

  /**
   * Decrypts a message using the Double Ratchet algorithm.
   * 
   * @param state 
   * @param encryptedMessage 
   * @returns 
   */
  async decryptMessage(state: RatchetState, encryptedMessage: EncryptedMessage): Promise<string> {
    const theirRatchetkey = naclUtil.decodeBase64(encryptedMessage.ratchetPublicKey);

    // Check if we need to perform a ratchet step
    if (!state.theirCurrentRatchetPublicKey || !this.keysEqual(theirRatchetkey, state.theirCurrentRatchetPublicKey)) {
      // Perform DH with their new ratchet public key
      this.performDHRatchetStep(state, theirRatchetkey);
    }

    // Derive message key from receiving chain key
    const { messageKey, newChainKey } = await this.deriveMessageKey(state.receivingChainKey);
    state.receivingChainKey = newChainKey;

    // Decrypt the ciphertext
    const ciphertextBytes = naclUtil.decodeBase64(encryptedMessage.ciphertext);
    const nonceBytes = naclUtil.decodeBase64(encryptedMessage.nonce);
    const plaintextBytes = nacl.secretbox.open(ciphertextBytes, nonceBytes, messageKey);

    if (!plaintextBytes) {
      throw new Error('Decryption failed');
    }

    state.receiveMessageNumber++;

    // Delete used message key from memory
    messageKey.fill(0);

    return naclUtil.encodeUTF8(plaintextBytes);
  }

  /**
   * Performs a DH ratchet step when a new ratchet public key is received.
   * 
   * @param state 
   * @param theirNewRatchetKey 
   */
  private performDHRatchetStep(state: RatchetState, theirNewRatchetKey: Uint8Array): void {
    
    // save friend's new ratchet public key
    const previousRatchetKey = state.theirCurrentRatchetPublicKey;
    state.theirCurrentRatchetPublicKey = theirNewRatchetKey;

    // perform DH with friend's new ratchet public key
    const dhOutput = nacl.scalarMult(state.myCurrentRatchetKeyPair.privateKey, theirNewRatchetKey);

    // derive new root key and receiving chain key
    const { newRootKey, chainKey } = this.deriveRatchetKeys(state.rootKey, dhOutput);
    state.rootKey = newRootKey;
    state.receivingChainKey = chainKey;
    state.previousSendingChainLength = state.sendMessageNumber;
    state.receiveMessageNumber = 0;

    // generate new ratchet key pair
    const newRatchetKeyPair = nacl.box.keyPair();
    state.myCurrentRatchetKeyPair = {
      publicKey: newRatchetKeyPair.publicKey,
      privateKey: newRatchetKeyPair.secretKey
    };

    // perform DH with new ratchet key pair
    const dhOutput2 = nacl.scalarMult(state.myCurrentRatchetKeyPair.privateKey, theirNewRatchetKey);

    // derive new root key and sending chain key
    const { newRootKey: finalRootKey, chainKey: newSendingChainKey } = this.deriveRatchetKeys(state.rootKey, dhOutput2);

    state.rootKey = finalRootKey;
    state.sendingChainKey = newSendingChainKey;
    state.sendMessageNumber = 0;
  }

  /**
   * Derives the message key and the next chain key from the current chain key.
   * 
   * @param chainKey 
   * @returns 
   */
  private async deriveMessageKey(chainKey: Uint8Array): Promise<{
    messageKey: Uint8Array;
    newChainKey: Uint8Array;
  }> {
    const messageKey = await this.hkdf(chainKey, new Uint8Array(1).fill(0x01), 'WhisperMessageKeys', 32);
    const newChainKey = await this.hkdf(chainKey, new Uint8Array(1).fill(0x02), 'WhisperMessageKeys', 32);
    
    return { messageKey, newChainKey };
  }

  /**
   * Derives new root key and chain key using the Double Ratchet KDF.
   * 
   * @param rootKey 
   * @param dhOutput 
   * @returns 
   */
  private deriveRatchetKeys(rootKey: Uint8Array, dhOutput: Uint8Array): {
    newRootKey: Uint8Array;
    chainKey: Uint8Array;
  } {
    const combined = new Uint8Array(rootKey.length + dhOutput.length);
    combined.set(rootKey);
    combined.set(dhOutput, rootKey.length);
    
    const hash = nacl.hash(combined);
    
    return {
      newRootKey: hash.slice(0, 32),
      chainKey: hash.slice(32, 64)
    };
  }

  /**
   * Derives cryptographic key material using HKDF (RFC 5869) with SHA-256.
   * 
   * @param inputKeyMaterial Input key material (shared secret)
   * @param salt Optional random salt value
   * @param info Contextual information to bind the derived key to a specific use
   * @param length Desired length of the derived key in bytes
   * @returns A Uint8Array containing the derived key material
   */
  private async hkdf(
    ikm: Uint8Array,
    salt: Uint8Array,
    info: string,
    length: number
  ): Promise<Uint8Array> {

    // Helper function to convert Uint8Array to ArrayBuffer which is needed for Web Crypto API
    const toArrayBuffer = (u8: Uint8Array): ArrayBuffer => {
      if (u8.byteOffset === 0 && u8.byteLength === u8.buffer.byteLength && u8.buffer instanceof ArrayBuffer) {
        return u8.buffer as ArrayBuffer;
      }
      const ab = new ArrayBuffer(u8.byteLength);
      new Uint8Array(ab).set(u8);
      return ab;
    }

    // Import the input key material
    const key = await crypto.subtle.importKey(
      "raw",
      toArrayBuffer(ikm),
      "HKDF",
      false,
      ["deriveBits"]
    );

    // Derive bits using HKDF
    const bits = await crypto.subtle.deriveBits(
      {
        name: "HKDF",
        hash: "SHA-256",
        salt: toArrayBuffer(salt),
        info: new TextEncoder().encode(info),
      },
      key,
      length * 8
    );

    return new Uint8Array(bits);
  }

  /**
   * Compares two Uint8Array instances for equality.
   * 
   * @param a 
   * @param b 
   * @returns 
   */
  private keysEqual(a: Uint8Array, b: Uint8Array): boolean {
    if (a.length !== b.length) return false;
    for (let i = 0; i < a.length; i++) {
      if (a[i] !== b[i]) return false;
    }
    return true;
  }

  /**
   * Uploads an encrypted message to the backend.
   * 
   * @param message 
   */
  async uploadMessage(message: MessageDetailDto): Promise<MessageDetailDto> {
    console.log('Uploading message to backend:', message);
    return await firstValueFrom(this.httpClient.post<MessageDetailDto>(`${this.authBaseUri}/messages`, message));
  }

  /**
   * Fetches messages from the backend sent by a friend after a specific timestamp.
   * 
   * @param friendEmail 
   * @param timestamp 
   * @returns 
   */
  async getMessagesFromBackendAfter(friendEmail: string, timestamp: Date): Promise<MessageDetailDto[]> {
    return await firstValueFrom(this.httpClient.get<MessageDetailDto[]>(
      `${this.authBaseUri}/messages/${encodeURIComponent(friendEmail)}?timestamp=${timestamp.toISOString()}`
    ));
  }

  /**
   * Sends an encrypted message to a friend.
   * 
   * @param friendEmail
   * @param plaintext 
   */
  async sendMessageToFriend(friendEmail: string, plaintext: string): Promise<Message> {
    // Check if there is an existing ratchet state with the friend
    const state = await db.ratchetStates.get(friendEmail);

    let savedMessage: MessageDetailDto;
    if (!state) {
      // if there is no existing state, perform X3DH and initialize ratchet

      // get friend's keys
      const friendKeysDto = await firstValueFrom(this.getKeysOfFriend(friendEmail));

      // get my DH identity key
      const myIdentityDHKeyPair = await this.getIdentityDHKey();
      if (!myIdentityDHKeyPair) {
        throw new Error('No DH identity key found');
      }

      // get my sign identity key for sending
      const myIdentityKeyPair = await this.getIdentityKey();
      if (!myIdentityKeyPair) {
        throw new Error('No sign identity key found');
      }

      // perform X3DH
      const { sharedSecret, ephemeralPublicKey } = await this.performX3DH(friendKeysDto, myIdentityDHKeyPair);

      // initialize ratchet state
      const ratchetState = await this.initializeForSender(sharedSecret, naclUtil.decodeBase64(friendKeysDto.signedPreKey));

      // encrypt message
      const encryptedMessage = await this.encryptMessage(ratchetState, plaintext);

      // store ratchet state
      await db.ratchetStates.put({ contactId: friendEmail, ...ratchetState });

      // send encrypted message to friend via backend
      const messageDetail: MessageDetailDto = {
        recipientEmail: friendEmail,
        senderIdentityKey: naclUtil.encodeBase64(myIdentityKeyPair.publicKey),
        senderIdentityDHKey: naclUtil.encodeBase64(myIdentityDHKeyPair.publicKey),
        senderEphemeralKey: naclUtil.encodeBase64(ephemeralPublicKey),
        usedOneTimePreKeyId: friendKeysDto.oneTimePreKey ? friendKeysDto.oneTimePreKey.uuid : null,
        encryptedMessage: encryptedMessage
      };
      savedMessage = await this.uploadMessage(messageDetail);
    } else {
      // if there is an existing state, use it to encrypt the message
      const encryptedMessage = await this.encryptMessage(state, plaintext);

      // update ratchet state in database
      // encrypt message did change the state
      await db.ratchetStates.put(state);

      // send encrypted message to friend via backend
      const messageDetail: MessageDetailDto = {
        recipientEmail: friendEmail,
        senderIdentityKey: null, // not needed for existing sessions
        senderIdentityDHKey: null, // not needed for existing sessions
        senderEphemeralKey: null, // not needed for existing sessions
        usedOneTimePreKeyId: null, // not needed for existing sessions
        encryptedMessage: encryptedMessage
      };
      savedMessage = await this.uploadMessage(messageDetail);
    }

    // save the message to local database
    const messageRecord: Message = {
      id: savedMessage.id!,
      conversationId: '',
      senderId: 'me',
      recipientId: friendEmail,
      plaintext,
      timestamp: savedMessage.timestamp ? new Date(savedMessage.timestamp) : new Date(),
      direction: 'sent',
    }
    await db.messages.add(messageRecord);

    return messageRecord;
  }

  /**
   * Receives and decrypts a message from a friend.
   * 
   * @param messageDetail 
   * @returns 
   */
  async receiveMessageFromFriend(messageDetail: MessageDetailDto): Promise<Message> {
    // Check if there is an existing ratchet state with the friend
    const state = await db.ratchetStates.get(messageDetail.senderEmail!);

    let plaintext: string;
    if (!state) {
      // if there is no existing state, perform X3DH and initialize ratchet

      // receive X3DH
      const myIdentityDHKeyPair = await this.getIdentityDHKey();
      const mySignedPreKeyPair = await this.getSignedPreKeyPair();
      const myOneTimePreKeyPair = messageDetail.usedOneTimePreKeyId
        ? await this.getOneTimePreKeyPairByUuid(messageDetail.usedOneTimePreKeyId)
        : null;

      if (!myIdentityDHKeyPair) {
        throw new Error('No DH identity key found');
      }

      const { sharedSecret } = await this.receiveX3DH(
        messageDetail.senderIdentityDHKey!,
        messageDetail.senderEphemeralKey!,
        messageDetail.usedOneTimePreKeyId
          ? { uuid: messageDetail.usedOneTimePreKeyId, publicKey: '' }
          : null,
        myIdentityDHKeyPair,
        mySignedPreKeyPair!,
        myOneTimePreKeyPair
      );

      // initialize ratchet state
      const ratchetState = await this.initializeForReceiver(sharedSecret, mySignedPreKeyPair!);

      // decrypt message
      plaintext = await this.decryptMessage(ratchetState, messageDetail.encryptedMessage);

      // store ratchet state
      await db.ratchetStates.put({ contactId: messageDetail.senderEmail!, ...ratchetState });
    } else {
      // if there is an existing state, use it to decrypt the message
      plaintext = await this.decryptMessage(state, messageDetail.encryptedMessage);

      // update ratchet state in database
      // decrypt message did change the state
      await db.ratchetStates.put(state);

    }

    // save the message to local database
    const messageRecord: Message = {
      id: messageDetail.id!,
      conversationId: '',
      senderId: messageDetail.senderEmail!,
      recipientId: 'me',
      plaintext,
      timestamp: messageDetail.timestamp ? new Date(messageDetail.timestamp) : new Date(),
      direction: 'received',
    }
    await db.messages.add(messageRecord);

    return messageRecord;
  }

}