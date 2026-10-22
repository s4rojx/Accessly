package com.assessly.auth;

import com.assessly.user.AuthRole;
import com.assessly.user.UserEntity;

import java.util.UUID;

public record UserResponse(UUID id, String fullName, String email, AuthRole role, String rollNumber, String avatarUrl) {
    public static UserResponse from(UserEntity user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.getRollNumber(), user.getAvatarUrl());
    }
}
