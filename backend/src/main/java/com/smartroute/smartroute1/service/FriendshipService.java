package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.entity.Friendship;
import com.smartroute.smartroute1.exception.ConflictException;
import com.smartroute.smartroute1.exception.NotFoundException;
import org.springframework.security.access.AccessDeniedException;

public interface FriendshipService {

    /**
     * Sends a friend request from one user to another.
     * Creates a new friendship with status PENDING.
     * If the sender already receives a pending request from the receiver, the friendship is automatically accepted.
     *
     * @param senderEmail the email of the user sending the request
     * @param receiverEmail the email of the user receiving the request
     * @return the created Friendship entity
     * @throws NotFoundException if either user does not exist
     * @throws ConflictException if an accepted friendship already exists between these users
     */
    Friendship sendFriendRequest(String senderEmail, String receiverEmail) throws NotFoundException, ConflictException;

    /**
     * Cancels a pending friend request.
     * Removes the friendship entity from the database.
     *
     * @param senderEmail the email of the user cancelling the request (must be the sender)
     * @param friendshipId the ID of the friendship to cancel
     * @throws NotFoundException if the friendship does not exist
     * @throws AccessDeniedException if the user is not the sender of the request
     * @throws ConflictException if the friendship is not in PENDING status
     */
    void cancelFriendRequest(String senderEmail, Long friendshipId) throws NotFoundException, AccessDeniedException, ConflictException;

    /**
     * Accepts a pending friend request.
     * Changes the friendship status from PENDING to ACCEPTED.
     *
     * @param email the email of the user accepting the request (must be the receiver)
     * @param friendshipId the ID of the friendship to accept
     * @return the updated Friendship entity
     * @throws NotFoundException if the friendship does not exist
     * @throws AccessDeniedException if the user is not the receiver of the request
     * @throws ConflictException if the friendship is not in PENDING status
     */
    Friendship acceptFriendRequest(String email, Long friendshipId) throws NotFoundException, AccessDeniedException, ConflictException;

    /**
     * Rejects a pending friend request.
     * Changes the friendship status to REJECTED or removes it entirely.
     *
     * @param email the email of the user rejecting the request (must be the receiver)
     * @param friendshipId the ID of the friendship to reject
     * @throws NotFoundException if the friendship does not exist
     * @throws AccessDeniedException if the user is not the receiver of the request
     * @throws ConflictException if the friendship is not in PENDING status
     */
    void rejectFriendRequest(String email, Long friendshipId) throws NotFoundException, AccessDeniedException, ConflictException;

    /**
     * Removes an existing friendship.
     * Deletes the friendship entity from the database.
     *
     * @param email the email of the user removing the friend (must be sender or receiver)
     * @param friendshipId the ID of the friendship to remove
     * @throws NotFoundException if the friendship does not exist
     * @throws AccessDeniedException if the user is not part of this friendship
     * @throws ConflictException if the friendship is not in ACCEPTED status
     */
    void removeFriend(String email, Long friendshipId) throws NotFoundException, AccessDeniedException, ConflictException;
}
