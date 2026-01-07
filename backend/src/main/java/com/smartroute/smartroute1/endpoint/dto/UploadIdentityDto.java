package com.smartroute.smartroute1.endpoint.dto;

import lombok.Data;

@Data
public class UploadIdentityDto {
    private String publicKey;
    private String publicDHKey;
}
