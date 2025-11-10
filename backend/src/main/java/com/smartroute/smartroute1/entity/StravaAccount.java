package com.smartroute.smartroute1.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
public class StravaAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable=false, unique=true)
    private ApplicationUser user;

    private Long athleteId;

    @Column(length = 2000)
    private String accessToken;

    @Column(length = 2000)
    private String refreshToken;

    private Instant expiresAt;
    private String scopes;

    private Instant connectedAt;
}