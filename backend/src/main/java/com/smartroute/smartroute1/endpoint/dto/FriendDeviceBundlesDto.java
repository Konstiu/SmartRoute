package com.smartroute.smartroute1.endpoint.dto;

import lombok.Data;

import java.util.List;

@Data
public class FriendDeviceBundlesDto {
    private List<DeviceKeysDto> devices;
}
