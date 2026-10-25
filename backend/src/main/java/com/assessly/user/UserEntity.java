package com.assessly.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "roll_number", unique = true, length = 64)
    private String rollNumber;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 32)
    private AuthProvider authProvider;

    @Column(name = "google_subject", unique = true, length = 255)
    private String googleSubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuthRole role;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "avatar_public_id", length = 255)
    private String avatarPublicId;

    protected UserEntity() {}

    private UserEntity(UUID id, String email, String fullName, String rollNumber, String passwordHash, AuthRole role) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.rollNumber = rollNumber;
        this.passwordHash = passwordHash;
        this.authProvider = AuthProvider.LOCAL;
        this.role = role;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static UserEntity candidate(String email, String fullName, String rollNumber, String passwordHash) {
        return new UserEntity(UUID.randomUUID(), email, fullName, rollNumber, passwordHash, AuthRole.ROLE_CANDIDATE);
    }

    public static UserEntity administrator(String email, String fullName, String passwordHash) {
        return new UserEntity(UUID.randomUUID(), email, fullName, null, passwordHash, AuthRole.ROLE_ADMIN);
    }

    public static UserEntity googleCandidate(String email, String fullName, String googleSubject) {
        UserEntity user = new UserEntity(UUID.randomUUID(), email, fullName, null, null, AuthRole.ROLE_CANDIDATE);
        user.authProvider = AuthProvider.GOOGLE;
        user.googleSubject = googleSubject;
        return user;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getRollNumber() { return rollNumber; }
    public String getPasswordHash() { return passwordHash; }
    public AuthProvider getAuthProvider() { return authProvider; }
    public String getGoogleSubject() { return googleSubject; }
    public AuthRole getRole() { return role; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getAvatarPublicId() { return avatarPublicId; }

    public void updateProfile(String fullName, String rollNumber) {
        this.fullName = fullName;
        this.rollNumber = rollNumber;
        this.updatedAt = Instant.now();
    }

    public void setAvatar(String avatarUrl, String avatarPublicId) {
        this.avatarUrl = avatarUrl;
        this.avatarPublicId = avatarPublicId;
        this.updatedAt = Instant.now();
    }

    public void linkGoogleIdentity(String subject) {
        this.googleSubject = subject;
        this.authProvider = this.passwordHash == null ? AuthProvider.GOOGLE : AuthProvider.BOTH;
        this.updatedAt = Instant.now();
    }
}
