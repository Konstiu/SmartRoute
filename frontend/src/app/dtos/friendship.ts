export interface FriendInfoDto {
  firstName: string;
  lastName: string;
  email: string;
}

export type FriendshipStatus = 'PENDING' | 'ACCEPTED';

export interface FriendshipDetailDto {
  friendshipId: number;
  sender: FriendInfoDto;
  receiver: FriendInfoDto;
  status: FriendshipStatus;
}

export interface FriendRequestDto {
  receiverEmail: string;
}