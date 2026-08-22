package com.kryptosystems.ballastasera.models.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "revoked_tokens")
@Getter
@Setter
public class RevokedTokens {

    @Id
    @Column(name = "jti", nullable = false, updatable = false)
    private UUID jti;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "revoked_at", nullable = false, updatable = false)
    private OffsetDateTime revokedAt;
}