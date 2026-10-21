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

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuthRole role;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {}

    private UserEntity(UUID id, String email, String fullName, String rollNumber, String passwordHash, AuthRole role) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.rollNumber = rollNumber;
        this.passwordHash = passwordHash;
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

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getRollNumber() { return rollNumber; }
    public String getPasswordHash() { return passwordHash; }
    public AuthRole getRole() { return role; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
