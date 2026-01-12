package com.smartroute.smartroute1.endpoint.dto;

import com.smartroute.smartroute1.entity.enums.SyncState;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class SyncStatusStoreEntryDto {
    private String email;
    private SyncState state;
    private Instant updatedAt;
    private String message;
}

