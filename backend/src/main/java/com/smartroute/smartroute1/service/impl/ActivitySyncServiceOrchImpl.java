package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.service.ActivityService;
import com.smartroute.smartroute1.service.ActivitySyncServiceOrch;
import com.smartroute.smartroute1.service.SyncStatusStore;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@AllArgsConstructor
@Service
@Slf4j
public class ActivitySyncServiceOrchImpl implements ActivitySyncServiceOrch {


    private final ActivityService activityService;
    private final SyncStatusStore syncStatusStore;

    @Override
    public void synchronize(String email, int count, UUID requestId) throws Exception {
        log.trace(
                "Starting activity sync [requestId={}, email={}, count={}]",
                requestId, email, count
        );
        syncStatusStore.running(requestId, email);
        try {
            activityService.synchronize(email, count);
            syncStatusStore.success(requestId, email);
            log.trace(
                    "Finished activity sync successfully [requestId={}, email={}]",
                    requestId, email
            );
        } catch (Exception e) {
            log.trace(
                    "Activity sync failed [requestId={}, email={}]",
                    requestId, email,
                    e
            );
            syncStatusStore.failed(requestId, email, e.getMessage());
            throw e;
        }
    }
}
