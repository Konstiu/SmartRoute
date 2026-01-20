package com.smartroute.smartroute1.endpoint.dto;

import lombok.Data;

@Data
public class DeviceKeysDto {
    private String deviceId;
    private String identityKey;
    private String identityDhKey;
    private String signedPreKey;
    private String signedPreKeySignature;
    private OneTimePreKeyDto oneTimePreKey;
}
