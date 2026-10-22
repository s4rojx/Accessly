package com.assessly.infrastructure.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RateLimitWebConfig implements WebMvcConfigurer {
    private final CandidateRateLimitInterceptor candidateRateLimitInterceptor;

    public RateLimitWebConfig(CandidateRateLimitInterceptor candidateRateLimitInterceptor) {
        this.candidateRateLimitInterceptor = candidateRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(candidateRateLimitInterceptor);
    }
}
