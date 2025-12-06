package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Friendship;
import com.smartroute.smartroute1.entity.enums.FriendshipStatus;
import com.smartroute.smartroute1.exception.ConflictException;
import com.smartroute.smartroute1.exception.NotFoundException;
import com.smartroute.smartroute1.repository.FriendshipRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.FriendshipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import jakarta.transaction.Transactional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles({"test", "generateData"})
@Transactional
class FriendshipServiceTest {

    @Autowired
    private FriendshipService friendshipService;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private UserRepository userRepository;

    private ApplicationUser alice;
    private ApplicationUser bob;
    private ApplicationUser charlie;

    @BeforeEach
    void setUp() {
        friendshipRepository.deleteAll();
        alice = userRepository.findUserByEmail("alice@example.com");
        if (alice == null) {
            alice = userRepository.save(new ApplicationUser("alice@example.com", "pw", "Alice", "A"));
        }
        bob = userRepository.findUserByEmail("bob@example.com");
        if (bob == null) {
            bob = userRepository.save(new ApplicationUser("bob@example.com", "pw", "Bob", "B"));
        }
        charlie = userRepository.findUserByEmail("charlie@example.com");
        if (charlie == null) {
            charlie = userRepository.save(new ApplicationUser("charlie@example.com", "pw", "C", "C"));
        }
    }

    @Test
    void sendFriendRequest_createsPendingWhenNoExisting() throws Exception {
        Friendship result = friendshipService.sendFriendRequest("alice@example.com", "bob@example.com");

        assertAll(
            () -> assertEquals(FriendshipStatus.PENDING, result.getStatus()),
            () -> assertEquals("alice@example.com", result.getSender().getEmail()),
            () -> assertEquals("bob@example.com", result.getReceiver().getEmail())
        );

        // Ensure repository persisted it
        assertTrue(friendshipRepository.findById(result.getId()).isPresent());
    }

    @Test
    void sendFriendRequest_receiverNotFound_throwsNotFound() {
        assertThrows(NotFoundException.class, () -> friendshipService.sendFriendRequest("alice@example.com", "unknown@example.com"));
    }

    @Test
    void sendFriendRequest_toSelf_throwsConflict() {
        assertThrows(ConflictException.class, () -> friendshipService.sendFriendRequest("alice@example.com", "alice@example.com"));
    }

    @Test
    void sendFriendRequest_existingAlreadyAccepted_throwsConflict() {
        Friendship existing = new Friendship();
        existing.setSender(alice);
        existing.setReceiver(bob);
        existing.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(existing);

        assertThrows(ConflictException.class, () -> friendshipService.sendFriendRequest("alice@example.com", "bob@example.com"));
    }

    @Test
    void sendFriendRequest_existingPendingReverse_acceptsAndSaves() throws Exception {
        Friendship existing = new Friendship();
        existing.setSender(bob);
        existing.setReceiver(alice);
        existing.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(existing);

        Friendship result = friendshipService.sendFriendRequest("alice@example.com", "bob@example.com");

        assertEquals(FriendshipStatus.ACCEPTED, result.getStatus());

        // repo shows accepted
        Friendship fromDb = friendshipRepository.findById(result.getId()).orElseThrow();
        assertEquals(FriendshipStatus.ACCEPTED, fromDb.getStatus());
    }

    @Test
    void sendFriendRequest_existingPendingSameDirection_throwsConflict() {
        Friendship existing = new Friendship();
        existing.setSender(alice);
        existing.setReceiver(bob);
        existing.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(existing);

        assertThrows(ConflictException.class, () -> friendshipService.sendFriendRequest("alice@example.com", "bob@example.com"));
    }

    @Test
    void cancelFriendRequest_notFound_throwsNotFound() {
        assertThrows(NotFoundException.class, () -> friendshipService.cancelFriendRequest("alice@example.com", 999L));
    }

