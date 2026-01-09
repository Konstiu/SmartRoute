package com.smartroute.smartroute1.endpoint.dto;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class MessageDetailDto {
    private Long id;
    private String senderEmail;
    private String recipientEmail;
    private String senderIdentityKey;
    private String senderIdentityDhKey;
    private String senderEphemeralKey;
    private UUID usedOneTimePreKeyId;
    private EncryptedMessageDto encryptedMessage;
    private Instant timestamp;

    private String senderDeviceId;
    private String recipientDeviceId;
}
