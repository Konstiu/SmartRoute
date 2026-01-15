//package com.smartroute.smartroute1.unittest;
//
//import com.smartroute.smartroute1.endpoint.dto.DeviceKeysDto;
//import com.smartroute.smartroute1.endpoint.dto.MessageDetailDto;
//import com.smartroute.smartroute1.endpoint.dto.OneTimePreKeyDto;
//import com.smartroute.smartroute1.entity.ApplicationUser;
//import com.smartroute.smartroute1.entity.Friendship;
//import com.smartroute.smartroute1.entity.Message;
//import com.smartroute.smartroute1.entity.PreKey;
//import com.smartroute.smartroute1.entity.enums.FriendshipStatus;
//import com.smartroute.smartroute1.exception.NotFoundException;
//import com.smartroute.smartroute1.exception.ValidationException;
//import com.smartroute.smartroute1.repository.FriendshipRepository;
//import com.smartroute.smartroute1.repository.MessageRepository;
//import com.smartroute.smartroute1.repository.PreKeyRepository;
//import com.smartroute.smartroute1.repository.UserRepository;
//import com.smartroute.smartroute1.service.CommunicationService;
//import jakarta.transaction.Transactional;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.security.access.AccessDeniedException;
//
//import java.time.Instant;
//import java.util.List;
//import java.util.UUID;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//@SpringBootTest
//@ActiveProfiles({"test", "generateData"})
//@Transactional
//class CommunicationServiceTest {
//
//    @Autowired
//    private CommunicationService communicationService;
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Autowired
//    private PreKeyRepository preKeyRepository;
//
//    @Autowired
//    private FriendshipRepository friendshipRepository;
//
//    @Autowired
//    private MessageRepository messageRepository;
//
//    @Test
//    void uploadIdentityKey_withExistingUser_shouldSetPublicKeyAndReturnUser() {
//        // arrange
//        ApplicationUser user = new ApplicationUser("commtest@example.com", "pw", "Comm", "Test");
//        userRepository.save(user);
//
//        String publicKey = "PUBLIC_KEY_ABC123";
//        String publicDHKey = "PUBLIC_DH_KEY_XYZ789";
//
//        // act
//        ApplicationUser updated = communicationService.uploadIdentityKey(user.getEmail(), publicKey, publicDHKey);
//
//        // assert
//        assertAll("updated and persisted user",
//            () -> assertNotNull(updated),
//            () -> assertEquals(user.getEmail(), updated.getEmail()),
//            () -> assertEquals(publicKey, updated.getPublicIdentityKey())
//        );
//
//        // verify persisted
//        ApplicationUser fromDb = userRepository.findUserByEmail(user.getEmail());
//        assertAll("from DB",
//            () -> assertNotNull(fromDb),
//            () -> assertEquals(publicKey, fromDb.getPublicIdentityKey())
//        );
//    }
//
//    @Test
//    void uploadIdentityKey_withNonExistingUser_shouldThrowNotFoundException() {
//        assertThrows(NotFoundException.class, () -> communicationService.uploadIdentityKey("unknown@example.com", "KEY", "DHKEY"));
//    }
//
//    @Test
//    void uploadSignedPreKey_withExistingUser_shouldSetPreKeyAndSignatureAndReturnUser() {
//        // arrange
//        ApplicationUser user = new ApplicationUser("prekeytest@example.com", "pw", "Pre", "Key");
//        userRepository.save(user);
//
//        String publicPreKey = "PRE_KEY_123";
//        String signature = "SIG_ABC";
//
//        // act
//        ApplicationUser updated = communicationService.uploadSignedPreKey(user.getEmail(), publicPreKey, signature);
//
//        // assert
//        assertAll("updated signed prekey",
//            () -> assertNotNull(updated),
//            () -> assertEquals(user.getEmail(), updated.getEmail()),
//            () -> assertEquals(publicPreKey, updated.getPublicPreKey()),
//            () -> assertEquals(signature, updated.getPreKeySignature())
//        );
//
//        // verify persisted
//        ApplicationUser fromDb = userRepository.findUserByEmail(user.getEmail());
//        assertAll("from DB signed prekey",
//            () -> assertNotNull(fromDb),
//            () -> assertEquals(publicPreKey, fromDb.getPublicPreKey()),
//            () -> assertEquals(signature, fromDb.getPreKeySignature())
//        );
//    }
//
//    @Test
//    void uploadSignedPreKey_withNonExistingUser_shouldThrowNotFoundException() {
//        assertThrows(NotFoundException.class,
//                () -> communicationService.uploadSignedPreKey("doesnotexist@example.com", "PRE", "SIG"));
//    }
//
//
//    @Test
//    void uploadOneTimePreKeys_withNewKeys_shouldPersistPreKeys() {
//        // arrange
//        ApplicationUser user = new ApplicationUser("otp_user@example.com", "pw", "OT", "P");
//        userRepository.save(user);
//
//        OneTimePreKeyDto dto1 = new OneTimePreKeyDto();
//        dto1.setUuid(UUID.randomUUID());
//        dto1.setPublicKey("ONE_KEY_1");
//
//        OneTimePreKeyDto dto2 = new OneTimePreKeyDto();
//        dto2.setUuid(UUID.randomUUID());
//        dto2.setPublicKey("ONE_KEY_2");
//
//        // act
//        communicationService.uploadOneTimePreKeys(user.getEmail(), List.of(dto1, dto2));
//
//        // assert
//        ApplicationUser fromDb = userRepository.findUserByEmail(user.getEmail());
//        assertAll("from DB prekeys",
//            () -> assertEquals(2, preKeyRepository.countByUserId(user.getId()), "Two pre-keys should have been persisted"),
//            () -> assertNotNull(fromDb),
//            () -> assertEquals(2, fromDb.getOneTimePreKeys().size())
//        );
//    }
//
//    @Test
//    void uploadOneTimePreKeys_skipsExistingUuid_and_doesNotDuplicate() {
//        // arrange
//        ApplicationUser user = new ApplicationUser("otp_existing@example.com", "pw", "OT", "P");
//        userRepository.save(user);
//
//        UUID existingUuid = UUID.randomUUID();
//        PreKey existing = new PreKey();
//        existing.setUuid(existingUuid);
//        existing.setPublicKey("EXISTING_KEY");
//        existing.setUser(user);
//        preKeyRepository.save(existing);
//
//        OneTimePreKeyDto dto1 = new OneTimePreKeyDto();
//        dto1.setUuid(existingUuid);
//        dto1.setPublicKey("ONE_KEY_SHOULD_BE_SKIPPED");
//
//        OneTimePreKeyDto dto2 = new OneTimePreKeyDto();
//        dto2.setUuid(UUID.randomUUID());
//        dto2.setPublicKey("ONE_KEY_NEW");
//
//        // act
//        communicationService.uploadOneTimePreKeys(user.getEmail(), List.of(dto1, dto2));
//
//        // assert
//        long count = preKeyRepository.countByUserId(user.getId());
//        assertEquals(2, count, "Only the new pre-key should be added in addition to the existing one");
//    }
//
//    @Test
//    void countOneTimePreKeys_returnsCorrectNumber() {
//        // arrange
//        ApplicationUser user = new ApplicationUser("count_user@example.com", "pw", "Count", "Test");
//        userRepository.save(user);
//
//        PreKey p1 = new PreKey();
//        p1.setUuid(UUID.randomUUID());
//        p1.setPublicKey("C1");
//        p1.setUser(user);
//        preKeyRepository.save(p1);
//
//        PreKey p2 = new PreKey();
//        p2.setUuid(UUID.randomUUID());
//        p2.setPublicKey("C2");
//        p2.setUser(user);
//        preKeyRepository.save(p2);
//
//        // act
//        long count = communicationService.countOneTimePreKeys(user.getEmail());
//
//        // assert
//        assertEquals(2, count);
//    }
//
//    @Test
//    void getKeysOfFriend_whenNotFriends_shouldThrowAccessDenied() {
//        ApplicationUser user = new ApplicationUser("userA@example.com", "pw", "User", "A");
//        ApplicationUser friend = new ApplicationUser("friendB@example.com", "pw", "Friend", "B");
//        userRepository.save(user);
//        userRepository.save(friend);
//
//        assertThrows(AccessDeniedException.class,
//            () -> communicationService.getKeysOfFriend(friend.getEmail(), user.getEmail()));
//    }
//
//    @Test
//    void getKeysOfFriend_whenFriends_andNoPreKey_shouldReturnKeysDtoWithNullOneTimePreKey() {
//        // arrange
//        ApplicationUser user = new ApplicationUser("userC@example.com", "pw", "User", "C");
//        ApplicationUser friend = new ApplicationUser("friendD@example.com", "pw", "Friend", "D");
//        userRepository.save(user);
//        userRepository.save(friend);
//
//        Friendship f = new Friendship();
//        f.setSender(user);
//        f.setReceiver(friend);
//        f.setStatus(FriendshipStatus.ACCEPTED);
//        friendshipRepository.save(f);
//
//        // set friend's keys
//        friend.setPublicIdentityKey("IDENTITY_KEY_D");
//        friend.setPublicPreKey("SIGNED_PREKEY_D");
//        friend.setPreKeySignature("SIGNATURE_D");
//        userRepository.save(friend);
//
//        // act
//        DeviceKeysDto keys = communicationService.getKeysOfFriend(friend.getEmail(), user.getEmail());
//
//        // assert
//        assertAll("keys content",
//            () -> assertNotNull(keys),
//            () -> assertEquals("IDENTITY_KEY_D", keys.getIdentityKey()),
//            () -> assertEquals("SIGNED_PREKEY_D", keys.getSignedPreKey()),
//            () -> assertEquals("SIGNATURE_D", keys.getSignedPreKeySignature()),
//            () -> assertNull(keys.getOneTimePreKey(), "No one-time pre-key should be returned")
//        );
//    }
//
//    @Test
//    void getKeysOfFriend_withOneTimePreKey_returnsKeyAndDeletesIt() {
//        // arrange
//        ApplicationUser user = new ApplicationUser("userE@example.com", "pw", "User", "E");
//        ApplicationUser friend = new ApplicationUser("friendF@example.com", "pw", "Friend", "F");
//        userRepository.save(user);
//        userRepository.save(friend);
//
//        Friendship f = new Friendship();
//        f.setSender(user);
//        f.setReceiver(friend);
//        f.setStatus(FriendshipStatus.ACCEPTED);
//        friendshipRepository.save(f);
//
//        // set friend's keys
//        friend.setPublicIdentityKey("IDENTITY_KEY_F");
//        friend.setPublicPreKey("SIGNED_PREKEY_F");
//        friend.setPreKeySignature("SIGNATURE_F");
//        userRepository.save(friend);
//
//        PreKey preKey = new PreKey();
//        preKey.setUuid(UUID.randomUUID());
//        preKey.setPublicKey("ONE_TIME_F");
//        preKey.setUser(friend);
//        preKeyRepository.save(preKey);
//
//        // act
//        DeviceKeysDto keys = communicationService.getKeysOfFriend(friend.getEmail(), user.getEmail());
//
//        // assert
//        assertAll("keys and cleanup",
//            () -> assertNotNull(keys),
//            () -> assertEquals("ONE_TIME_F", keys.getOneTimePreKey().getPublicKey()),
//            () -> assertEquals(0, preKeyRepository.countByUserId(friend.getId()), "One-time pre-key should have been deleted after retrieval")
//        );
//    }
//
//    @Test
//    void sendEncryptedMessage_whenNotFriends_shouldThrowAccessDenied() {
//        ApplicationUser sender = new ApplicationUser("sender1@example.com", "pw", "S", "One");
//        ApplicationUser recipient = new ApplicationUser("recipient1@example.com", "pw", "R", "One");
//        userRepository.save(sender);
//        userRepository.save(recipient);
//
//        MessageDetailDto dto = new MessageDetailDto();
//        dto.setSenderEmail(sender.getEmail());
//        dto.setRecipientEmail(recipient.getEmail());
//        dto.setEncryptedMessage(null);
//
//        assertThrows(AccessDeniedException.class,
//                () -> communicationService.sendEncryptedMessage(sender.getEmail(), dto));
//    }
//
//    @Test
//    void sendEncryptedMessage_whenFriendsAndInvalid_shouldThrowValidationException() {
//        ApplicationUser sender = new ApplicationUser("sender2@example.com", "pw", "S", "Two");
//        ApplicationUser recipient = new ApplicationUser("recipient2@example.com", "pw", "R", "Two");
//        userRepository.save(sender);
//        userRepository.save(recipient);
//
//        Friendship f = new Friendship();
//        f.setSender(sender);
//        f.setReceiver(recipient);
//        f.setStatus(FriendshipStatus.ACCEPTED);
//        friendshipRepository.save(f);
//
//        MessageDetailDto dto = new MessageDetailDto();
//        dto.setSenderEmail(sender.getEmail());
//        dto.setRecipientEmail(recipient.getEmail());
//        dto.setEncryptedMessage(null);
//
//        assertThrows(ValidationException.class,
//                () -> communicationService.sendEncryptedMessage(sender.getEmail(), dto));
//    }
//
//    @Test
//    void sendEncryptedMessage_whenFriendsAndValid_shouldPersistAllFields() throws com.smartroute.smartroute1.exception.ValidationException {
//        ApplicationUser sender = new ApplicationUser("sender3@example.com", "pw", "S", "Three");
//        ApplicationUser recipient = new ApplicationUser("recipient3@example.com", "pw", "R", "Three");
//        userRepository.save(sender);
//        userRepository.save(recipient);
//
//        Friendship f = new Friendship();
//        f.setSender(sender);
//        f.setReceiver(recipient);
//        f.setStatus(FriendshipStatus.ACCEPTED);
//        friendshipRepository.save(f);
//
//        // build full DTO
//        MessageDetailDto dto = new MessageDetailDto();
//        dto.setSenderEmail(sender.getEmail());
//        dto.setRecipientEmail(recipient.getEmail());
//        dto.setSenderIdentityKey("SENDER_IDENTITY_KEY");
//        dto.setSenderEphemeralKey("SENDER_EPHEMERAL_KEY");
//        UUID usedPreKey = UUID.randomUUID();
//        dto.setUsedOneTimePreKeyId(usedPreKey);
//
//        com.smartroute.smartroute1.endpoint.dto.EncryptedMessageDto enc = new com.smartroute.smartroute1.endpoint.dto.EncryptedMessageDto();
//        enc.setCiphertext("CIPHERTEXT_ABC");
//        enc.setNonce("NONCE_123");
//        enc.setMessageNumber(42L);
//        enc.setRatchetPublicKey("RATCHET_KEY_XYZ");
//        dto.setEncryptedMessage(enc);
//
//        Message saved = communicationService.sendEncryptedMessage(sender.getEmail(), dto);
//
//        assertAll("saved message",
//            () -> assertNotNull(saved, "Saved message should not be null"),
//            () -> assertEquals(sender.getEmail(), saved.getSender().getEmail()),
//            () -> assertEquals(recipient.getEmail(), saved.getRecipient().getEmail()),
//            () -> assertEquals(dto.getSenderIdentityKey(), saved.getSenderIdentityKey()),
//            () -> assertEquals(dto.getSenderEphemeralKey(), saved.getSenderEphemeralKey()),
//            () -> assertEquals(dto.getUsedOneTimePreKeyId(), saved.getUsedOneTimePreKeyId()),
//            () -> assertNotNull(saved.getCiphertext()),
//            () -> assertEquals(enc.getCiphertext(), saved.getCiphertext()),
//            () -> assertEquals(enc.getNonce(), saved.getNonce()),
//            () -> assertEquals(enc.getMessageNumber(), saved.getMessageNumber()),
//            () -> assertEquals(enc.getRatchetPublicKey(), saved.getRatchetPublicKey())
//        );
//    }
//
//    @Test
//    void retrieveMessagesByFriendAndTimestamp_whenNotFriends_shouldThrowAccessDenied() {
//        ApplicationUser user = new ApplicationUser("rm_user@example.com", "pw", "R", "One");
//        ApplicationUser friend = new ApplicationUser("rm_friend@example.com", "pw", "R", "Friend");
//        userRepository.save(user);
//        userRepository.save(friend);
//
//        Instant since = Instant.now().minusSeconds(3600);
//
//        assertThrows(AccessDeniedException.class,
//                () -> communicationService.retrieveMessagesByFriendAndTimestamp(user.getEmail(), friend.getEmail(), since));
//    }
//
//    @Test
//    void retrieveMessagesByFriendAndTimestamp_whenFriends_returnsMessagesSinceTimestamp() throws InterruptedException {
//        ApplicationUser user = new ApplicationUser("rm_user2@example.com", "pw", "R", "Two");
//        ApplicationUser friend = new ApplicationUser("rm_friend2@example.com", "pw", "R", "Friend2");
//        userRepository.save(user);
//        userRepository.save(friend);
//
//        Friendship f = new Friendship();
//        f.setSender(user);
//        f.setReceiver(friend);
//        f.setStatus(FriendshipStatus.ACCEPTED);
//        friendshipRepository.save(f);
//
//        Message mOld = new Message();
//        mOld.setSender(user);
//        mOld.setRecipient(friend);
//        mOld.setCiphertext("OLD");
//        messageRepository.save(mOld);
//
//        Thread.sleep(1000); // ensure timestamp difference
//
//        Message mNew = new Message();
//        mNew.setSender(friend);
//        mNew.setRecipient(user);
//        mNew.setCiphertext("NEW");
//        messageRepository.save(mNew);
//
//        messageRepository.flush();
//
//        Message persistedOld = messageRepository.findById(mOld.getId()).orElseThrow();
//        Instant base = persistedOld.getTimestamp().plusMillis(100); // slightly after old message
//
//        // retrieve messages since 'base'
//        var results = communicationService.retrieveMessagesByFriendAndTimestamp(user.getEmail(), friend.getEmail(), base);
//
//        assertAll("results",
//            () -> assertNotNull(results),
//            () -> assertEquals(1, results.size(), "Only the message with timestamp > base should be returned"),
//            () -> assertEquals("NEW", results.getFirst().getCiphertext())
//        );
//    }
//
//}
