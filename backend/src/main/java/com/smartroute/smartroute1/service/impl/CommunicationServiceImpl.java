package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.KeysDto;
import com.smartroute.smartroute1.endpoint.dto.MessageDetailDto;
import com.smartroute.smartroute1.endpoint.dto.OneTimePreKeyDto;
import com.smartroute.smartroute1.endpoint.mapper.MessageMapper;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Message;
import com.smartroute.smartroute1.entity.PreKey;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.repository.MessageRepository;
import com.smartroute.smartroute1.repository.PreKeyRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.CommunicationService;
import com.smartroute.smartroute1.service.FriendshipService;
import com.smartroute.smartroute1.service.UserService;
import com.smartroute.smartroute1.websocket.ChatWebSocketHandler;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.List;

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

    private static final int MAX_ONE_TIME_PRE_KEYS = 150;

    @Override
    @Transactional
    public ApplicationUser uploadIdentityKey(String email, String publicKey, String publicDhKey) {
        LOGGER.trace("uploadIdentityKey({}, {}, {})", email, publicKey, publicDhKey);
        ApplicationUser user = userService.findApplicationUserByEmail(email);
        user.setPublicIdentityKey(publicKey);
        user.setPublicIdentityDhKey(publicDhKey);
        userRepository.save(user);
        return user;
    }

    @Override
    @Transactional
    public ApplicationUser uploadSignedPreKey(String email, String publicPreKey, String signature) {
        LOGGER.trace("uploadSignedPreKey({}, {}, {})", email, publicPreKey, signature);
        ApplicationUser user = userService.findApplicationUserByEmail(email);
        user.setPublicPreKey(publicPreKey);
        user.setPreKeySignature(signature);
        userRepository.save(user);
        return user;
    }

    @Override
    @Transactional
    public void uploadOneTimePreKeys(String email, List<OneTimePreKeyDto> publicPreKeys) {
        LOGGER.trace("uploadOneTimePreKeys({}, {})", email, publicPreKeys);
        ApplicationUser user = userService.findApplicationUserByEmail(email);

        if (publicPreKeys == null || publicPreKeys.isEmpty()) {
            return;
        }

        long currentCount = preKeyRepository.countByUserId(user.getId());
        long availableSlots = MAX_ONE_TIME_PRE_KEYS - currentCount;

        if (availableSlots <= 0) {
            return;
        }

        int toInsert = (int) Math.min(availableSlots, publicPreKeys.size());

        for (int i = 0; i < toInsert; i++) {
            OneTimePreKeyDto dto = publicPreKeys.get(i);

            if (preKeyRepository.existsByUuid(dto.getUuid())) {
                continue;
            }

            PreKey preKey = new PreKey();
            preKey.setUuid(dto.getUuid());
            preKey.setPublicKey(dto.getPublicKey());
            preKey.setUser(user);

            user.getOneTimePreKeys().add(preKey);
        }
    }

    @Override
    @Transactional
    public long countOneTimePreKeys(String email) {
        LOGGER.trace("countOneTimePreKeys({})", email);
        ApplicationUser user = userService.findApplicationUserByEmail(email);
        return preKeyRepository.countByUserId(user.getId());
    }

    @Override
    @Transactional
    public KeysDto getKeysOfFriend(String friendEmail, String userEmail) {
        LOGGER.trace("getKeysOfFriend({}, {})", friendEmail, userEmail);
        boolean areFriends = friendshipService.areFriends(userEmail, friendEmail);
        if (!areFriends) {
            throw new AccessDeniedException("You are not allowed to access this friend");
        }
        ApplicationUser friend = userService.findApplicationUserByEmail(friendEmail);
        KeysDto keysDto = new KeysDto();
        keysDto.setIdentityKey(friend.getPublicIdentityKey());
        keysDto.setIdentityDhKey(friend.getPublicIdentityDhKey());
        keysDto.setSignedPreKey(friend.getPublicPreKey());
        keysDto.setSignedPreKeySignature(friend.getPreKeySignature());

        PreKey oneTimePreKey = preKeyRepository.findFirstByUserIdOrderByIdAsc(friend.getId()).orElse(null);

        if (oneTimePreKey != null) {
            OneTimePreKeyDto oneTimePreKeyDto = new OneTimePreKeyDto();
            oneTimePreKeyDto.setUuid(oneTimePreKey.getUuid());
            oneTimePreKeyDto.setPublicKey(oneTimePreKey.getPublicKey());
            keysDto.setOneTimePreKey(oneTimePreKeyDto);
            preKeyRepository.delete(oneTimePreKey);
        } else {
            keysDto.setOneTimePreKey(null);
        }
        return keysDto;
    }

    @Override
    @Transactional
    public Message sendEncryptedMessage(String senderEmail, MessageDetailDto messageDetailDto) throws ValidationException {
        LOGGER.trace("sendEncryptedMessage({}, {})", senderEmail, messageDetailDto);
        boolean areFriends = friendshipService.areFriends(senderEmail, messageDetailDto.getRecipientEmail());
        if (!areFriends) {
            throw new AccessDeniedException("You are not allowed to send messages to this user");
        }
        validateMessageDetailDto(messageDetailDto);
        ApplicationUser user = userService.findApplicationUserByEmail(senderEmail);
        ApplicationUser friend = userService.findApplicationUserByEmail(messageDetailDto.getRecipientEmail());
        Message message = messageMapper.messageDetailDtoToEntity(messageDetailDto, user, friend);

        Message savedMessage = messageRepository.save(message);

        ChatWebSocketHandler.notifyUser(friend.getEmail(), user.getEmail());

        return savedMessage;
    }

    private void validateMessageDetailDto(MessageDetailDto messageDetailDto) throws ValidationException {
        if (messageDetailDto == null
            || messageDetailDto.getEncryptedMessage() == null
            || messageDetailDto.getEncryptedMessage().getCiphertext() == null
            || messageDetailDto.getEncryptedMessage().getNonce() == null
            || messageDetailDto.getEncryptedMessage().getRatchetPublicKey() == null) {
            throw new ValidationException("Invalid MessageDetailDto", List.of());
        }
    }

    @Override
    @Transactional
    public List<Message> retrieveMessagesByFriendAndTimestamp(String userEmail, String friendEmail, Instant timestamp) {
        LOGGER.trace("retrieveMessagesByFriendAndTimestamp({}, {})", userEmail, friendEmail);
        boolean areFriends = friendshipService.areFriends(userEmail, friendEmail);
        if (!areFriends) {
            throw new AccessDeniedException("You are not allowed to access messages of this user");
        }
        ApplicationUser user = userService.findApplicationUserByEmail(userEmail);
        ApplicationUser friend = userService.findApplicationUserByEmail(friendEmail);

        return messageRepository.findConversationSince(user, friend, timestamp);
    }
}
