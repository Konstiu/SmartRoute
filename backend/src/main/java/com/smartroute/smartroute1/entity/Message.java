package com.smartroute.smartroute1.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private ApplicationUser sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private ApplicationUser recipient;

    private String senderIdentityKey;

    private String senderIdentityDhKey;

    private String senderEphemeralKey;

    private UUID usedOneTimePreKeyId;

    @Column(columnDefinition = "TEXT")
    private String ciphertext;

    private String nonce;

    private Long messageNumber;

    private String ratchetPublicKey;

    @Column(nullable = false)
    private String senderDeviceId;

    @Column(nullable = false)
    private String recipientDeviceId;

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private Instant timestamp;

    @Column(nullable = false)
    private String conversationMessageId;


    @Override
    public String toString() {
        return "Message{"
                + "id=" + id
                + ", senderId=" + (sender != null ? sender.getId() : null)
                + ", recipientId=" + (recipient != null ? recipient.getId() : null)
                + ", senderDeviceId='" + senderDeviceId + '\''
                + ", recipientDeviceId='" + recipientDeviceId + '\''
                + ", messageNumber=" + messageNumber
                + ", conversationMessageId='" + conversationMessageId + '\''
                + ", ratchetPublicKey='"
                + (ratchetPublicKey != null ? ratchetPublicKey.substring(0, Math.min(12, ratchetPublicKey.length())) + "…" : null)
                + ", timestamp=" + timestamp + '}';
    }

}
