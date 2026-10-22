package com.assessly.profile;

import com.assessly.AbstractIntegrationTest;
import com.assessly.auth.UserResponse;
import com.assessly.user.UserEntity;
import com.assessly.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;
import java.util.Base64;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;

    @DynamicPropertySource
    static void profileTestProperties(DynamicPropertyRegistry registry) {
        registry.add("assessly.rate-limit.enabled", () -> "false");
    }

    @Test
    void getProfileAuthenticatedReturnsUserProfile() throws Exception {
        String token = signupAndLogin("profile-" + UUID.randomUUID() + "@example.com", "PROFILE-1");

        mockMvc.perform(get("/api/v1/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Profile Candidate"))
                .andExpect(jsonPath("$.role").value("ROLE_CANDIDATE"))
                .andExpect(jsonPath("$.rollNumber").value("PROFILE-1"))
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());
    }

    @Test
    void updateProfileValidPayloadUpdatesDetails() throws Exception {
        String token = signupAndLogin("update-" + UUID.randomUUID() + "@example.com", "UPDATE-1");

        mockMvc.perform(patch("/api/v1/profile").header("Authorization", "Bearer " + token).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Updated Candidate\",\"rollNumber\":\"UPDATE-2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Candidate"))
                .andExpect(jsonPath("$.rollNumber").value("UPDATE-2"));
    }

    @Test
    void duplicateRollNumberReturnsConflict() throws Exception {
        signupAndLogin("owner-" + UUID.randomUUID() + "@example.com", "SHARED-1");
        String token = signupAndLogin("other-" + UUID.randomUUID() + "@example.com", "OTHER-1");

        mockMvc.perform(patch("/api/v1/profile").header("Authorization", "Bearer " + token).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Other Candidate\",\"rollNumber\":\"SHARED-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLL_NUMBER_ALREADY_REGISTERED"));
    }

    @Test
    void uploadWithoutCloudinaryReturnsSafeServiceError() throws Exception {
        String token = signupAndLogin("upload-" + UUID.randomUUID() + "@example.com", "UPLOAD-1");
        byte[] oneByOnePng = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", oneByOnePng);

        mockMvc.perform(multipart("/api/v1/profile/avatar").file(file)
                        .header("Authorization", "Bearer " + token).with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AVATAR_STORAGE_UNAVAILABLE"));
    }

    @Test
    void invalidAvatarReturnsSafeBadRequest() throws Exception {
        String token = signupAndLogin("invalid-" + UUID.randomUUID() + "@example.com", "INVALID-1");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.txt", MediaType.TEXT_PLAIN_VALUE, "not an image".getBytes());

        mockMvc.perform(multipart("/api/v1/profile/avatar").file(file)
                        .header("Authorization", "Bearer " + token).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AVATAR_INVALID"));
    }

    @Test
    void removeAvatarClearsStoredFields() throws Exception {
        String email = "remove-" + UUID.randomUUID() + "@example.com";
        String token = signupAndLogin(email, "REMOVE-1");
        UserEntity user = users.findByEmail(email).orElseThrow();
        user.setAvatar("https://res.cloudinary.com/example/image/upload/avatar", "assessly/avatars/example");
        users.saveAndFlush(user);

        mockMvc.perform(delete("/api/v1/profile/avatar").header("Authorization", "Bearer " + token).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());

        UserEntity stored = users.findById(user.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(stored.getAvatarUrl()).isNull();
        org.assertj.core.api.Assertions.assertThat(stored.getAvatarPublicId()).isNull();
    }

    @Test
    void profileEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    private String signupAndLogin(String email, String rollNumber) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Profile Candidate\",\"email\":\"" + email + "\",\"rollNumber\":\"" + rollNumber + "\",\"password\":\"candidate-password\"}"))
                .andExpect(status().isCreated());
        String response = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"candidate-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        return body.get("accessToken").asText();
    }
}
