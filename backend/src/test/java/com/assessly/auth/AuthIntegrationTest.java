package com.assessly.auth;

import com.assessly.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AuthService authService;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired ObjectMapper objectMapper;

    @Test
    void signupHashesPasswordAndReturnsCandidateWithoutSecrets() throws Exception {
        String body = "{\"fullName\":\"Candidate One\",\"email\":\"candidate.one@example.com\",\"rollNumber\":\"CS-001\",\"password\":\"candidate-password\"}";
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("candidate.one@example.com"))
                .andExpect(jsonPath("$.role").value("ROLE_CANDIDATE"))
                .andExpect(jsonPath("$.password").doesNotExist());
        String passwordHash = jdbcTemplate.queryForObject("select password_hash from users where email=?", String.class, "candidate.one@example.com");
        org.assertj.core.api.Assertions.assertThat(passwordHash).startsWith("$2").doesNotContain("candidate-password");
    }

    @Test
    void duplicateEmailAndRollNumberAreRejected() throws Exception {
        signup("duplicate@example.com", "CS-DUP");
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Other\",\"email\":\"DUPLICATE@example.com\",\"rollNumber\":\"CS-OTHER\",\"password\":\"candidate-password\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Other\",\"email\":\"other@example.com\",\"rollNumber\":\"CS-DUP\",\"password\":\"candidate-password\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ROLL_NUMBER_ALREADY_REGISTERED"));
    }

    @Test
    void loginDerivesRoleAndSetsHttpOnlyRefreshCookie() throws Exception {
        signup("login@example.com", "CS-LOGIN");
        var login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"LOGIN@example.com\",\"password\":\"candidate-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.role").value("ROLE_CANDIDATE"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().httpOnly("assessly_refresh", true))
                .andExpect(cookie().path("assessly_refresh", "/api/v1/auth"))
                .andReturn();
        String accessToken = objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
        var jwt = jwtDecoder.decode(accessToken);
        org.assertj.core.api.Assertions.assertThat(jwt.getClaimAsString("role")).isEqualTo("ROLE_CANDIDATE");
        org.assertj.core.api.Assertions.assertThat(jwt.getExpiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void invalidCredentialsAndPublicAdminSignupAreRejectedSafely() throws Exception {
        signup("safe@example.com", "CS-SAFE");
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"safe@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Admin\",\"email\":\"admin@example.com\",\"password\":\"candidate-password\",\"role\":\"ROLE_ADMIN\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void refreshRotatesAndReuseRevokesTheTokenFamily() throws Exception {
        signup("refresh@example.com", "CS-REFRESH");
        var login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"refresh@example.com\",\"password\":\"candidate-password\"}"))
                .andExpect(status().isOk()).andReturn();
        Cookie original = login.getResponse().getCookie("assessly_refresh");
        var rotated = mockMvc.perform(post("/api/v1/auth/refresh").cookie(original))
                .andExpect(status().isOk()).andExpect(cookie().httpOnly("assessly_refresh", true)).andReturn();
        Cookie replacement = rotated.getResponse().getCookie("assessly_refresh");
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(original))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSE"));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where revoked_at is null", Integer.class)).isZero();
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(replacement))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSE"));
    }

    @Test
    void logoutIsIdempotentAndRevokesRefreshToken() throws Exception {
        signup("logout@example.com", "CS-LOGOUT");
        var login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"logout@example.com\",\"password\":\"candidate-password\"}"))
                .andReturn();
        Cookie refresh = login.getResponse().getCookie("assessly_refresh");
        mockMvc.perform(post("/api/v1/auth/logout").cookie(refresh)).andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("assessly_refresh", 0));
        mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSE"));
    }

    @Test
    void protectedEndpointsRequireBearerTokenAndErrorsHaveSafeShape() throws Exception {
        mockMvc.perform(get("/api/v1/private"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Authentication failed"))
                .andExpect(jsonPath("$.correlationId").isString())
                .andExpect(header().string("X-Request-ID", containsString("-")))
                .andExpect(jsonPath("$.message", not(containsString("token"))));
        mockMvc.perform(get("/api/v1/private").header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bootstrapCreatesAdminAndRefusesOverwrite() {
        authService.bootstrapAdmin("bootstrap@example.com", "Bootstrap Admin", "bootstrap-password");
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "select count(*) from users where role='ROLE_ADMIN'", Integer.class)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> authService.bootstrapAdmin(
                "other-admin@example.com", "Other Admin", "other-admin-password"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    private void signup(String email, String rollNumber) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Test Candidate\",\"email\":\"" + email + "\",\"rollNumber\":\"" + rollNumber + "\",\"password\":\"candidate-password\"}"))
                .andExpect(status().isCreated());
    }
}
