package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Friendship;
import com.smartroute.smartroute1.entity.enums.FriendshipStatus;
import com.smartroute.smartroute1.exception.ConflictException;
import com.smartroute.smartroute1.exception.NotFoundException;
import com.smartroute.smartroute1.repository.FriendshipRepository;
import com.smartroute.smartroute1.service.FriendshipService;
import com.smartroute.smartroute1.service.UserService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;
import java.util.Optional;

@Service
public class FriendshipServiceImpl implements FriendshipService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final UserService userService;
    private final FriendshipRepository friendshipRepository;

    public FriendshipServiceImpl(UserService userService, FriendshipRepository friendshipRepository) {
        this.userService = userService;
        this.friendshipRepository = friendshipRepository;
    }


    @Override
    @Transactional
    public Friendship sendFriendRequest(String senderEmail, String receiverEmail) throws NotFoundException, ConflictException {
        LOGGER.trace("sendFriendRequest({}, {})", senderEmail, receiverEmail);
        ApplicationUser sender = userService.findApplicationUserByEmail(senderEmail);
        ApplicationUser receiver = userService.findApplicationUserByEmail(receiverEmail);
        
        // check if receiver exists
        if (receiver == null) {
            throw new NotFoundException("Receiver with email " + receiverEmail + " not found");
        }
        // check if there is already some friendship relation between sender and receiver
        Optional<Friendship> friendship = friendshipRepository.findByUsers(sender, receiver);
        
        // if there is no friendship, create a new friend request
        if (friendship.isEmpty()) {
            Friendship newFriendRequest = new Friendship();
            newFriendRequest.setSender(sender);
            newFriendRequest.setReceiver(receiver);
            newFriendRequest.setStatus(FriendshipStatus.PENDING);
            return friendshipRepository.save(newFriendRequest);
        }
        
        Friendship existingFriendship = friendship.get();

        // check if the existing friendship is already accepted
        if (existingFriendship.getStatus() == FriendshipStatus.ACCEPTED) {
            throw new ConflictException("Friendship already exists between " + senderEmail + " and " + receiverEmail);
        }
        
        // if the receiver also sent a friend request to the sender, accept it automatically
        if (
            existingFriendship.getStatus() == FriendshipStatus.PENDING
                && existingFriendship.getSender().equals(receiver)
                && existingFriendship.getReceiver().equals(sender)
        ) {
            existingFriendship.setStatus(FriendshipStatus.ACCEPTED);
            return friendshipRepository.save(existingFriendship);
        }
        
        // Otherwise, there is already a pending request from sender to receiver
        throw new ConflictException("A pending friend request already exists between " + senderEmail + " and " + receiverEmail);
    }

    @Override
    @Transactional
    public void cancelFriendRequest(String senderEmail, Long friendshipId) throws NotFoundException, AccessDeniedException, ConflictException {
        LOGGER.trace("cancelFriendRequest({}, {})", senderEmail, friendshipId);
        ApplicationUser sender = userService.findApplicationUserByEmail(senderEmail);
        Optional<Friendship> friendship = friendshipRepository.findById(friendshipId);
        
        // check if friendship exists
        if (friendship.isEmpty()) {
            throw new NotFoundException("Friendship with id " + friendshipId + " not found");
        }
        
        Friendship existingFriendship = friendship.get();
        
        // check if the sender is the one who sent the request
        if (!existingFriendship.getSender().equals(sender)) {
            throw new AccessDeniedException("User " + senderEmail + " is not the sender of the friend request");
        }
        
        // check if the friendship is in PENDING status
        if (existingFriendship.getStatus() != FriendshipStatus.PENDING) {
            throw new ConflictException("Friendship already exists between " + senderEmail + " and " + friendshipId);
        }
        
        // cancel the friend request
        friendshipRepository.delete(existingFriendship);
    }

    @Override
    @Transactional
    public Friendship acceptFriendRequest(String email, Long friendshipId) throws NotFoundException, AccessDeniedException, ConflictException {
        LOGGER.trace("acceptFriendRequest({}, {})", email, friendshipId);
        // get the existing friendship and validate
        Friendship existingFriendship = acceptRejectHelper(email, friendshipId);
        
        // accept the friend request
        existingFriendship.setStatus(FriendshipStatus.ACCEPTED);
        return friendshipRepository.save(existingFriendship);
    }

    @Override
    @Transactional
    public void rejectFriendRequest(String email, Long friendshipId) throws NotFoundException, AccessDeniedException, ConflictException {
        LOGGER.trace("rejectFriendRequest({}, {})", email, friendshipId);
        // get the existing friendship and validate
        Friendship existingFriendship = acceptRejectHelper(email, friendshipId);
        
        // reject the friend request
        friendshipRepository.delete(existingFriendship);
    }
    
    private Friendship acceptRejectHelper(String email, Long friendshipId) throws NotFoundException, AccessDeniedException, ConflictException {
        ApplicationUser receiver = userService.findApplicationUserByEmail(email);
        Optional<Friendship> friendship = friendshipRepository.findById(friendshipId);
        // check if friendship exists
        if (friendship.isEmpty()) {
            throw new NotFoundException("Friendship with id " + friendshipId + " not found");
        }

        Friendship existingFriendship = friendship.get();

        // check if the receiver is the one who received the request
        if (!existingFriendship.getReceiver().equals(receiver)) {
            throw new AccessDeniedException("User " + email + " is not the receiver of the friend request");
        }

        // check if the friendship is in PENDING status
        if (existingFriendship.getStatus() != FriendshipStatus.PENDING) {
            throw new ConflictException("Friendship is not in PENDING status");
        }

        return existingFriendship;
    }

    @Override
    @Transactional
    public void removeFriend(String email, Long friendshipId) throws NotFoundException, AccessDeniedException, ConflictException {
        LOGGER.trace("removeFriend({}, {})", email, friendshipId);
        ApplicationUser user = userService.findApplicationUserByEmail(email);
        Optional<Friendship> friendship = friendshipRepository.findById(friendshipId);
        // check if friendship exists
        if (friendship.isEmpty()) {
            throw new NotFoundException("Friendship with id " + friendshipId + " not found");
        }

        Friendship existingFriendship = friendship.get();

        // check if the user is part of this friendship
        if (!existingFriendship.getSender().equals(user) && !existingFriendship.getReceiver().equals(user)) {
            throw new AccessDeniedException("User " + email + " is not part of this friendship");
        }

        // check if the friendship is in ACCEPTED status
        if (existingFriendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new ConflictException("Friendship is not in ACCEPTED status");
        }

        // remove the friend
        friendshipRepository.delete(existingFriendship);
    }
}
