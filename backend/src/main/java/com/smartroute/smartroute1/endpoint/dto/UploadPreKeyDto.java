package com.smartroute.smartroute1.endpoint.dto;

import lombok.Data;

@Data
public class UploadPreKeyDto {
    private String publicPreKey;
    private String signature;
}
