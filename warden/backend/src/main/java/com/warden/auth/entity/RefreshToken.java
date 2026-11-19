package com.warden.auth.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One refresh token of a session "family" (V2__create_refresh_tokens.sql). Only the SHA-256 of the
 * token is stored. The user is referenced by id on purpose: the session module never needs to load
 * or modify the User aggregate through this entity.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private Long replacedById;

    @Column(name = "user_agent", length = 255, updatable = false)
    private String userAgent;

    public RefreshToken(Long userId, String tokenHash, UUID familyId, Instant createdAt, Instant expiresAt, String userAgent) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 255));
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** Rotated tokens have a successor; presenting one again means it was copied (reuse). */
    public boolean wasRotated() {
        return replacedById != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void revoke(Instant now, Long successorId) {
        if (revokedAt == null) {
            revokedAt = now;
            replacedById = successorId;
        }
    }
}
