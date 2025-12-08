package com.smartroute.smartroute1.endpoint.dto;

import com.smartroute.smartroute1.entity.enums.FriendshipStatus;
import lombok.Data;

@Data
public class FriendshipDetailDto {
    private Long friendshipId;
    private FriendInfoDto sender;
    private FriendInfoDto receiver;
    private FriendshipStatus status;
}
