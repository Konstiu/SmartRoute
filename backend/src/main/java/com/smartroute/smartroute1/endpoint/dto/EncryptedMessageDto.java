package com.smartroute.smartroute1.endpoint.dto;

import lombok.Data;

@Data
public class EncryptedMessageDto {
    private String ciphertext;
    private String nonce;
    private Long messageNumber;
    private String ratchetPublicKey;
}
