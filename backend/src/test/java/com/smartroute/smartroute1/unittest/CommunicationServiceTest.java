package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.endpoint.dto.DeviceKeysDto;
import com.smartroute.smartroute1.endpoint.dto.EncryptedMessageDto;
import com.smartroute.smartroute1.endpoint.dto.FriendDeviceBundlesDto;
import com.smartroute.smartroute1.endpoint.dto.MessageDetailDto;
import com.smartroute.smartroute1.endpoint.dto.OneTimePreKeyDto;
import com.smartroute.smartroute1.endpoint.mapper.MessageMapper;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Message;
import com.smartroute.smartroute1.entity.PreKey;
import com.smartroute.smartroute1.entity.UserDevice;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.repository.DeviceRepository;
import com.smartroute.smartroute1.repository.MessageRepository;
import com.smartroute.smartroute1.repository.PreKeyRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.FriendshipService;
import com.smartroute.smartroute1.service.UserService;
import com.smartroute.smartroute1.service.impl.CommunicationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class CommunicationServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PreKeyRepository preKeyRepository;

    @Mock
    private FriendshipService friendshipService;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private DeviceRepository deviceRepository;

    @InjectMocks
    private CommunicationServiceImpl communicationService;

    private ApplicationUser testUser;
    private ApplicationUser friendUser;
    private UserDevice testDevice;
    private UserDevice friendDevice;

    @BeforeEach
    void setUp() {
        testUser = new ApplicationUser();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");

        friendUser = new ApplicationUser();
        friendUser.setId(2L);
        friendUser.setEmail("friend@example.com");

        testDevice = new UserDevice();
        testDevice.setId(1L);
        testDevice.setUser(testUser);
        testDevice.setDeviceId("device123");
        testDevice.setPublicIdentityKey("identityKey");
        testDevice.setPublicIdentityDhKey("dhKey");
        testDevice.setPublicPreKey("preKey");
        testDevice.setPreKeySignature("signature");

        friendDevice = new UserDevice();
        friendDevice.setId(2L);
        friendDevice.setUser(friendUser);
        friendDevice.setDeviceId("friendDevice123");
        friendDevice.setPublicIdentityKey("friendIdentityKey");
        friendDevice.setPublicIdentityDhKey("friendDhKey");
        friendDevice.setPublicPreKey("friendPreKey");
        friendDevice.setPreKeySignature("friendSignature");
    }

    // ==================== uploadIdentityKey Tests ====================

    @Test
    void uploadIdentityKey_newDevice_createsAndSavesDevice() {
        // Given
        String deviceId = "newDevice";
        String publicKey = "newPublicKey";
        String publicDhKey = "newDhKey";

        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);
        when(deviceRepository.findByUserAndDeviceId(testUser, deviceId)).thenReturn(Optional.empty());
        when(deviceRepository.save(any(UserDevice.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        ApplicationUser result = communicationService.uploadIdentityKey("test@example.com", deviceId, publicKey, publicDhKey);

        // Then
        assertNotNull(result);
        assertEquals(testUser, result);
        verify(deviceRepository).save(argThat(device ->
                device.getDeviceId().equals(deviceId) &&
                        device.getPublicIdentityKey().equals(publicKey) &&
                        device.getPublicIdentityDhKey().equals(publicDhKey) &&
                        device.getUser().equals(testUser)
        ));
    }

    @Test
    void uploadIdentityKey_existingDevice_updatesDevice() {
        // Given
        String publicKey = "updatedPublicKey";
        String publicDhKey = "updatedDhKey";

        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);
        when(deviceRepository.findByUserAndDeviceId(testUser, "device123")).thenReturn(Optional.of(testDevice));
        when(deviceRepository.save(any(UserDevice.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        ApplicationUser result = communicationService.uploadIdentityKey("test@example.com", "device123", publicKey, publicDhKey);

        // Then
        assertAll(
                () ->
                        assertNotNull(result),
                () -> assertEquals(publicKey, testDevice.getPublicIdentityKey()),
                () -> assertEquals(publicDhKey, testDevice.getPublicIdentityDhKey())
        );
        verify(deviceRepository).save(testDevice);
    }

    // ==================== uploadSignedPreKey Tests ====================

    @Test
    void uploadSignedPreKey_validDevice_updatesPreKey() throws ValidationException {
        // Given
        String publicPreKey = "newPreKey";
        String signature = "newSignature";

        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);
        when(deviceRepository.findByUserAndDeviceId(testUser, "device123")).thenReturn(Optional.of(testDevice));
        when(deviceRepository.save(any(UserDevice.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        ApplicationUser result = communicationService.uploadSignedPreKey("test@example.com", publicPreKey, signature, "device123");

        // Then
        assertAll(

                () -> assertNotNull(result),
                () -> assertEquals(publicPreKey, testDevice.getPublicPreKey()),
                () -> assertEquals(signature, testDevice.getPreKeySignature())
        );
        verify(deviceRepository).save(testDevice);
    }

    @Test
    void uploadSignedPreKey_unknownDevice_throwsValidationException() {
        // Given
        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);
        when(deviceRepository.findByUserAndDeviceId(testUser, "unknownDevice")).thenReturn(Optional.empty());

        // When & Then
        ValidationException exception = assertThrows(ValidationException.class, () ->
                communicationService.uploadSignedPreKey("test@example.com", "preKey", "signature", "unknownDevice")
        );
        assertEquals("Unknown device", exception.getMessage());
        verify(deviceRepository, never()).save(any());
    }

    // ==================== uploadOneTimePreKeys Tests ====================

    @Test
    void uploadOneTimePreKeys_nullKeys_returnsEarlyWithoutSaving() throws ValidationException {
        // Given
        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);

        // When
        communicationService.uploadOneTimePreKeys("test@example.com", "device123", null);

        // Then
        verify(deviceRepository, never()).findByUserAndDeviceId(any(), any());
        verify(preKeyRepository, never()).save(any());
    }

    @Test
    void uploadOneTimePreKeys_emptyKeys_returnsEarlyWithoutSaving() throws ValidationException {
        // Given
        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);

        // When
        communicationService.uploadOneTimePreKeys("test@example.com", "device123", new ArrayList<>());

        // Then
        verify(deviceRepository, never()).findByUserAndDeviceId(any(), any());
        verify(preKeyRepository, never()).save(any());
    }

    @Test
    void uploadOneTimePreKeys_validKeys_savesAllKeys() throws ValidationException {
        // Given
        OneTimePreKeyDto key1 = new OneTimePreKeyDto();
        key1.setUuid(UUID.randomUUID());
        key1.setPublicKey("key1");

        OneTimePreKeyDto key2 = new OneTimePreKeyDto();
        key2.setUuid(UUID.randomUUID());
        key2.setPublicKey("key2");

        List<OneTimePreKeyDto> keys = List.of(key1, key2);

        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);
        when(deviceRepository.findByUserAndDeviceId(testUser, "device123")).thenReturn(Optional.of(testDevice));
        when(preKeyRepository.countByDevice_Id(testDevice.getId())).thenReturn(0L);
        when(preKeyRepository.existsByDevice_IdAndUuid(anyLong(), any(UUID.class))).thenReturn(false);
        when(preKeyRepository.save(any(PreKey.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        communicationService.uploadOneTimePreKeys("test@example.com", "device123", keys);

        // Then
        verify(preKeyRepository, times(2)).save(argThat(preKey ->
                preKey.getDevice().equals(testDevice)
        ));
    }

    @Test
    void uploadOneTimePreKeys_atMaxCapacity_doesNotSaveAnyKeys() throws ValidationException {
        // Given
        OneTimePreKeyDto key = new OneTimePreKeyDto();
        key.setUuid(UUID.randomUUID());
        key.setPublicKey("key1");

        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);
        when(deviceRepository.findByUserAndDeviceId(testUser, "device123")).thenReturn(Optional.of(testDevice));
        when(preKeyRepository.countByDevice_Id(testDevice.getId())).thenReturn(150L); // MAX_ONE_TIME_PRE_KEYS

        // When
        communicationService.uploadOneTimePreKeys("test@example.com", "device123", List.of(key));

        // Then
        verify(preKeyRepository, never()).save(any());
    }

    @Test
    void uploadOneTimePreKeys_partialCapacity_savesOnlyWhatFits() throws ValidationException {
        // Given
        List<OneTimePreKeyDto> keys = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            OneTimePreKeyDto key = new OneTimePreKeyDto();
            key.setUuid(UUID.randomUUID());
            key.setPublicKey("key" + i);
            keys.add(key);
        }

        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);
        when(deviceRepository.findByUserAndDeviceId(testUser, "device123")).thenReturn(Optional.of(testDevice));
        when(preKeyRepository.countByDevice_Id(testDevice.getId())).thenReturn(145L); // 5 slots available
        when(preKeyRepository.existsByDevice_IdAndUuid(anyLong(), any(UUID.class))).thenReturn(false);
        when(preKeyRepository.save(any(PreKey.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        communicationService.uploadOneTimePreKeys("test@example.com", "device123", keys);

        // Then
        verify(preKeyRepository, times(5)).save(any(PreKey.class)); // Only 5 saved, not 10
    }

    @Test
    void uploadOneTimePreKeys_duplicateUuid_skipsKey() throws ValidationException {
        // Given
        UUID duplicateUuid = UUID.randomUUID();
        OneTimePreKeyDto key1 = new OneTimePreKeyDto();
        key1.setUuid(duplicateUuid);
        key1.setPublicKey("key1");

        OneTimePreKeyDto key2 = new OneTimePreKeyDto();
        key2.setUuid(UUID.randomUUID());
        key2.setPublicKey("key2");

        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);
        when(deviceRepository.findByUserAndDeviceId(testUser, "device123")).thenReturn(Optional.of(testDevice));
        when(preKeyRepository.countByDevice_Id(testDevice.getId())).thenReturn(0L);
        when(preKeyRepository.existsByDevice_IdAndUuid(testDevice.getId(), duplicateUuid)).thenReturn(true);
        when(preKeyRepository.existsByDevice_IdAndUuid(eq(testDevice.getId()), argThat(uuid -> !uuid.equals(duplicateUuid)))).thenReturn(false);
        when(preKeyRepository.save(any(PreKey.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        communicationService.uploadOneTimePreKeys("test@example.com", "device123", List.of(key1, key2));

        // Then
        verify(preKeyRepository, times(1)).save(argThat(preKey ->
                preKey.getPublicKey().equals("key2")
        ));
    }

    @Test
    void uploadOneTimePreKeys_unknownDevice_throwsValidationException() {
        // Given
        OneTimePreKeyDto key = new OneTimePreKeyDto();
        key.setUuid(UUID.randomUUID());
        key.setPublicKey("key1");

        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);
        when(deviceRepository.findByUserAndDeviceId(testUser, "unknownDevice")).thenReturn(Optional.empty());

        // When & Then
        ValidationException exception = assertThrows(ValidationException.class, () ->
                communicationService.uploadOneTimePreKeys("test@example.com", "unknownDevice", List.of(key))
        );
        assertEquals("Unknown device", exception.getMessage());
        verify(preKeyRepository, never()).save(any());
    }

    // ==================== countOneTimePreKeys Tests ====================

    @Test
    void countOneTimePreKeys_validDevice_returnsCount() throws ValidationException {
        // Given
        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);
        when(deviceRepository.findByUserAndDeviceId(testUser, "device123")).thenReturn(Optional.of(testDevice));
        when(preKeyRepository.countByDevice_Id(testDevice.getId())).thenReturn(42L);

        // When
        long count = communicationService.countOneTimePreKeys("test@example.com", "device123");

        // Then
        assertEquals(42L, count);
        verify(preKeyRepository).countByDevice_Id(testDevice.getId());
    }

    @Test
    void countOneTimePreKeys_unknownDevice_throwsValidationException() {
        // Given
        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);
        when(deviceRepository.findByUserAndDeviceId(testUser, "unknownDevice")).thenReturn(Optional.empty());

        // When & Then
        ValidationException exception = assertThrows(ValidationException.class, () ->
                communicationService.countOneTimePreKeys("test@example.com", "unknownDevice")
        );
        assertEquals("Unknown device", exception.getMessage());
    }

    // ==================== getKeysOfFriendAllDevices Tests ====================

    @Test
    void getKeysOfFriendAllDevices_notFriends_throwsAccessDeniedException() {
        // Given
        when(friendshipService.areFriends("test@example.com", "friend@example.com")).thenReturn(false);

        // When & Then
        assertThrows(AccessDeniedException.class, () ->
                communicationService.getKeysOfFriendAllDevices("friend@example.com", "test@example.com")
        );
        verify(userService, never()).findApplicationUserByEmail(any());
    }

    @Test
    void getKeysOfFriendAllDevices_friends_returnsAllDeviceKeys() {
        // Given
        when(friendshipService.areFriends("test@example.com", "friend@example.com")).thenReturn(true);
        when(userService.findApplicationUserByEmail("friend@example.com")).thenReturn(friendUser);
        when(deviceRepository.findAllByUser(friendUser)).thenReturn(List.of(friendDevice));
        when(preKeyRepository.findFirstByDevice_IdOrderByIdAsc(friendDevice.getId())).thenReturn(null);

        // When
        FriendDeviceBundlesDto result = communicationService.getKeysOfFriendAllDevices("friend@example.com", "test@example.com");

        // Then
        assertAll(
                () -> assertNotNull(result),
                () -> assertNotNull(result.getDevices()),
                () -> assertEquals(1, result.getDevices().size()));
        DeviceKeysDto deviceKeys = result.getDevices().get(0);
        assertAll(
                () -> assertEquals("friendDevice123", deviceKeys.getDeviceId()),
                () -> assertEquals("friendIdentityKey", deviceKeys.getIdentityKey()),
                () -> assertEquals("friendDhKey", deviceKeys.getIdentityDhKey()),
                () -> assertEquals("friendPreKey", deviceKeys.getSignedPreKey()),
                () -> assertEquals("friendSignature", deviceKeys.getSignedPreKeySignature()),
                () -> assertNull(deviceKeys.getOneTimePreKey())
        );
    }

    @Test
    void getKeysOfFriendAllDevices_withOneTimePreKey_returnsKeyAndDeletesIt() {
        // Given
        PreKey oneTimePreKey = new PreKey();
        oneTimePreKey.setId(1L);
        oneTimePreKey.setUuid(UUID.randomUUID());
        oneTimePreKey.setPublicKey("oneTimeKey");
        oneTimePreKey.setDevice(friendDevice);

        when(friendshipService.areFriends("test@example.com", "friend@example.com")).thenReturn(true);
        when(userService.findApplicationUserByEmail("friend@example.com")).thenReturn(friendUser);
        when(deviceRepository.findAllByUser(friendUser)).thenReturn(List.of(friendDevice));
        when(preKeyRepository.findFirstByDevice_IdOrderByIdAsc(friendDevice.getId())).thenReturn(oneTimePreKey);

        // When
        FriendDeviceBundlesDto result = communicationService.getKeysOfFriendAllDevices("friend@example.com", "test@example.com");

        // Then
        assertNotNull(result);
        assertEquals(1, result.getDevices().size());
        DeviceKeysDto deviceKeys = result.getDevices().get(0);
        assertNotNull(deviceKeys.getOneTimePreKey());
        assertEquals("oneTimeKey", deviceKeys.getOneTimePreKey().getPublicKey());
        verify(preKeyRepository).delete(oneTimePreKey);
    }

    // ==================== getKeysOfAllMyDevices Tests ====================

    @Test
    void getKeysOfAllMyDevices_returnsAllMyDevices() {
        // Given
        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);
        when(deviceRepository.findAllByUser(testUser)).thenReturn(List.of(testDevice));
        when(preKeyRepository.findFirstByDevice_IdOrderByIdAsc(testDevice.getId())).thenReturn(null);

        // When
        FriendDeviceBundlesDto result = communicationService.getKeysOfAllMyDevices("test@example.com");

        // Then
        assertAll(
                () -> assertNotNull(result),
                () -> assertNotNull(result.getDevices()),
                () -> assertEquals(1, result.getDevices().size()),
                () -> assertEquals("device123", result.getDevices().get(0).getDeviceId())
        );
    }

    // ==================== sendEncryptedMessage Tests ====================

    @Test
    void sendEncryptedMessage_notFriends_throwsAccessDeniedException() {
        // Given
        MessageDetailDto dto = createValidMessageDto("friend@example.com");

        when(friendshipService.areFriends("test@example.com", "friend@example.com")).thenReturn(false);

        // When & Then
        assertThrows(AccessDeniedException.class, () ->
                communicationService.sendEncryptedMessage("test@example.com", dto)
        );
        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendEncryptedMessage_invalidDto_throwsValidationException() {
        // Given
        MessageDetailDto dto = new MessageDetailDto();
        dto.setRecipientEmail("friend@example.com");
        dto.setEncryptedMessage(null); // Invalid - missing encrypted message

        when(friendshipService.areFriends("test@example.com", "friend@example.com")).thenReturn(true);

        // When & Then
        assertThrows(ValidationException.class, () ->
                communicationService.sendEncryptedMessage("test@example.com", dto)
        );
    }

    @Test
    void sendEncryptedMessage_unknownSenderDevice_throwsValidationException() {
        // Given
        MessageDetailDto dto = createValidMessageDto("friend@example.com");

        when(friendshipService.areFriends("test@example.com", "friend@example.com")).thenReturn(true);
        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);
        when(userService.findApplicationUserByEmail("friend@example.com")).thenReturn(friendUser);
        when(deviceRepository.findByUserAndDeviceId(testUser, dto.getSenderDeviceId())).thenReturn(Optional.empty());

        // When & Then
        ValidationException exception = assertThrows(ValidationException.class, () ->
                communicationService.sendEncryptedMessage("test@example.com", dto)
        );
        assertEquals("Unknown sender device", exception.getMessage());
    }

    @Test
    void sendEncryptedMessage_unknownRecipientDevice_throwsValidationException() {
        // Given
        MessageDetailDto dto = createValidMessageDto("friend@example.com");

        when(friendshipService.areFriends("test@example.com", "friend@example.com")).thenReturn(true);
        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);
        when(userService.findApplicationUserByEmail("friend@example.com")).thenReturn(friendUser);
        when(deviceRepository.findByUserAndDeviceId(testUser, dto.getSenderDeviceId())).thenReturn(Optional.of(testDevice));
        when(deviceRepository.findByUserAndDeviceId(friendUser, dto.getRecipientDeviceId())).thenReturn(Optional.empty());

        // When & Then
        ValidationException exception = assertThrows(ValidationException.class, () ->
                communicationService.sendEncryptedMessage("test@example.com", dto)
        );
        assertEquals("Unknown recipient device", exception.getMessage());
    }

    @Test
    void sendEncryptedMessage_validMessage_savesAndReturnsMessage() throws ValidationException {
        // Given
        MessageDetailDto dto = createValidMessageDto("friend@example.com");
        Message mappedMessage = new Message();
        Message savedMessage = new Message();
        savedMessage.setId(1L);

        when(friendshipService.areFriends("test@example.com", "friend@example.com")).thenReturn(true);
        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);
        when(userService.findApplicationUserByEmail("friend@example.com")).thenReturn(friendUser);
        when(deviceRepository.findByUserAndDeviceId(testUser, dto.getSenderDeviceId())).thenReturn(Optional.of(testDevice));
        when(deviceRepository.findByUserAndDeviceId(friendUser, dto.getRecipientDeviceId())).thenReturn(Optional.of(friendDevice));
        when(messageMapper.messageDetailDtoToEntity(dto, testUser, friendUser)).thenReturn(mappedMessage);
        when(messageRepository.save(mappedMessage)).thenReturn(savedMessage);

        // When
        Message result = communicationService.sendEncryptedMessage("test@example.com", dto);

        // Then

        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(savedMessage, result),
                () -> assertEquals("device123", mappedMessage.getSenderDeviceId()),
                () -> assertEquals("friendDevice123", mappedMessage.getRecipientDeviceId())
        );
        verify(messageRepository).save(mappedMessage);
    }

    // ==================== sendEncryptedMessageToMyDevices Tests ====================

    @Test
    void sendEncryptedMessageToMyDevices_validMessage_savesMessage() throws ValidationException {
        // Given
        MessageDetailDto dto = createValidMessageDto("test@example.com"); // Sending to self
        dto.setRecipientDeviceId("device456"); // Different device

        UserDevice anotherDevice = new UserDevice();
        anotherDevice.setDeviceId("device456");
        anotherDevice.setUser(testUser);

        Message mappedMessage = new Message();
        Message savedMessage = new Message();

        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);
        when(deviceRepository.findByUserAndDeviceId(testUser, dto.getSenderDeviceId())).thenReturn(Optional.of(testDevice));
        when(deviceRepository.findByUserAndDeviceId(testUser, dto.getRecipientDeviceId())).thenReturn(Optional.of(anotherDevice));
        when(messageMapper.messageDetailDtoToEntity(dto, testUser, testUser)).thenReturn(mappedMessage);
        when(messageRepository.save(mappedMessage)).thenReturn(savedMessage);

        // When
        Message result = communicationService.sendEncryptedMessageToMyDevices("test@example.com", dto);

        // Then
        assertNotNull(result);
        verify(messageRepository).save(mappedMessage);
    }

    // ==================== retrieveMessagesByFriendAndTimestamp Tests ====================

    @Test
    void retrieveMessagesByFriendAndTimestamp_notFriends_throwsAccessDeniedException() {
        // Given
        Instant timestamp = Instant.now();
        when(friendshipService.areFriends("test@example.com", "friend@example.com")).thenReturn(false);

        // When & Then
        assertThrows(AccessDeniedException.class, () ->
                communicationService.retrieveMessagesByFriendAndTimestamp("test@example.com", "friend@example.com", timestamp, "device123")
        );
    }

    @Test
    void retrieveMessagesByFriendAndTimestamp_unknownDevice_throwsAccessDeniedException() {
        // Given
        Instant timestamp = Instant.now();
        when(friendshipService.areFriends("test@example.com", "friend@example.com")).thenReturn(true);
        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);
        when(userService.findApplicationUserByEmail("friend@example.com")).thenReturn(friendUser);
        when(deviceRepository.findByUserAndDeviceId(testUser, "unknownDevice")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(AccessDeniedException.class, () ->
                communicationService.retrieveMessagesByFriendAndTimestamp("test@example.com", "friend@example.com", timestamp, "unknownDevice")
        );
    }

    @Test
    void retrieveMessagesByFriendAndTimestamp_validRequest_returnsMessages() {
        // Given
        Instant timestamp = Instant.now();
        List<Message> messages = List.of(new Message(), new Message());

        when(friendshipService.areFriends("test@example.com", "friend@example.com")).thenReturn(true);
        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);
        when(userService.findApplicationUserByEmail("friend@example.com")).thenReturn(friendUser);
        when(deviceRepository.findByUserAndDeviceId(testUser, "device123")).thenReturn(Optional.of(testDevice));
        when(messageRepository.findConversationSince(testUser, friendUser, timestamp, "device123")).thenReturn(messages);

        // When
        List<Message> result = communicationService.retrieveMessagesByFriendAndTimestamp("test@example.com", "friend@example.com", timestamp, "device123");

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(messageRepository).findConversationSince(testUser, friendUser, timestamp, "device123");
    }

    // ==================== getDevicesOfFriend Tests ====================

    @Test
    void getDevicesOfFriend_notFriends_throwsAccessDeniedException() {
        // Given
        when(friendshipService.areFriends("test@example.com", "friend@example.com")).thenReturn(false);

        // When & Then
        assertThrows(AccessDeniedException.class, () ->
                communicationService.getDevicesOfFriend("test@example.com", "friend@example.com")
        );
    }

    @Test
    void getDevicesOfFriend_friends_returnsDeviceIds() {
        // Given
        UserDevice device1 = new UserDevice();
        device1.setDeviceId("device1");
        UserDevice device2 = new UserDevice();
        device2.setDeviceId("device2");

        when(friendshipService.areFriends("test@example.com", "friend@example.com")).thenReturn(true);
        when(userService.findApplicationUserByEmail("friend@example.com")).thenReturn(friendUser);
        when(deviceRepository.findAllByUser(friendUser)).thenReturn(List.of(device1, device2));

        // When
        List<String> result = communicationService.getDevicesOfFriend("test@example.com", "friend@example.com");

        // Then

        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(2, result.size()),
                () -> assertTrue(result.contains("device1")),
                () -> assertTrue(result.contains("device2")));
    }

    // ==================== getMyDevices Tests ====================

    @Test
    void getMyDevices_returnsAllDeviceIds() {
        // Given
        UserDevice device1 = new UserDevice();
        device1.setDeviceId("myDevice1");
        UserDevice device2 = new UserDevice();
        device2.setDeviceId("myDevice2");

        when(userService.findApplicationUserByEmail("test@example.com")).thenReturn(testUser);
        when(deviceRepository.findAllByUser(testUser)).thenReturn(List.of(device1, device2));

        // When
        List<String> result = communicationService.getMyDevices("test@example.com");
        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(2, result.size()),
                () -> assertTrue(result.contains("myDevice1")));
    }

    MessageDetailDto createValidMessageDto(String email) {
        return new MessageDetailDto() {{
            setSenderDeviceId("device123");
            setRecipientEmail(email);
            setRecipientDeviceId("friendDevice123");
            setEncryptedMessage(new EncryptedMessageDto() {{
                setCiphertext("ciphertext");
                setNonce("nonce");
                setMessageNumber(1L);
                setRatchetPublicKey("publicKey");
            }});
        }};
    }
}
