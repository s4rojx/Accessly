package com.assessly.profile;

import com.assessly.user.UserRepository;
import com.assessly.shared.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {
    @Mock UserRepository users;

    @Test
    void rejectsOversizedAvatarBeforeCloudinaryAccess() {
        UUID userId = UUID.randomUUID();
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", oversized);
        ProfileService service = new ProfileService(users, Optional.empty());

        assertThatThrownBy(() -> service.uploadAvatar(userId, file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Avatar must be a non-empty image up to 5 MB")
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("AVATAR_TOO_LARGE");
        verifyNoInteractions(users);
    }
}