    @Test
    void cancelFriendRequest_notSender_throwsAccessDenied() {
        Friendship f = new Friendship();
        f.setSender(bob);
        f.setReceiver(alice);
        f.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(f);

        assertThrows(AccessDeniedException.class, () -> friendshipService.cancelFriendRequest("alice@example.com", f.getId()));
    }

    @Test
    void cancelFriendRequest_notPending_throwsConflict() {
        Friendship f = new Friendship();
        f.setSender(alice);
        f.setReceiver(bob);
        f.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(f);

        assertThrows(ConflictException.class, () -> friendshipService.cancelFriendRequest("alice@example.com", f.getId()));
    }

    @Test
    void cancelFriendRequest_success_deletesFriendRequest() throws Exception {
        Friendship f = new Friendship();
        f.setSender(alice);
        f.setReceiver(bob);
        f.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(f);

        friendshipService.cancelFriendRequest("alice@example.com", f.getId());

        assertFalse(friendshipRepository.findById(f.getId()).isPresent());
    }

    @Test
    void acceptFriendRequest_notFound_throwsNotFound() {
        assertThrows(NotFoundException.class, () -> friendshipService.acceptFriendRequest("bob@example.com", 123456L));
    }

    @Test
    void acceptFriendRequest_notReceiver_throwsAccessDenied() {
        Friendship f = new Friendship();
        f.setSender(bob);
        f.setReceiver(bob); // receiver is bob, will test with alice
        f.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(f);

        assertThrows(AccessDeniedException.class, () -> friendshipService.acceptFriendRequest("alice@example.com", f.getId()));
    }

    @Test
    void acceptFriendRequest_notPending_throwsConflict() {
        Friendship f = new Friendship();
        f.setSender(bob);
        f.setReceiver(alice);
        f.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(f);

        assertThrows(ConflictException.class, () -> friendshipService.acceptFriendRequest("alice@example.com", f.getId()));
    }

    @Test
    void acceptFriendRequest_success_setsAcceptedAndSaves() throws Exception {
        Friendship f = new Friendship();
        f.setSender(bob);
        f.setReceiver(alice);
        f.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(f);

        Friendship result = friendshipService.acceptFriendRequest("alice@example.com", f.getId());

        assertEquals(FriendshipStatus.ACCEPTED, result.getStatus());
        Friendship fromDb = friendshipRepository.findById(f.getId()).orElseThrow();
        assertEquals(FriendshipStatus.ACCEPTED, fromDb.getStatus());
    }

    @Test
    void rejectFriendRequest_notFound_throwsNotFound() {
        assertThrows(NotFoundException.class, () -> friendshipService.rejectFriendRequest("alice@example.com", 333L));
    }

    @Test
    void rejectFriendRequest_notReceiver_throwsAccessDenied() {
        Friendship f = new Friendship();
        f.setSender(alice);
        f.setReceiver(alice);
        f.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(f);

        assertThrows(AccessDeniedException.class, () -> friendshipService.rejectFriendRequest("bob@example.com", f.getId()));
    }

    @Test
    void rejectFriendRequest_notPending_throwsConflict() {
        Friendship f = new Friendship();
        f.setSender(alice);
        f.setReceiver(bob);
        f.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(f);

        assertThrows(ConflictException.class, () -> friendshipService.rejectFriendRequest("bob@example.com", f.getId()));
    }

    @Test
    void rejectFriendRequest_success_deletesFriendship() throws Exception {
        Friendship f = new Friendship();
        f.setSender(alice);
        f.setReceiver(bob);
        f.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(f);

        friendshipService.rejectFriendRequest("bob@example.com", f.getId());

        assertFalse(friendshipRepository.findById(f.getId()).isPresent());
    }

    @Test
    void removeFriend_notFound_throwsNotFound() {
        assertThrows(NotFoundException.class, () -> friendshipService.removeFriend("alice@example.com", 777L));
    }

