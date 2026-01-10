package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.DeviceKeysDto;
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
import com.smartroute.smartroute1.service.CommunicationService;
import com.smartroute.smartroute1.service.FriendshipService;
import com.smartroute.smartroute1.service.UserService;
import com.smartroute.smartroute1.websocket.ChatWebSocketHandler;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunicationServiceImpl implements CommunicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final UserService userService;
    private final UserRepository userRepository;
    private final PreKeyRepository preKeyRepository;
    private final FriendshipService friendshipService;
    private final MessageRepository messageRepository;
    private final MessageMapper messageMapper;
    private final DeviceRepository deviceRepository;

    private static final int MAX_ONE_TIME_PRE_KEYS = 150;

    @Transactional
    @Override
    public ApplicationUser uploadIdentityKey(String email, String deviceId, String publicKey, String publicDhKey) {
        LOGGER.trace("uploadIdentityKey({}, {}, {}, {})", email, deviceId, publicKey, publicDhKey);
        ApplicationUser user = userService.findApplicationUserByEmail(email);

        UserDevice device = deviceRepository
                .findByUserAndDeviceId(user, deviceId)
                .orElseGet(() -> {
                    UserDevice d = new UserDevice();
                    d.setUser(user);
                    d.setDeviceId(deviceId);
                    return d;
                });

        device.setPublicIdentityKey(publicKey);
        device.setPublicIdentityDhKey(publicDhKey);

        deviceRepository.save(device);
        return user;
    }


    @Override
    @Transactional
    public ApplicationUser uploadSignedPreKey(String email, String publicPreKey, String signature, String deviceId) throws ValidationException {
        LOGGER.trace("uploadSignedPreKey({}, {}, {}, {})", email, publicPreKey, signature, deviceId);
        ApplicationUser user = userService.findApplicationUserByEmail(email);

        UserDevice device = deviceRepository.findByUserAndDeviceId(user, deviceId)
                .orElseThrow(() -> new ValidationException("Unknown device", List.of()));

        device.setPublicPreKey(publicPreKey);
        device.setPreKeySignature(signature);

        deviceRepository.save(device);
        return user;
    }

    @Override
    @Transactional
    public void uploadOneTimePreKeys(String email, String deviceId, List<OneTimePreKeyDto> publicPreKeys) throws ValidationException {
        LOGGER.trace("uploadOneTimePreKeys({}, {}, keys={})",
                email, deviceId, publicPreKeys == null ? 0 : publicPreKeys.size());

        ApplicationUser user = userService.findApplicationUserByEmail(email);

        if (publicPreKeys == null || publicPreKeys.isEmpty()) {
            return;
        }

        UserDevice device = deviceRepository.findByUserAndDeviceId(user, deviceId)
                .orElseThrow(() -> new ValidationException("Unknown device", List.of()));

        long currentCount = preKeyRepository.countByDevice_Id(device.getId());
        long availableSlots = MAX_ONE_TIME_PRE_KEYS - currentCount;

        if (availableSlots <= 0) {
            return;
        }

        int toInsert = (int) Math.min(availableSlots, publicPreKeys.size());

        for (int i = 0; i < toInsert; i++) {
            OneTimePreKeyDto dto = publicPreKeys.get(i);

            // Check if this prekey UUID already exists for this device
            boolean exists = preKeyRepository.existsByDevice_IdAndUuid(device.getId(), dto.getUuid());

            if (exists) {
                continue;
            }

            PreKey preKey = new PreKey();
            preKey.setUuid(dto.getUuid());
            preKey.setPublicKey(dto.getPublicKey());
            preKey.setDevice(device);

            preKeyRepository.save(preKey);
        }
    }

    @Transactional
    @Override
    public long countOneTimePreKeys(String email, String deviceId) throws ValidationException {
        LOGGER.trace("countOneTimePreKeys({}, {})", email, deviceId);
        ApplicationUser user = userService.findApplicationUserByEmail(email);

        UserDevice device = deviceRepository.findByUserAndDeviceId(user, deviceId)
                .orElseThrow(() -> new ValidationException("Unknown device", List.of()));

        return preKeyRepository.countByDevice_Id(device.getId());
    }

    @Transactional
    @Override
    public FriendDeviceBundlesDto getKeysOfFriendAllDevices(String friendEmail, String userEmail) {
        LOGGER.trace("getKeysOfFriendAllDevices({}, {})", friendEmail, userEmail);

        if (!friendshipService.areFriends(userEmail, friendEmail)) {
            throw new AccessDeniedException("You are not allowed to access this friend");
        }

        ApplicationUser friend = userService.findApplicationUserByEmail(friendEmail);

        List<UserDevice> devices = deviceRepository.findAllByUser(friend);

        FriendDeviceBundlesDto out = new FriendDeviceBundlesDto();
        out.setDevices(devices.stream().map(device -> {
            return getDeviceKeysDto(device);
        }).toList());

        return out;
    }

    @Override
    public FriendDeviceBundlesDto getKeysOfAllMyDevices(String userEmail) {
        LOGGER.trace("getKeysOfAllMyDevices({})", userEmail);
        ApplicationUser user = userService.findApplicationUserByEmail(userEmail);
        List<UserDevice> devices = deviceRepository.findAllByUser(user);
        FriendDeviceBundlesDto out = new FriendDeviceBundlesDto();
        out.setDevices(devices.stream().map(this::getDeviceKeysDto).toList());
        return out;
    }

    @Override
    @Transactional
    public Message sendEncryptedMessage(String senderEmail, MessageDetailDto messageDetailDto) throws ValidationException {
        LOGGER.trace("sendEncryptedMessage({}, {})", senderEmail, messageDetailDto);
        if (!friendshipService.areFriends(senderEmail, messageDetailDto.getRecipientEmail())) {
            throw new AccessDeniedException("You are not allowed to send messages to this user");
        }

        validateMessageDetailDto(messageDetailDto);

        ApplicationUser sender = userService.findApplicationUserByEmail(senderEmail);
        ApplicationUser recipient = userService.findApplicationUserByEmail(messageDetailDto.getRecipientEmail());

        // Validate sender device
        UserDevice senderDevice = deviceRepository.findByUserAndDeviceId(sender, messageDetailDto.getSenderDeviceId())
                .orElseThrow(() -> new ValidationException("Unknown sender device", List.of()));

        // Validate recipient device
        UserDevice recipientDevice = deviceRepository.findByUserAndDeviceId(recipient, messageDetailDto.getRecipientDeviceId())
                .orElseThrow(() -> new ValidationException("Unknown recipient device", List.of()));

        // Map & persist (Message must store senderDeviceId/recipientDeviceId)
        Message message = messageMapper.messageDetailDtoToEntity(messageDetailDto, sender, recipient);
        message.setSenderDeviceId(senderDevice.getDeviceId());
        message.setRecipientDeviceId(recipientDevice.getDeviceId());

        Message saved = messageRepository.save(message);

        // Notify recipient user; their clients will filter by recipientDeviceId
        ChatWebSocketHandler.notifyUser(recipient.getEmail(), sender.getEmail());

        return saved;
    }

    @Override
    public Message sendEncryptedMessageToMyDevices(String senderEmail, MessageDetailDto messageDetailDto) throws ValidationException {
        LOGGER.trace("sendEncryptedMessageToMyDevices({}, {})", senderEmail, messageDetailDto);

        validateMessageDetailDto(messageDetailDto);

        ApplicationUser sender = userService.findApplicationUserByEmail(senderEmail);
        ApplicationUser recipient = userService.findApplicationUserByEmail(messageDetailDto.getRecipientEmail());

        // Validate sender device
        UserDevice senderDevice = deviceRepository.findByUserAndDeviceId(sender, messageDetailDto.getSenderDeviceId())
                .orElseThrow(() -> new ValidationException("Unknown sender device", List.of()));

        // Validate recipient device
        UserDevice recipientDevice = deviceRepository.findByUserAndDeviceId(sender, messageDetailDto.getRecipientDeviceId())
                .orElseThrow(() -> new ValidationException("Unknown recipient device", List.of()));

        // Map & persist (Message must store senderDeviceId/recipientDeviceId)
        Message message = messageMapper.messageDetailDtoToEntity(messageDetailDto, sender, recipient);
        message.setSenderDeviceId(senderDevice.getDeviceId());
        message.setRecipientDeviceId(recipientDevice.getDeviceId());

        Message saved = messageRepository.save(message);

        LOGGER.info(recipientDevice.getDeviceId(), senderDevice.getDeviceId());
        LOGGER.info(saved.toString());

        // Notify recipient user; their clients will filter by recipientDeviceId
        ChatWebSocketHandler.notifyUser(recipient.getEmail(), sender.getEmail());
        return saved;
    }

    private void validateMessageDetailDto(MessageDetailDto messageDetailDto) throws ValidationException {
        if (messageDetailDto == null
                || messageDetailDto.getEncryptedMessage() == null
                || messageDetailDto.getEncryptedMessage().getCiphertext() == null
                || messageDetailDto.getEncryptedMessage().getNonce() == null
                || messageDetailDto.getEncryptedMessage().getRatchetPublicKey() == null
                || messageDetailDto.getSenderDeviceId() == null
                || messageDetailDto.getRecipientDeviceId() == null) {
            throw new ValidationException("Invalid MessageDetailDto", List.of());
        }
    }

    @Override
    @Transactional
    public List<Message> retrieveMessagesByFriendAndTimestamp(String userEmail, String friendEmail, Instant timestamp, String deviceId) {
        LOGGER.trace("retrieveMessagesByFriendAndTimestamp({}, {})", userEmail, friendEmail);
        boolean areFriends = friendshipService.areFriends(userEmail, friendEmail);
        if (!areFriends) {
            throw new AccessDeniedException("You are not allowed to access messages of this user");
        }
        ApplicationUser user = userService.findApplicationUserByEmail(userEmail);
        ApplicationUser friend = userService.findApplicationUserByEmail(friendEmail);

        deviceRepository.findByUserAndDeviceId(user, deviceId)
                .orElseThrow(() -> new AccessDeniedException("Unknown device"));

        return messageRepository.findConversationSince(user, friend, timestamp, deviceId);
    }

    @Override
    public List<String> getDevicesOfFriend(String userEmail, String friendEmail) {
        LOGGER.trace("retrieveMessagesByFriendAndTimestamp({}, {})", userEmail, friendEmail);
        boolean areFriends = friendshipService.areFriends(userEmail, friendEmail);
        if (!areFriends) {
            throw new AccessDeniedException("You are not allowed to access messages of this user");
        }
        ApplicationUser friend = userService.findApplicationUserByEmail(friendEmail);

        return deviceRepository.findAllByUser(friend)
                .stream()
                .map(UserDevice::getDeviceId)
                .toList();
    }

    @Override
    public List<String> getMyDevices(String user) {
        LOGGER.trace("getMyDevices({})", user);
        ApplicationUser applicationUser = userService.findApplicationUserByEmail(user);
        return deviceRepository.findAllByUser(applicationUser)
                .stream()
                .map(UserDevice::getDeviceId)
                .toList();
    }

    @Override
    public DeviceKeysDto getPreKeyBundle(String userEmail, String friendEmail, String deviceId) {
        LOGGER.trace("getPreKeyBundle({}, {}, {})", userEmail, friendEmail, deviceId);

        if (!friendshipService.areFriends(userEmail, friendEmail)) {
            throw new AccessDeniedException("You are not allowed to access keys of this user");
        }

        ApplicationUser friend = userService.findApplicationUserByEmail(friendEmail);

        UserDevice device = deviceRepository.findByUserAndDeviceId(friend, deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found"));

        return getDeviceKeysDto(device);
    }

    @NonNull
    private DeviceKeysDto getDeviceKeysDto(UserDevice device) {
        DeviceKeysDto dto = new DeviceKeysDto();
        dto.setDeviceId(device.getDeviceId());
        dto.setIdentityKey(device.getPublicIdentityKey());
        dto.setIdentityDhKey(device.getPublicIdentityDhKey());
        dto.setSignedPreKey(device.getPublicPreKey());
        dto.setSignedPreKeySignature(device.getPreKeySignature());

        PreKey opk = preKeyRepository.findFirstByDevice_IdOrderByIdAsc(device.getId());
        if (opk != null) {
            OneTimePreKeyDto opkDto = new OneTimePreKeyDto();
            opkDto.setUuid(opk.getUuid());
            opkDto.setPublicKey(opk.getPublicKey());
            dto.setOneTimePreKey(opkDto);

            preKeyRepository.delete(opk);
        } else {
            dto.setOneTimePreKey(null);
        }
        return dto;
    }
}