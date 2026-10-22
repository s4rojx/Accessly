package com.assessly.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

@Component
public class CandidateRateLimitInterceptor implements HandlerInterceptor {
    private final RateLimitService rateLimitService;

    public CandidateRateLimitInterceptor(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !request.getRequestURI().contains("/api/v1/student/exams/")) {
            return true;
        }
        RateLimitService.CandidateOperation operation = operation(request.getRequestURI());
        if (operation == null) return true;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) return true;
        @SuppressWarnings("unchecked")
        Map<String, String> variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables == null || variables.get("examId") == null) return true;
        rateLimitService.checkCandidate(jwt.getToken().getSubject(), variables.get("examId"), operation);
        return true;
    }

    private static RateLimitService.CandidateOperation operation(String path) {
        if (path.endsWith("/start")) return RateLimitService.CandidateOperation.START;
        if (path.endsWith("/action")) return RateLimitService.CandidateOperation.ACTION;
        if (path.endsWith("/submit")) return RateLimitService.CandidateOperation.SUBMIT;
        return null;
    }
}
