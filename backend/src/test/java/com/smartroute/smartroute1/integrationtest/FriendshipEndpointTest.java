package com.smartroute.smartroute1.integrationtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.basetest.BaseTest;
import com.smartroute.smartroute1.basetest.TestData;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Friendship;
import com.smartroute.smartroute1.entity.enums.FriendshipStatus;
import com.smartroute.smartroute1.endpoint.dto.FriendRequestDto;
import com.smartroute.smartroute1.repository.FriendshipRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.transaction.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "generateData"})
@Transactional
class FriendshipEndpointTest extends BaseTest implements TestData {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    private static final String BASE = BASE_URI + "/friendship";

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL)
    void sendFriendRequest_returnsDetailDto_whenReceiverExists() throws Exception {
        // default data generator creates multiple users; pick email1 as receiver
        String receiver = "email1@smartroute.com";

        FriendRequestDto dto = new FriendRequestDto();
        dto.setReceiverEmail(receiver);

        mockMvc.perform(post(BASE + "/send-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.sender.email").value(DEFAULT_USER_EMAIL))
                .andExpect(jsonPath("$.receiver.email").value(receiver));

        // Ensure persisted
        assertFalse(friendshipRepository.findAll().isEmpty());
    }

    @Test
    void sendFriendRequest_forbidden_whenNotAuthenticated() throws Exception {
        FriendRequestDto dto = new FriendRequestDto();
        dto.setReceiverEmail("email1@smartroute.com");

        mockMvc.perform(post(BASE + "/send-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL)
    void cancelFriendRequest_allowsSenderToCancel() throws Exception {
        // Create pending friendship from DEFAULT_USER_EMAIL -> email1
        ApplicationUser sender = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        ApplicationUser receiver = userRepository.findUserByEmail("email1@smartroute.com");

        Friendship f = new Friendship();
        f.setSender(sender);
        f.setReceiver(receiver);
        f.setStatus(FriendshipStatus.PENDING);
        f = friendshipRepository.save(f);

        mockMvc.perform(delete(BASE + "/" + f.getId() + "/cancel"))
                .andExpect(status().isOk());

        assertFalse(friendshipRepository.findById(f.getId()).isPresent());
    }

    @Test
    @WithMockUser(username = "email1@smartroute.com")
    void acceptFriendRequest_changesStatusToAccepted() throws Exception {
        ApplicationUser sender = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        ApplicationUser receiver = userRepository.findUserByEmail("email1@smartroute.com");

        Friendship f = new Friendship();
        f.setSender(sender);
        f.setReceiver(receiver);
        f.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(f);

        mockMvc.perform(post(BASE + "/" + f.getId() + "/accept"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        Friendship fromDb = friendshipRepository.findById(f.getId()).orElseThrow();
        assertEquals(FriendshipStatus.ACCEPTED, fromDb.getStatus());
    }

    @Test
    @WithMockUser(username = "email1@smartroute.com")
    void rejectFriendRequest_deletesPendingFriendship() throws Exception {
        ApplicationUser sender = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        ApplicationUser receiver = userRepository.findUserByEmail("email1@smartroute.com");

        Friendship f = new Friendship();
        f.setSender(sender);
        f.setReceiver(receiver);
        f.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(f);

        mockMvc.perform(delete(BASE + "/" + f.getId() + "/reject"))
                .andExpect(status().isOk());

        assertFalse(friendshipRepository.findById(f.getId()).isPresent());
    }

    @Test
    @WithMockUser(username = DEFAULT_USER_EMAIL)
    void unfriend_deletesAcceptedFriendship() throws Exception {
        ApplicationUser u1 = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        ApplicationUser u2 = userRepository.findUserByEmail("email1@smartroute.com");

        Friendship f = new Friendship();
        f.setSender(u1);
        f.setReceiver(u2);
        f.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(f);

        mockMvc.perform(delete(BASE + "/" + f.getId() + "/unfriend"))
                .andExpect(status().isOk());

        assertFalse(friendshipRepository.findById(f.getId()).isPresent());
    }

    // ---------- NOT AUTHENTICATED (should return 403) ----------
    @Test
    void cancelFriendRequest_forbidden_whenNotAuthenticated() throws Exception {
        ApplicationUser sender = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        ApplicationUser receiver = userRepository.findUserByEmail("email1@smartroute.com");

        Friendship f = new Friendship();
        f.setSender(sender);
        f.setReceiver(receiver);
        f.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(f);

        mockMvc.perform(delete(BASE + "/" + f.getId() + "/cancel"))
                .andExpect(status().isForbidden());

        // still exists
        assertTrue(friendshipRepository.findById(f.getId()).isPresent());
    }

    @Test
    void acceptFriendRequest_forbidden_whenNotAuthenticated() throws Exception {
        ApplicationUser sender = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        ApplicationUser receiver = userRepository.findUserByEmail("email1@smartroute.com");

        Friendship f = new Friendship();
        f.setSender(sender);
        f.setReceiver(receiver);
        f.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(f);

        mockMvc.perform(post(BASE + "/" + f.getId() + "/accept"))
                .andExpect(status().isForbidden());

        Friendship fromDb = friendshipRepository.findById(f.getId()).orElseThrow();
        assertEquals(FriendshipStatus.PENDING, fromDb.getStatus());
    }

    @Test
    void rejectFriendRequest_forbidden_whenNotAuthenticated() throws Exception {
        ApplicationUser sender = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        ApplicationUser receiver = userRepository.findUserByEmail("email1@smartroute.com");

        Friendship f = new Friendship();
        f.setSender(sender);
        f.setReceiver(receiver);
        f.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(f);

        mockMvc.perform(delete(BASE + "/" + f.getId() + "/reject"))
                .andExpect(status().isForbidden());

        assertTrue(friendshipRepository.findById(f.getId()).isPresent());
    }

    @Test
    void unfriend_forbidden_whenNotAuthenticated() throws Exception {
        ApplicationUser u1 = userRepository.findUserByEmail(DEFAULT_USER_EMAIL);
        ApplicationUser u2 = userRepository.findUserByEmail("email1@smartroute.com");

        Friendship f = new Friendship();
        f.setSender(u1);
        f.setReceiver(u2);
        f.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(f);

        mockMvc.perform(delete(BASE + "/" + f.getId() + "/unfriend"))
                .andExpect(status().isForbidden());

        assertTrue(friendshipRepository.findById(f.getId()).isPresent());
    }
}
