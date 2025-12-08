import { HttpClient } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { Observable, ObservableLike } from "rxjs";
import { FriendRequestDto, FriendshipDetailDto } from "src/app/dtos/friendship";
import { Globals } from "src/global/globals";

@Injectable({ providedIn: "root" })
export class FriendshipService {
  private httpClient = inject(HttpClient);
  private globals = inject(Globals);
  private friendshipUri: string = this.globals.backendUri + '/friendship';

  /**
   * Sends a friend request to another user.
   * Endpoint: POST /api/v1/friendship/send-request
   * Body: {
   *    "receiverEmail": string,
   * }
   * @param friendRequest The friend request data transfer object containing the email of the receiver.
   * @returns An observable of the friendship detail data transfer object representing the created friendship.
   */
  sendFriendRequest(friendRequest: FriendRequestDto): Observable<FriendshipDetailDto> {
    return this.httpClient.post<FriendshipDetailDto>(`${this.friendshipUri}/send-request`, friendRequest);
  }

  /**
   * Cancels a previously sent friend request.
   * Endpoint: DELETE /api/v1/friendship/{friendshipId}/cancel
   * @param friendshipId The ID of the friendship to cancel the request for.
   * @returns An observable that completes when the friend request is successfully canceled.
   */
  cancelFriendRequest(friendshipId: number): Observable<void> {
    return this.httpClient.delete<void>(`${this.friendshipUri}/${friendshipId}/cancel`);
  }

  /**
   * Accepts a pending friend request.
   * Endpoint: POST /api/v1/friendship/{friendshipId}/accept
   * @param friendshipId The ID of the friendship to accept the request for.
   * @returns An observable of the updated friendship detail data transfer object.
   */
  acceptFriendRequest(friendshipId: number): Observable<FriendshipDetailDto> {
    return this.httpClient.post<FriendshipDetailDto>(`${this.friendshipUri}/${friendshipId}/accept`, {});
  }

  /**
   * Rejects a pending friend request.
   * Endpoint: DELETE /api/v1/friendship/{friendshipId}/reject
   * @param friendshipId The ID of the friendship to reject the request for.
   * @returns An observable that completes when the friend request is successfully rejected.
   */
  rejectFriendRequest(friendshipId: number): Observable<void> {
    return this.httpClient.delete<void>(`${this.friendshipUri}/${friendshipId}/reject`);
  }

  /**
   * Unfriends an existing friend.
   * Endpoint: DELETE /api/v1/friendship/{friendshipId}/unfriend
   * @param friendshipId The ID of the friendship to unfriend.
   * @returns An observable that completes when the user is successfully unfriended.
   */
  unfriendUser(friendshipId: number): Observable<void> {
    return this.httpClient.delete<void>(`${this.friendshipUri}/${friendshipId}/unfriend`);
  }

  /**
   * Retrieves the list of friends for the authenticated user.
   * Endpoint: GET /api/v1/friendship/friends
   * @returns An observable that emits an array of FriendshipDetailDto representing the user's friends.
   */
  getFriends(): Observable<FriendshipDetailDto[]> {
    return this.httpClient.get<FriendshipDetailDto[]>(this.friendshipUri + "/friends");
  }

  /**
   * Retrieves the list of incoming friend requests for the authenticated user.
   * Endpoint: GET /api/v1/friendship/incoming-requests
   * @returns An observable that emits an array of FriendshipDetailDto representing the incoming friend requests.
   */
  getIncomingFriendRequests(): Observable<FriendshipDetailDto[]> {
    return this.httpClient.get<FriendshipDetailDto[]>(this.friendshipUri + "/incoming-requests");
  }

  /**
   * Retrieves the list of outgoing friend requests sent by the authenticated user.
   * Endpoint: GET /api/v1/friendship/outgoing-requests
   * @returns An observable that emits an array of FriendshipDetailDto representing the outgoing friend requests.
   */
  getOutgoingFriendRequests(): Observable<FriendshipDetailDto[]> {
    return this.httpClient.get<FriendshipDetailDto[]>(this.friendshipUri + "/outgoing-requests");
  }

}