package com.smartroute.smartroute1.endpoint.dto;

import lombok.Data;

@Data
public class GarminConnectAccountDto {
    private String garminEmail;
    private String garminPassword;
    private Integer count;
}

