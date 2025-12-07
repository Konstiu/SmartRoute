package com.smartroute.smartroute1.endpoint.mapper;

import com.smartroute.smartroute1.endpoint.dto.FriendInfoDto;
import com.smartroute.smartroute1.endpoint.dto.FriendshipDetailDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Friendship;
import org.mapstruct.Mapper;

@Mapper
public interface FriendshipMapper {

    default FriendshipDetailDto entityToDto(Friendship friendship) {
        FriendshipDetailDto dto = new FriendshipDetailDto();
        if (friendship == null) {
            return null;
        }
        dto.setFriendshipId(friendship.getId());
        dto.setSender(
            userToFriendInfoDto(friendship.getSender())
        );
        dto.setReceiver(
            userToFriendInfoDto(friendship.getReceiver())
        );
        dto.setStatus(friendship.getStatus());
        return dto;
    }

    default FriendInfoDto userToFriendInfoDto(ApplicationUser user) {
        FriendInfoDto dto = new FriendInfoDto();
        if (user == null) {
            return null;
        }
        dto.setFirstName(user.getFirstname());
        dto.setLastName(user.getLastname());
        dto.setEmail(user.getEmail());
        return dto;
    }

}
