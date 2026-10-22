package com.assessly.profile;

import com.assessly.auth.UserResponse;
import com.assessly.infrastructure.security.RateLimitService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
    private final ProfileService profiles;
    private final RateLimitService rateLimitService;

    public ProfileController(ProfileService profiles, RateLimitService rateLimitService) {
        this.profiles = profiles;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping
    public UserResponse get(@AuthenticationPrincipal Jwt jwt) { return profiles.getProfile(userId(jwt)); }

    @PatchMapping
    public UserResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileUpdateRequest request) {
        return profiles.updateProfile(userId(jwt), request);
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> upload(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file) {
        rateLimitService.checkAvatarUpload(userId(jwt).toString());
        return ResponseEntity.ok(profiles.uploadAvatar(userId(jwt), file));
    }

    @DeleteMapping("/avatar")
    public UserResponse remove(@AuthenticationPrincipal Jwt jwt) { return profiles.removeAvatar(userId(jwt)); }

    private static UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
