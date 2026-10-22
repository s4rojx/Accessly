package com.assessly.security;

import com.assessly.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "assessly.rate-limit.enabled=true",
        "assessly.rate-limit.login-limit=2",
        "assessly.rate-limit.login-window-seconds=60"
})
class RateLimitIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;

    @Test
    void loginLimitReturnsRetryAfterAndIsolatedIpCanTry() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(request -> { request.setRemoteAddr("198.51.100.10"); return request; })
                            .contentType("application/json")
                            .content("{\"email\":\"missing@example.com\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> { request.setRemoteAddr("198.51.100.10"); return request; })
                        .contentType("application/json")
                        .content("{\"email\":\"missing@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> { request.setRemoteAddr("198.51.100.11"); return request; })
                        .contentType("application/json")
                        .content("{\"email\":\"missing@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }
}
