package com.assessly.auth;

import com.assessly.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "token_family_id", nullable = false)
    private UUID tokenFamilyId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected RefreshTokenEntity() {}

    private RefreshTokenEntity(UserEntity user, String tokenHash, UUID tokenFamilyId, Instant issuedAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.tokenHash = tokenHash;
        this.tokenFamilyId = tokenFamilyId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public static RefreshTokenEntity issue(UserEntity user, String tokenHash, UUID familyId, Instant issuedAt, Instant expiresAt) {
        return new RefreshTokenEntity(user, tokenHash, familyId, issuedAt, expiresAt);
    }

    public void markUsed(Instant now) { this.lastUsedAt = now; this.revokedAt = now; }
    public void revoke(Instant now) { this.revokedAt = now; }
    public boolean isExpired(Instant now) { return !expiresAt.isAfter(now); }
    public boolean isRevoked() { return revokedAt != null; }
    public UUID getId() { return id; }
    public UserEntity getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public UUID getTokenFamilyId() { return tokenFamilyId; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
}
