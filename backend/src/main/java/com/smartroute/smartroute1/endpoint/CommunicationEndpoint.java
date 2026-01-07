package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.KeysDto;
import com.smartroute.smartroute1.endpoint.dto.MessageDetailDto;
import com.smartroute.smartroute1.endpoint.dto.UploadIdentityDto;
import com.smartroute.smartroute1.endpoint.dto.UploadOneTimePreKeysDto;
import com.smartroute.smartroute1.endpoint.dto.UploadPreKeyDto;
import com.smartroute.smartroute1.endpoint.dto.UserDetailDto;
import com.smartroute.smartroute1.endpoint.mapper.MessageMapper;
import com.smartroute.smartroute1.endpoint.mapper.UserMapper;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Message;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.service.CommunicationService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/communication")
@RequiredArgsConstructor
public class CommunicationEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final CommunicationService communicationService;
    private final UserMapper userMapper;
    private final MessageMapper messageMapper;

    @PutMapping("/upload-identity-key")
    @Secured("ROLE_USER")
    @Operation(summary = "Upload identity key", description = "A user can upload their public identity key for other users")
    public UserDetailDto uploadIdentityKey(@RequestBody UploadIdentityDto uploadIdentityDto) {
        LOGGER.info("POST /api/v1/communication/upload-identity-key");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ApplicationUser updatedUser = communicationService.uploadIdentityKey(
                authentication.getName(),
                uploadIdentityDto.getPublicKey(),
                uploadIdentityDto.getPublicDHKey()
        );
        return userMapper.applicationUserToDetailDto(updatedUser);
    }

    @PutMapping("/upload-signed-pre-key")
    @Secured("ROLE_USER")
    @Operation(summary = "Upload signed pre key", description = "A user can upload their signed pre key for other users")
    public UserDetailDto uploadSignedPreKey(@RequestBody UploadPreKeyDto uploadPreKeyDto) {
        LOGGER.info("POST /api/v1/communication/upload-signed-pre-key");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ApplicationUser updatedUser = communicationService.uploadSignedPreKey(
            authentication.getName(),
            uploadPreKeyDto.getPublicPreKey(),
            uploadPreKeyDto.getSignature()
        );
        return userMapper.applicationUserToDetailDto(updatedUser);
    }

    @GetMapping("/amount-of-one-time-pre-keys")
    @Secured("ROLE_USER")
    @Operation(summary = "Get amount of one-time pre keys", description = "A user can get the amount of their one-time pre keys stored on the server")
    public long getAmountOfOneTimePreKeys() {
        LOGGER.info("GET /api/v1/communication/amount-of-one-time-pre-keys");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return communicationService.countOneTimePreKeys(authentication.getName());
    }

    @PutMapping("/upload-one-time-pre-keys")
    @Secured("ROLE_USER")
    @Operation(summary = "Upload one-time pre keys", description = "A user can upload their one-time pre keys for other users")
    public void uploadOneTimePreKeys(@RequestBody UploadOneTimePreKeysDto uploadOneTimePreKeysDto) {
        LOGGER.info("POST /api/v1/communication/upload-one-time-pre-keys");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        communicationService.uploadOneTimePreKeys(authentication.getName(), uploadOneTimePreKeysDto.getOneTimePreKeys());
    }

    @GetMapping("/keys-of-friend/{friendEmail}")
    @Secured("ROLE_USER")
    @Operation(summary = "Get keys of friend", description = "A user can get the communication keys of a friend")
    public KeysDto getKeysOfFriend(@PathVariable("friendEmail") String friendEmail) {
        LOGGER.info("GET /api/v1/communication/keys-of-friend/{}", friendEmail);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return communicationService.getKeysOfFriend(friendEmail, authentication.getName());
    }

    @PostMapping("/messages")
    @Secured("ROLE_USER")
    @Operation(summary = "Send encrypted message", description = "A user can send an encrypted message to another user")
    public MessageDetailDto sendEncryptedMessage(@RequestBody MessageDetailDto messageDetailDto) throws ValidationException {
        LOGGER.info("POST /api/v1/communication/messages");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return messageMapper.entityToMessageDetailDto(communicationService.sendEncryptedMessage(authentication.getName(), messageDetailDto));
    }

    @GetMapping("/messages/{friendEmail}")
    @Secured("ROLE_USER")
    @Operation(summary = "Get messages", description = "A user can get all messages exchanged with a friend after a specific timestamp")
    public List<MessageDetailDto> getMessages(@PathVariable("friendEmail") String friendEmail, @RequestParam("timestamp") Instant timestamp) {
        LOGGER.info("GET /api/v1/communication/messages/{}", friendEmail);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<Message> messages = communicationService.retrieveMessagesByFriendAndTimestamp(authentication.getName(), friendEmail, timestamp);
        return messages.stream()
            .map(messageMapper::entityToMessageDetailDto)
            .toList();
    }

}
