package com.smartroute.smartroute1.endpoint.mapper;

import com.smartroute.smartroute1.endpoint.dto.EncryptedMessageDto;
import com.smartroute.smartroute1.endpoint.dto.MessageDetailDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Message;
import org.mapstruct.Mapper;

@Mapper
public interface MessageMapper {

    /**
     * Maps a Message entity to a MessageDetailDto.
     *
     * @param message the Message entity to be mapped
     * @return the corresponding MessageDetailDto
     */
    default MessageDetailDto entityToMessageDetailDto(Message message) {
        if  (message == null) {
            return null;
        }
        MessageDetailDto messageDetailDto = new MessageDetailDto();
        messageDetailDto.setId(message.getId());
        messageDetailDto.setSenderEmail(message.getSender().getEmail());
        messageDetailDto.setRecipientEmail(message.getRecipient().getEmail());
        messageDetailDto.setSenderIdentityKey(message.getSenderIdentityKey());
        messageDetailDto.setSenderIdentityDhKey(message.getSenderIdentityDhKey());
        messageDetailDto.setSenderEphemeralKey(message.getSenderEphemeralKey());
        messageDetailDto.setUsedOneTimePreKeyId(message.getUsedOneTimePreKeyId());

        EncryptedMessageDto encryptedMessageDto = new EncryptedMessageDto();
        encryptedMessageDto.setCiphertext(message.getCiphertext());
        encryptedMessageDto.setNonce(message.getNonce());
        encryptedMessageDto.setMessageNumber(message.getMessageNumber());
        encryptedMessageDto.setRatchetPublicKey(message.getRatchetPublicKey());

        messageDetailDto.setEncryptedMessage(encryptedMessageDto);
        messageDetailDto.setTimestamp(message.getTimestamp());

        messageDetailDto.setRecipientDeviceId(message.getRecipientDeviceId());
        messageDetailDto.setSenderDeviceId(message.getSenderDeviceId());

        return messageDetailDto;
    }

    /**
     * Maps a MessageDetailDto to a Message entity.
     *
     * @param messageDetailDto the MessageDetailDto to be mapped
     * @param sender the ApplicationUser who is the sender
     * @param recipient the ApplicationUser who is the recipient
     * @return the corresponding Message entity
     */
    default Message messageDetailDtoToEntity(MessageDetailDto messageDetailDto, ApplicationUser sender, ApplicationUser recipient) {
        if  (messageDetailDto == null) {
            return null;
        }
        Message message = new Message();
        message.setId(messageDetailDto.getId());
        message.setSender(sender);
        message.setRecipient(recipient);
        message.setSenderIdentityKey(messageDetailDto.getSenderIdentityKey());
        message.setSenderIdentityDhKey(messageDetailDto.getSenderIdentityDhKey());
        message.setSenderEphemeralKey(messageDetailDto.getSenderEphemeralKey());
        message.setUsedOneTimePreKeyId(messageDetailDto.getUsedOneTimePreKeyId());
        message.setCiphertext(messageDetailDto.getEncryptedMessage().getCiphertext());
        message.setNonce(messageDetailDto.getEncryptedMessage().getNonce());
        message.setMessageNumber(messageDetailDto.getEncryptedMessage().getMessageNumber());
        message.setRatchetPublicKey(messageDetailDto.getEncryptedMessage().getRatchetPublicKey());

        message.setSenderDeviceId(messageDetailDto.getSenderDeviceId());
        message.setRecipientDeviceId(messageDetailDto.getRecipientDeviceId());

        return message;
    }
}