    @Test
    void removeFriend_userNotPart_throwsAccessDenied() {
        Friendship f = new Friendship();
        f.setSender(alice);
        f.setReceiver(bob);
        f.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(f);

        assertThrows(AccessDeniedException.class, () -> friendshipService.removeFriend("charlie@example.com", f.getId()));
    }

    @Test
    void removeFriend_notAccepted_throwsConflict() {
        Friendship f = new Friendship();
        f.setSender(alice);
        f.setReceiver(bob);
        f.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(f);

        assertThrows(ConflictException.class, () -> friendshipService.removeFriend("alice@example.com", f.getId()));
    }

    @Test
    void removeFriend_success_deletesFriendship() throws Exception {
        Friendship f = new Friendship();
        f.setSender(alice);
        f.setReceiver(bob);
        f.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(f);

        friendshipService.removeFriend("alice@example.com", f.getId());

        assertFalse(friendshipRepository.findById(f.getId()).isPresent());
    }

    @Test
    void getFriends_returnsAcceptedFriends() {
        // accepted friend where alice is sender
        Friendship f1 = new Friendship();
        f1.setSender(alice);
        f1.setReceiver(bob);
        f1.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(f1);

        // accepted friend where alice is receiver
        Friendship f2 = new Friendship();
        f2.setSender(charlie);
        f2.setReceiver(alice);
        f2.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(f2);

        List<Friendship> friends = friendshipService.getFriends("alice@example.com");

        assertAll(
            () -> assertEquals(2, friends.size()),
            () -> assertTrue(friends.stream().allMatch(f -> f.getStatus() == FriendshipStatus.ACCEPTED)),
            () -> assertTrue(friends.stream().anyMatch(f ->
                (f.getSender().getEmail().equals("bob@example.com") || f.getReceiver().getEmail().equals("bob@example.com"))
            )),
            () -> assertTrue(friends.stream().anyMatch(f ->
                (f.getSender().getEmail().equals("charlie@example.com") || f.getReceiver().getEmail().equals("charlie@example.com"))
            ))
        );
    }

    @Test
    void getIncomingFriendRequests_returnsPendingIncoming() {
        // pending incoming (bob -> alice)
        Friendship incoming = new Friendship();
        incoming.setSender(bob);
        incoming.setReceiver(alice);
        incoming.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(incoming);

        // other pending (alice -> charlie) should not appear as incoming for alice
        Friendship other = new Friendship();
        other.setSender(alice);
        other.setReceiver(charlie);
        other.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(other);

        List<Friendship> incomingList = friendshipService.getIncomingFriendRequests("alice@example.com");

        assertAll(
            () -> assertEquals(1, incomingList.size()),
            () -> assertTrue(incomingList.stream().allMatch(f -> f.getStatus() == FriendshipStatus.PENDING)),
            () -> assertTrue(incomingList.stream().anyMatch(f -> f.getSender().getEmail().equals("bob@example.com")))
        );
    }

    @Test
    void getOutgoingFriendRequests_returnsPendingOutgoing() {
        // pending outgoing (alice -> bob)
        Friendship outgoing = new Friendship();
        outgoing.setSender(alice);
        outgoing.setReceiver(bob);
        outgoing.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(outgoing);

        // other pending (charlie -> alice) should not appear as outgoing for alice
        Friendship other = new Friendship();
        other.setSender(charlie);
        other.setReceiver(alice);
        other.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(other);

        List<Friendship> outgoingList = friendshipService.getOutgoingFriendRequests("alice@example.com");

        assertAll(
            () -> assertEquals(1, outgoingList.size()),
            () -> assertTrue(outgoingList.stream().allMatch(f -> f.getStatus() == FriendshipStatus.PENDING)),
            () -> assertTrue(outgoingList.stream().anyMatch(f -> f.getReceiver().getEmail().equals("bob@example.com")))
        );
    }
}
