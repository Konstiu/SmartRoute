package com.smartroute.smartroute1.endpoint.dto;

import lombok.Data;

@Data
public class KeysDto {
    private String identityKey;
    private String signedPreKey;
    private String signedPreKeySignature;
    private OneTimePreKeyDto oneTimePreKey;
}
