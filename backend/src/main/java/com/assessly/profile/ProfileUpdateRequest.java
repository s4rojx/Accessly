package com.assessly.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank @Size(max = 255) String fullName,
        @Size(max = 64) String rollNumber
) { }
