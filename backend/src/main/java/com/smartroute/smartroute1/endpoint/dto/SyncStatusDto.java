package com.smartroute.smartroute1.endpoint.dto;

import com.smartroute.smartroute1.entity.enums.SyncState;
import lombok.Data;

@Data
public class SyncStatusDto {
    SyncState state;
    String message;
}
