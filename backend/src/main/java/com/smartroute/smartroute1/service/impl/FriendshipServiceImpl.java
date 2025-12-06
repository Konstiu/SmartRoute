package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.entity.Friendship;
import com.smartroute.smartroute1.exception.ConflictException;
import com.smartroute.smartroute1.exception.NotFoundException;
import com.smartroute.smartroute1.service.FriendshipService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class FriendshipServiceImpl implements FriendshipService {
    @Override
    public Friendship sendFriendRequest(String senderEmail, String receiverEmail) throws NotFoundException, ConflictException {
        return null;
    }

    @Override
    public void cancelFriendRequest(String senderEmail, Long friendshipId) throws NotFoundException, AccessDeniedException, ConflictException {

    }

    @Override
    public Friendship acceptFriendRequest(String email, Long friendshipId) throws NotFoundException, AccessDeniedException, ConflictException {
        return null;
    }

    @Override
    public void rejectFriendRequest(String email, Long friendshipId) throws NotFoundException, AccessDeniedException, ConflictException {

    }

    @Override
    public void removeFriend(String email, Long friendshipId) throws NotFoundException, AccessDeniedException, ConflictException {

    }
}
