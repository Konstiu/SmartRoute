package com.smartroute.smartroute1.endpoint;

import com.smartroute.smartroute1.endpoint.dto.FriendRequestDto;
import com.smartroute.smartroute1.endpoint.dto.FriendshipDetailDto;
import com.smartroute.smartroute1.endpoint.mapper.FriendshipMapper;
import com.smartroute.smartroute1.entity.Friendship;
import com.smartroute.smartroute1.exception.ConflictException;
import com.smartroute.smartroute1.service.FriendshipService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.invoke.MethodHandles;
import java.util.List;

@RestController
@RequestMapping("/api/v1/friendship")
@RequiredArgsConstructor
public class FriendshipEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final FriendshipService friendshipService;
    private final FriendshipMapper mapper;

    @PostMapping("/send-request")
    @Secured("ROLE_USER")
    @Operation(summary = "Send friend request", description = "Send a friend request from the authenticated user to the specified receiver email.")
    public FriendshipDetailDto sendFriendRequest(@RequestBody FriendRequestDto friendRequestDto) throws ConflictException {
        LOGGER.info("POST /api/v1/friendship/send-request");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Friendship friendship = friendshipService.sendFriendRequest(authentication.getName(), friendRequestDto.getReceiverEmail());
        return mapper.entityToDto(friendship);
    }

    @DeleteMapping("/{friendshipId}/cancel")
    @Secured("ROLE_USER")
    @Operation(summary = "Cancel friend request", description = "Cancel an outgoing pending friend request belonging to the authenticated user.")
    public void cancelFriendRequest(@PathVariable("friendshipId") Long friendshipId) throws ConflictException {
        LOGGER.info("DELETE /api/v1/friendship/{friendshipId}/cancel");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        friendshipService.cancelFriendRequest(authentication.getName(), friendshipId);
    }

    @PostMapping("/{friendshipId}/accept")
    @Secured("ROLE_USER")
    @Operation(summary = "Accept friend request", description = "Accept an incoming pending friend request for the authenticated user and return the updated friendship.")
    public FriendshipDetailDto acceptFriendRequest(@PathVariable("friendshipId") Long friendshipId) throws ConflictException {
        LOGGER.info("POST /api/v1/friendship/{friendshipId}/accept");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Friendship friendship = friendshipService.acceptFriendRequest(authentication.getName(), friendshipId);
        return mapper.entityToDto(friendship);
    }

    @DeleteMapping("/{friendshipId}/reject")
    @Secured("ROLE_USER")
    @Operation(summary = "Reject friend request", description = "Reject an incoming pending friend request for the authenticated user.")
    public void rejectFriendRequest(@PathVariable("friendshipId") Long friendshipId) throws ConflictException {
        LOGGER.info("DELETE /api/v1/friendship/{friendshipId}");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        friendshipService.rejectFriendRequest(authentication.getName(), friendshipId);
    }

    @DeleteMapping("/{friendshipId}/unfriend")
    @Secured("ROLE_USER")
    @Operation(summary = "Remove friend", description = "Remove an existing accepted friendship for the authenticated user.")
    public void unfriend(@PathVariable("friendshipId") Long friendshipId) throws ConflictException {
        LOGGER.info("DELETE /api/v1/friendship/{friendshipId}/unfriend");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        friendshipService.removeFriend(authentication.getName(), friendshipId);
    }

    @GetMapping("/friends")
    @Secured("ROLE_USER")
    @Operation(summary = "Get friends", description = "Return all accepted friendships of the authenticated user.")
    public List<FriendshipDetailDto> getFriends() {
        LOGGER.trace("GET /api/v1/friendship");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<Friendship> friendships = friendshipService.getFriends(authentication.getName());
        return friendships.stream().map(mapper::entityToDto).toList();
    }

    @GetMapping("/incoming-requests")
    @Secured("ROLE_USER")
    @Operation(summary = "Get incoming friend requests", description = "Return pending incoming friend requests for the authenticated user.")
    public List<FriendshipDetailDto> getIncomingFriendRequests() {
        LOGGER.trace("GET /api/v1/friendship/incoming-requests");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<Friendship> friendships = friendshipService.getIncomingFriendRequests(authentication.getName());
        return friendships.stream().map(mapper::entityToDto).toList();
    }

    @GetMapping("/outgoing-requests")
    @Secured("ROLE_USER")
    @Operation(summary = "Get outgoing friend requests", description = "Return pending outgoing friend requests sent by the authenticated user.")
    public List<FriendshipDetailDto> getOutgoingFriendRequests() {
        LOGGER.trace("GET /api/v1/friendship/outgoing-requests");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<Friendship> friendships = friendshipService.getOutgoingFriendRequests(authentication.getName());
        return friendships.stream().map(mapper::entityToDto).toList();
    }
}
