package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.endpoint.dto.OneTimePreKeyDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.PreKey;
import com.smartroute.smartroute1.exception.NotFoundException;
import com.smartroute.smartroute1.repository.PreKeyRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.CommunicationService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles({"test", "generateData"})
@Transactional
class CommunicationServiceTest {

    @Autowired
    private CommunicationService communicationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PreKeyRepository preKeyRepository;

    @Test
    void uploadIdentityKey_withExistingUser_shouldSetPublicKeyAndReturnUser() {
        // arrange
        ApplicationUser user = new ApplicationUser("commtest@example.com", "pw", "Comm", "Test");
        userRepository.save(user);

        String publicKey = "PUBLIC_KEY_ABC123";

        // act
        ApplicationUser updated = communicationService.uploadIdentityKey(user.getEmail(), publicKey);

        // assert
        assertNotNull(updated);
        assertAll(
            () -> assertEquals(user.getEmail(), updated.getEmail()),
            () -> assertEquals(publicKey, updated.getPublicIdentityKey())
        );

        // verify persisted
        ApplicationUser fromDb = userRepository.findUserByEmail(user.getEmail());
        assertNotNull(fromDb);
        assertEquals(publicKey, fromDb.getPublicIdentityKey());
    }

    @Test
    void uploadIdentityKey_withNonExistingUser_shouldThrowNotFoundException() {
        assertThrows(NotFoundException.class, () -> communicationService.uploadIdentityKey("unknown@example.com", "KEY"));
    }

    @Test
    void uploadSignedPreKey_withExistingUser_shouldSetPreKeyAndSignatureAndReturnUser() {
        // arrange
        ApplicationUser user = new ApplicationUser("prekeytest@example.com", "pw", "Pre", "Key");
        userRepository.save(user);

        String publicPreKey = "PRE_KEY_123";
        String signature = "SIG_ABC";

        // act
        ApplicationUser updated = communicationService.uploadSignedPreKey(user.getEmail(), publicPreKey, signature);

        // assert
        assertNotNull(updated);
        assertAll(
            () -> assertEquals(user.getEmail(), updated.getEmail()),
            () -> assertEquals(publicPreKey, updated.getPublicPreKey()),
            () -> assertEquals(signature, updated.getPreKeySignature())
        );

        // verify persisted
        ApplicationUser fromDb = userRepository.findUserByEmail(user.getEmail());
        assertNotNull(fromDb);
        assertAll(
            () -> assertEquals(publicPreKey, fromDb.getPublicPreKey()),
            () -> assertEquals(signature, fromDb.getPreKeySignature())
        );
    }

    @Test
    void uploadSignedPreKey_withNonExistingUser_shouldThrowNotFoundException() {
        assertThrows(NotFoundException.class,
                () -> communicationService.uploadSignedPreKey("doesnotexist@example.com", "PRE", "SIG"));
    }


    @Test
    void uploadOneTimePreKeys_withNewKeys_shouldPersistPreKeys() {
        // arrange
        ApplicationUser user = new ApplicationUser("otp_user@example.com", "pw", "OT", "P");
        userRepository.save(user);

        OneTimePreKeyDto dto1 = new OneTimePreKeyDto();
        dto1.setUuid(UUID.randomUUID());
        dto1.setPublicKey("ONE_KEY_1");

        OneTimePreKeyDto dto2 = new OneTimePreKeyDto();
        dto2.setUuid(UUID.randomUUID());
        dto2.setPublicKey("ONE_KEY_2");

        // act
        communicationService.uploadOneTimePreKeys(user.getEmail(), List.of(dto1, dto2));

        // assert
        long count = preKeyRepository.countByUserId(user.getId());
        assertEquals(2, count, "Two pre-keys should have been persisted");

        ApplicationUser fromDb = userRepository.findUserByEmail(user.getEmail());
        assertNotNull(fromDb);
        assertEquals(2, fromDb.getOneTimePreKeys().size());
    }

    @Test
    void uploadOneTimePreKeys_skipsExistingUuid_and_doesNotDuplicate() {
        // arrange
        ApplicationUser user = new ApplicationUser("otp_existing@example.com", "pw", "OT", "P");
        userRepository.save(user);

        UUID existingUuid = UUID.randomUUID();
        PreKey existing = new PreKey();
        existing.setUuid(existingUuid);
        existing.setPublicKey("EXISTING_KEY");
        existing.setUser(user);
        preKeyRepository.save(existing);

        OneTimePreKeyDto dto1 = new OneTimePreKeyDto();
        dto1.setUuid(existingUuid);
        dto1.setPublicKey("ONE_KEY_SHOULD_BE_SKIPPED");

        OneTimePreKeyDto dto2 = new OneTimePreKeyDto();
        dto2.setUuid(UUID.randomUUID());
        dto2.setPublicKey("ONE_KEY_NEW");

        // act
        communicationService.uploadOneTimePreKeys(user.getEmail(), List.of(dto1, dto2));

        // assert
        long count = preKeyRepository.countByUserId(user.getId());
        assertEquals(2, count, "Only the new pre-key should be added in addition to the existing one");
    }

    @Test
    void countOneTimePreKeys_returnsCorrectNumber() {
        // arrange
        ApplicationUser user = new ApplicationUser("count_user@example.com", "pw", "Count", "Test");
        userRepository.save(user);

        PreKey p1 = new PreKey();
        p1.setUuid(UUID.randomUUID());
        p1.setPublicKey("C1");
        p1.setUser(user);
        preKeyRepository.save(p1);

        PreKey p2 = new PreKey();
        p2.setUuid(UUID.randomUUID());
        p2.setPublicKey("C2");
        p2.setUser(user);
        preKeyRepository.save(p2);

        // act
        long count = communicationService.countOneTimePreKeys(user.getEmail());

        // assert
        assertEquals(2, count);
    }

}
