package com.smartroute.smartroute1.endpoint.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class OneTimePreKeyDto {
    private UUID uuid;
    private String publicKey;
}
