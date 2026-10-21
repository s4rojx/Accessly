package com.assessly.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 64) String rollNumber,
        @NotBlank @Size(min = 12, max = 128) String password) { }
