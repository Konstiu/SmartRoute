package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.KeysDto;
import com.smartroute.smartroute1.endpoint.dto.MessageDetailDto;
import com.smartroute.smartroute1.endpoint.dto.OneTimePreKeyDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Message;
import com.smartroute.smartroute1.exception.ValidationException;

import java.time.Instant;
import java.util.List;

public interface CommunicationService {

    /**
     * Uploads the public identity key for a user.
     *
     * @param email the email of the user
     * @param publicKey the public identity key to be uploaded
     * @param publicDhKey the public Diffie-Hellman key to be uploaded
     * @return the updated ApplicationUser entity
     */
    ApplicationUser uploadIdentityKey(String email, String publicKey, String publicDhKey);

    /**
     * Uploads the signed pre-key for a user.
     *
     * @param email the email of the user
     * @param publicPreKey the public pre-key to be uploaded
     * @param signature the signature of the pre-key
     */
    ApplicationUser uploadSignedPreKey(String email, String publicPreKey, String signature);

    /**
     * Uploads a list of one-time pre-keys for a user.
     *
     * @param email the email of the user
     * @param publicPreKeys the list of one-time pre-keys to be uploaded
     */
    void uploadOneTimePreKeys(String email, List<OneTimePreKeyDto> publicPreKeys);

    /**
     * Counts the number of one-time pre-keys for a user.
     *
     * @param email the email of the user
     * @return the count of one-time pre-keys
     */
    long countOneTimePreKeys(String email);

    /**
     * Retrieves the communication keys of a friend for a user.
     *
     * @param friendEmail the email of the friend
     * @param userEmail the email of the user requesting the keys
     * @return the KeysDto containing the friend's communication keys
     */
    KeysDto getKeysOfFriend(String friendEmail, String userEmail);

    /**
     * Sends an encrypted message from one user to another.
     *
     * @param senderEmail the email of the sender
     * @param messageDetailDto the details of the message to be sent
     * @return the Message entity representing the sent message
     */
    Message sendEncryptedMessage(String senderEmail, MessageDetailDto messageDetailDto) throws ValidationException;

    /**
     * Retrieves messages exchanged between a user and a friend after a specific timestamp.
     *
     * @param userEmail the email of the user
     * @param friendEmail the email of the friend
     * @param timestamp the timestamp after which messages should be retrieved
     * @return the list of Message entities exchanged after the specified timestamp
     */
    List<Message> retrieveMessagesByFriendAndTimestamp(String userEmail, String friendEmail, Instant timestamp);

}
