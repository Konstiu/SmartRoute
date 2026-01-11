package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.keysync.KeySyncSessionResponseDto;
import com.smartroute.smartroute1.endpoint.dto.keysync.CreateSessionRequestDto;
import com.smartroute.smartroute1.endpoint.dto.keysync.KeySyncSessionDto;
import com.smartroute.smartroute1.endpoint.dto.keysync.UploadKeysRequestDto;
import com.smartroute.smartroute1.endpoint.dto.keysync.DownloadKeysResponseDto;
import com.smartroute.smartroute1.endpoint.dto.keysync.CheckKeysResponseDto;
import com.smartroute.smartroute1.endpoint.dto.keysync.EncryptedDataRequestDto;
import com.smartroute.smartroute1.endpoint.dto.keysync.SuccessResponseDto;
import com.smartroute.smartroute1.service.KeySyncStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;


@RestController
@RequestMapping("api/v1/key-sync")
@Tag(name = "Key Sync", description = "Endpoints for synchronizing encryption keys between devices")
public class KeySyncEndpoint {

    private static final Logger log = LoggerFactory.getLogger(KeySyncEndpoint.class);

    private final KeySyncStore keySyncStore;

    public KeySyncEndpoint(KeySyncStore keySyncStore) {
        this.keySyncStore = keySyncStore;
    }


    @PostMapping("/create-session")
    @Operation(
            summary = "Create empty session",
            description = "Device without keys creates an empty session to request keys from another device"
    )
    @Secured("ROLE_USER")
    public ResponseEntity<KeySyncSessionResponseDto> createEmptySession(
            @RequestBody CreateSessionRequestDto request) {

        log.info("Creating empty sync session: {}", request.getSessionId());

        keySyncStore.createEmptySession(request.getSessionId(), request.getSessionKey());

        KeySyncSessionDto session = keySyncStore.getSession(request.getSessionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to create session"));

        return ResponseEntity.ok(new KeySyncSessionResponseDto(
                session.getSessionId(),
                session.getSessionKey(),
                session.getExpiresAt()
        ));
    }


    @PostMapping("/upload")
    @Operation(
            summary = "Upload keys with new session",
            description = "Device with keys creates a new session and uploads its encrypted database"
    )
    @Secured("ROLE_USER")
    public ResponseEntity<SuccessResponseDto> uploadKeys(
            @RequestBody UploadKeysRequestDto request) {

        keySyncStore.createSessionWithKeys(request.getSessionId(), request.getEncryptedData());

        return ResponseEntity.ok(new SuccessResponseDto(true, request.getSessionId()));
    }


    @GetMapping("/check/{sessionId}")
    @Operation(
            summary = "Check if session has keys",
            description = "Poll this endpoint to check if another device has uploaded keys to the session"
    )
    @ApiResponse(responseCode = "200", description = "Check completed")
    @Secured("ROLE_USER")
    public ResponseEntity<CheckKeysResponseDto> checkSessionHasKeys(
            @PathVariable String sessionId) {

        log.debug("Checking session for keys: {}", sessionId);

        boolean hasKeys = keySyncStore.sessionHasKeys(sessionId);

        return ResponseEntity.ok(new CheckKeysResponseDto(hasKeys));
    }


    @PutMapping("/session/{sessionId}/keys")
    @Operation(
            summary = "Upload keys to existing session",
            description = "Scan another device's QR code and upload your encrypted database to their session"
    )
    @ApiResponse(responseCode = "200", description = "Keys uploaded successfully")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @ApiResponse(responseCode = "410", description = "Session has expired")
    @Secured("ROLE_USER")
    public ResponseEntity<SuccessResponseDto> uploadKeysToSession(
            @PathVariable String sessionId,
            @RequestBody EncryptedDataRequestDto request) {

        log.info("Uploading keys to existing session: {}", sessionId);

        KeySyncSessionDto session = keySyncStore.getSession(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Session not found or has expired"));

        if (session.isExpired()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Session has expired");
        }

        keySyncStore.uploadKeysToSession(sessionId, request.getEncryptedData());

        return ResponseEntity.ok(new SuccessResponseDto(true, sessionId));
    }

    @GetMapping("/download/{sessionId}")
    @Operation(
            summary = "Download keys from session",
            description = "Download the encrypted database from a session"
    )
    @ApiResponse(responseCode = "200", description = "Keys downloaded successfully")
    @ApiResponse(responseCode = "404", description = "Session not found or no keys available")
    @ApiResponse(responseCode = "410", description = "Session has expired")
    @Secured("ROLE_USER")
    public ResponseEntity<DownloadKeysResponseDto> downloadKeys(
            @PathVariable("sessionId") String sessionId) {

        log.info("Downloading keys from session: {}", sessionId);

        KeySyncSessionDto session = keySyncStore.getSession(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Session not found or has expired"));

        if (session.isExpired()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Session has expired");
        }

        if (!session.isHasKeys() || session.getEncryptedData() == null) {
            log.info(String.valueOf(session.isHasKeys()));
            log.info(session.getEncryptedData());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No keys available in this session");
        }

        // Mark as downloaded
        keySyncStore.markAsDownloaded(sessionId);

        return ResponseEntity.ok(new DownloadKeysResponseDto(session.getEncryptedData()));
    }

    @GetMapping("/stats")
    @Operation(
            summary = "Get sync statistics",
            description = "Get statistics about active sync sessions (for monitoring)"
    )
    @Secured("ROLE_USER")
    public ResponseEntity<SyncStatsResponseDto> getStats() {
        long activeSessions = keySyncStore.getActiveSessionCount();
        return ResponseEntity.ok(new SyncStatsResponseDto(activeSessions));
    }

    /**
     * Stats response DTO.
     */
    public static class SyncStatsResponseDto {
        private long activeSessions;

        public SyncStatsResponseDto(long activeSessions) {
            this.activeSessions = activeSessions;
        }

        public long getActiveSessions() {
            return activeSessions;
        }

        public void setActiveSessions(long activeSessions) {
            this.activeSessions = activeSessions;
        }
    }
}