package com.smartroute.smartroute1.endpoint.dto;

import lombok.Data;

@Data
public class UploadPreKeyDto {
    private String publicPreKey;
    private String signature;
    private String deviceId;

    @Override
    public String toString() {
        return "UploadPreKeyDto{"
                + "publicPreKey='" + publicPreKey + '\''
                + ", signature='" + signature + '\''
                + ", deviceId='" + deviceId + '\''
                + '}';
    }
}
