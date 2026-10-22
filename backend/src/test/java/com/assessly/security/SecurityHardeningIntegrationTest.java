package com.assessly.security;

import com.assessly.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityHardeningIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;

    @Test
    void csrfEndpointInitializesReadableTokenCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void mismatchedCsrfTokenIsRejectedWithSafeError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"candidate@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_PROTECTION_FAILED"))
                .andExpect(jsonPath("$.message").value("CSRF token validation failed"));
    }

}
