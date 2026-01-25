import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { KeyManagementService } from './key-management.service';
import { Globals } from '../global/globals';
import { UserService } from './user.service';
import { AuthService } from './auth.service';
import { SecureStoragePlugin } from 'capacitor-secure-storage-plugin';
import nacl from 'tweetnacl';
import naclUtil from 'tweetnacl-util';
import { db } from 'src/db/encryption';
import {
  DeviceKeyBundleDto,
  EncryptedMessage
} from 'src/app/dtos/communication';

describe('KeyManagementService - E2EE Encryption', () => {
  let service: KeyManagementService;
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let userServiceSpy: jasmine.SpyObj<UserService>;
  let globalsStub: Partial<Globals>;

  const TEST_USER_EMAIL = 'alice@test.com';
  const TEST_DEVICE_ID = 'device-123';
  const TEST_FRIEND_DEVICE_ID = 'device-456';

  let mockStorage: Map<string, string>;

  beforeEach(async () => {
    mockStorage = new Map<string, string>();

    spyOn(SecureStoragePlugin, 'set').and.callFake(async (options: { key: string; value: string }) => {
      mockStorage.set(options.key, options.value);
      return { value: true };
    });

    spyOn(SecureStoragePlugin, 'get').and.callFake(async (options: { key: string }) => {
      const value = mockStorage.get(options.key);
      if (value === undefined) {
        throw new Error('Key not found');
      }
      return { value };
    });

    spyOn(SecureStoragePlugin, 'remove').and.callFake(async (options: { key: string }) => {
      mockStorage.delete(options.key);
      return { value: true };
    });

    spyOn(db, 'getCurrentDeviceId').and.returnValue(Promise.resolve(TEST_DEVICE_ID));
    spyOn(db, 'getRatchetState').and.returnValue(Promise.resolve(undefined));
    spyOn(db, 'saveRatchetState').and.returnValue(Promise.resolve());
    spyOn(db.messages, 'add').and.returnValue(Promise.resolve(1) as any);

    authServiceSpy = jasmine.createSpyObj('AuthService', ['getUserEmail']);
    authServiceSpy.getUserEmail.and.returnValue(TEST_USER_EMAIL);

    userServiceSpy = jasmine.createSpyObj('UserService', ['getUserData']);

    globalsStub = {
      backendUri: 'http://localhost:8080'
    };

    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        KeyManagementService,
        { provide: Globals, useValue: globalsStub },
        { provide: AuthService, useValue: authServiceSpy },
        { provide: UserService, useValue: userServiceSpy }
      ]
    });

    service = TestBed.inject(KeyManagementService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    mockStorage.clear();
  });

  describe('X3DH Key Agreement', () => {
    let aliceIdentityDHKeyPair: { publicKey: Uint8Array; privateKey: Uint8Array };
    let bobIdentityDHKeyPair: { publicKey: Uint8Array; privateKey: Uint8Array };
    let bobSignedPreKeyPair: { publicKey: Uint8Array; privateKey: Uint8Array };
    let bobOneTimePreKeyPair: { publicKey: Uint8Array; privateKey: Uint8Array };
    let bobIdentityKeyPair: { publicKey: Uint8Array; secretKey: Uint8Array };
    let bobKeyBundle: DeviceKeyBundleDto;

    beforeEach(() => {
      const bobIdentityDH = nacl.box.keyPair();
      bobIdentityDHKeyPair = {
        publicKey: bobIdentityDH.publicKey,
        privateKey: bobIdentityDH.secretKey
      };

      const bobSignedPreKey = nacl.box.keyPair();
      bobSignedPreKeyPair = {
        publicKey: bobSignedPreKey.publicKey,
        privateKey: bobSignedPreKey.secretKey
      };

      const bobOneTimePreKey = nacl.box.keyPair();
      bobOneTimePreKeyPair = {
        publicKey: bobOneTimePreKey.publicKey,
        privateKey: bobOneTimePreKey.secretKey
      };

      bobIdentityKeyPair = nacl.sign.keyPair();
      const signature = nacl.sign.detached(bobSignedPreKeyPair.publicKey, bobIdentityKeyPair.secretKey);

      bobKeyBundle = {
        deviceId: TEST_FRIEND_DEVICE_ID,
        identityKey: naclUtil.encodeBase64(bobIdentityKeyPair.publicKey),
        identityDhKey: naclUtil.encodeBase64(bobIdentityDHKeyPair.publicKey),
        signedPreKey: naclUtil.encodeBase64(bobSignedPreKeyPair.publicKey),
        signedPreKeySignature: naclUtil.encodeBase64(signature),
        oneTimePreKey: {
          uuid: 'one-time-key-uuid',
          publicKey: naclUtil.encodeBase64(bobOneTimePreKeyPair.publicKey)
        }
      };

      const aliceIdentityDH = nacl.box.keyPair();
      aliceIdentityDHKeyPair = {
        publicKey: aliceIdentityDH.publicKey,
        privateKey: aliceIdentityDH.secretKey
      };
    });

    it('should perform X3DH and derive shared secret', async () => {
      const result = await service.performX3DH(bobKeyBundle, aliceIdentityDHKeyPair);

      expect(result.sharedSecret).toBeInstanceOf(Uint8Array);
      expect(result.sharedSecret.length).toBe(32);
      expect(result.ephemeralPublicKey).toBeInstanceOf(Uint8Array);
      expect(result.ephemeralPublicKey.length).toBe(32);
    });

    it('should derive same shared secret on both sides', async () => {
      const aliceResult = await service.performX3DH(bobKeyBundle, aliceIdentityDHKeyPair);

      const aliceIdentityDHKey = naclUtil.encodeBase64(aliceIdentityDHKeyPair.publicKey);
      const aliceEphemeralKey = naclUtil.encodeBase64(aliceResult.ephemeralPublicKey);

      const bobResult = await service.receiveX3DH(
        aliceIdentityDHKey,
        aliceEphemeralKey,
        bobKeyBundle.oneTimePreKey,
        bobIdentityDHKeyPair,
        bobSignedPreKeyPair,
        bobOneTimePreKeyPair
      );

      expect(aliceResult.sharedSecret).toEqual(bobResult.sharedSecret);
    });

    it('should throw error if signature is invalid', async () => {
      bobKeyBundle.signedPreKeySignature = naclUtil.encodeBase64(nacl.randomBytes(64));

      await expectAsync(
        service.performX3DH(bobKeyBundle, aliceIdentityDHKeyPair)
      ).toBeRejectedWithError('Invalid signed prekey signature!');
    });
  });

  describe('Double Ratchet Encryption', () => {
    let sharedSecret: Uint8Array;
    let aliceSignedPreKeyPair: { publicKey: Uint8Array; privateKey: Uint8Array };
    let bobSignedPreKeyPair: { publicKey: Uint8Array; privateKey: Uint8Array };

    beforeEach(() => {
      sharedSecret = nacl.randomBytes(32);
      const aliceKeyPair = nacl.box.keyPair();
      aliceSignedPreKeyPair = {
        publicKey: aliceKeyPair.publicKey,
        privateKey: aliceKeyPair.secretKey
      };
      const bobKeyPair = nacl.box.keyPair();
      bobSignedPreKeyPair = {
        publicKey: bobKeyPair.publicKey,
        privateKey: bobKeyPair.secretKey
      };
    });

    it('should encrypt and decrypt message', async () => {
      const senderState = await service.initializeForSender(sharedSecret, bobSignedPreKeyPair.publicKey);
      const receiverState = await service.initializeForReceiver(sharedSecret, bobSignedPreKeyPair);
      const plaintext = 'Secret test message!';

      const encrypted = await service.encryptMessage(senderState, plaintext);
      const decrypted = await service.decryptMessage(receiverState, encrypted);

      expect(decrypted).toBe(plaintext);
      expect(encrypted.ciphertext).toBeTruthy();
      expect(encrypted.nonce).toBeTruthy();
    });

    it('should encrypt multiple messages sequentially', async () => {
      const senderState = await service.initializeForSender(sharedSecret, bobSignedPreKeyPair.publicKey);
      const receiverState = await service.initializeForReceiver(sharedSecret, bobSignedPreKeyPair);

      const messages = ['First message', 'Second message', 'Third message'];

      for (let i = 0; i < messages.length; i++) {
        const encrypted = await service.encryptMessage(senderState, messages[i]);
        const decrypted = await service.decryptMessage(receiverState, encrypted);

        expect(decrypted).toBe(messages[i]);
        expect(senderState.sendMessageNumber).toBe(i + 1);
      }
    });

    it('should support bidirectional communication with ratchet steps', async () => {
      const aliceState = await service.initializeForSender(sharedSecret, bobSignedPreKeyPair.publicKey);
      const bobState = await service.initializeForReceiver(sharedSecret, bobSignedPreKeyPair);

      // Alice -> Bob
      const encrypted1 = await service.encryptMessage(aliceState, 'Hello Bob!');
      const decrypted1 = await service.decryptMessage(bobState, encrypted1);
      expect(decrypted1).toBe('Hello Bob!');

      // Bob -> Alice (triggers ratchet step)
      const encrypted2 = await service.encryptMessage(bobState, 'Hello Alice!');
      const decrypted2 = await service.decryptMessage(aliceState, encrypted2);
      expect(decrypted2).toBe('Hello Alice!');

      // Alice -> Bob again
      const encrypted3 = await service.encryptMessage(aliceState, 'How are you?');
      const decrypted3 = await service.decryptMessage(bobState, encrypted3);
      expect(decrypted3).toBe('How are you?');
    });

    it('should generate different ciphertexts for same plaintexts', async () => {
      const state = await service.initializeForSender(sharedSecret, bobSignedPreKeyPair.publicKey);
      const plaintext = 'Same message';

      const encrypted1 = await service.encryptMessage(state, plaintext);
      const encrypted2 = await service.encryptMessage(state, plaintext);

      expect(encrypted1.ciphertext).not.toBe(encrypted2.ciphertext);
      expect(encrypted1.nonce).not.toBe(encrypted2.nonce);
    });

    it('should throw error for invalid encrypted message', async () => {
      const receiverState = await service.initializeForReceiver(sharedSecret, bobSignedPreKeyPair);

      const invalidEncrypted: EncryptedMessage = {
        ciphertext: naclUtil.encodeBase64(nacl.randomBytes(64)),
        nonce: naclUtil.encodeBase64(nacl.randomBytes(24)),
        messageNumber: 0,
        ratchetPublicKey: naclUtil.encodeBase64(nacl.randomBytes(32))
      };

      await expectAsync(
        service.decryptMessage(receiverState, invalidEncrypted)
      ).toBeRejectedWithError('Decryption failed');
    });
  });

  describe('Complete E2EE Flow', () => {
    it('should complete full encryption flow with X3DH and Double Ratchet', async () => {
      // Setup: Generate identity keys
      await service.generateAndStoreIdentityKey();
      await service.updateSignedPreKeyIfNecessary();

      const aliceIdentityDHKeyPair = await service.getIdentityDHKey();
      const aliceSignedPreKeyPair = await service.getSignedPreKeyPair();

      // Create Bob's Key Bundle
      const bobIdentityKeyPair = nacl.sign.keyPair();
      const bobIdentityDHKeyPair = nacl.box.keyPair();
      const bobSignedPreKeyPair = nacl.box.keyPair();
      const signature = nacl.sign.detached(bobSignedPreKeyPair.publicKey, bobIdentityKeyPair.secretKey);

      const bobKeyBundle: DeviceKeyBundleDto = {
        deviceId: TEST_FRIEND_DEVICE_ID,
        identityKey: naclUtil.encodeBase64(bobIdentityKeyPair.publicKey),
        identityDhKey: naclUtil.encodeBase64(bobIdentityDHKeyPair.publicKey),
        signedPreKey: naclUtil.encodeBase64(bobSignedPreKeyPair.publicKey),
        signedPreKeySignature: naclUtil.encodeBase64(signature),
        oneTimePreKey: null
      };

      // 1. X3DH: Derive shared secret
      const { sharedSecret } = await service.performX3DH(bobKeyBundle, aliceIdentityDHKeyPair!);

      // 2. Initialize Double Ratchet for both sides
      const aliceState = await service.initializeForSender(
        sharedSecret,
        naclUtil.decodeBase64(bobKeyBundle.signedPreKey)
      );

      const bobState = await service.initializeForReceiver(
        sharedSecret,
        {
          publicKey: bobSignedPreKeyPair.publicKey,
          privateKey: bobSignedPreKeyPair.secretKey
        }
      );

      // 3. Message exchange - Alice sends to Bob
      const testMessage = 'This is an end-to-end encrypted message!';
      const encrypted = await service.encryptMessage(aliceState, testMessage);
      
      // Verify encryption properties
      expect(encrypted.ciphertext).toBeTruthy();
      expect(encrypted.nonce).toBeTruthy();
      expect(encrypted.ratchetPublicKey).toBeTruthy();
      expect(encrypted.messageNumber).toBe(0);
      
      // Bob receives and decrypts
      const decrypted = await service.decryptMessage(bobState, encrypted);
      expect(decrypted).toBe(testMessage);
      
      // Verify message was actually encrypted (ciphertext != plaintext)
      expect(encrypted.ciphertext).not.toBe(naclUtil.encodeBase64(naclUtil.decodeUTF8(testMessage)));
    });
  });
});
