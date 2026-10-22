package com.assessly.profile;

import com.assessly.auth.UserResponse;
import com.assessly.shared.ApiException;
import com.assessly.user.UserEntity;
import com.assessly.user.UserRepository;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProfileService {
    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);
    private static final long MAX_AVATAR_BYTES = 5 * 1024 * 1024;
    private static final int MAX_AVATAR_DIMENSION = 4096;
    private static final String[] ALLOWED_TYPES = {"image/jpeg", "image/png", "image/gif"};

    private final UserRepository users;
    private final Optional<Cloudinary> cloudinary;

    public ProfileService(UserRepository users, Optional<Cloudinary> cloudinary) {
        this.users = users;
        this.cloudinary = cloudinary;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        return UserResponse.from(user(userId));
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, ProfileUpdateRequest request) {
        UserEntity user = user(userId);
        String rollNumber = request.rollNumber() == null || request.rollNumber().isBlank() ? null : request.rollNumber().trim();
        if (rollNumber != null && users.findByRollNumber(rollNumber).filter(existing -> !existing.getId().equals(userId)).isPresent()) {
            throw new ProfileConflictException("ROLL_NUMBER_ALREADY_REGISTERED", "Roll number is already registered");
        }
        user.updateProfile(request.fullName().trim(), rollNumber);
        return UserResponse.from(users.save(user));
    }

    @Transactional
    public UserResponse uploadAvatar(UUID userId, MultipartFile file) {
        validate(file);
        Cloudinary client = cloudinary.orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "AVATAR_STORAGE_UNAVAILABLE", "Avatar storage is not configured"));
        UserEntity user = user(userId);
        String oldPublicId = user.getAvatarPublicId();
        String newPublicId = "assessly/avatars/" + userId + "/" + UUID.randomUUID();
        Map<?, ?> uploaded;
        try {
            uploaded = client.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "public_id", newPublicId,
                    "resource_type", "image",
                    "overwrite", false
            ));
        } catch (IOException | RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AVATAR_UPLOAD_FAILED", "Avatar upload failed");
        }

        String url = String.valueOf(uploaded.get("secure_url"));
        String publicId = String.valueOf(uploaded.get("public_id"));
        try {
            user.setAvatar(url, publicId);
            UserResponse response = UserResponse.from(users.save(user));
            deleteQuietly(client, oldPublicId);
            return response;
        } catch (RuntimeException exception) {
            deleteQuietly(client, publicId);
            throw exception;
        }
    }

    @Transactional
    public UserResponse removeAvatar(UUID userId) {
        UserEntity user = user(userId);
        String oldPublicId = user.getAvatarPublicId();
        user.setAvatar(null, null);
        UserResponse response = UserResponse.from(users.save(user));
        deleteQuietly(cloudinary.orElse(null), oldPublicId);
        return response;
    }

    private UserEntity user(UUID userId) {
        return users.findById(userId).filter(UserEntity::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profile not found"));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_AVATAR_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AVATAR_TOO_LARGE", "Avatar must be a non-empty image up to 5 MB");
        }
        String contentType = file.getContentType();
        boolean allowed = contentType != null && java.util.Arrays.asList(ALLOWED_TYPES).contains(contentType.toLowerCase());
        if (!allowed || !hasImageContent(file)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AVATAR_INVALID", "Avatar must be a supported image type");
        }
    }

    private boolean hasImageContent(MultipartFile file) {
        try (InputStream input = file.getInputStream(); ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) return false;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_AVATAR_DIMENSION || height > MAX_AVATAR_DIMENSION) {
                    return false;
                }
                BufferedImage image = reader.read(0);
                return image != null;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            return false;
        }
    }

    private void deleteQuietly(Cloudinary client, String publicId) {
        if (client == null || publicId == null || publicId.isBlank()) return;
        try {
            client.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "image", "invalidate", true));
        } catch (IOException | RuntimeException exception) {
            log.warn("Cloudinary avatar cleanup failed for the stored asset");
        }
    }
}
