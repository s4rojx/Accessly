package com.assessly.infrastructure.security;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class RateLimitService {
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> script;
    private final RateLimitProperties properties;

    public RateLimitService(StringRedisTemplate redis, @Qualifier("rateLimitScript") DefaultRedisScript<List> rateLimitScript,
                             RateLimitProperties properties) {
        this.redis = redis;
        this.script = rateLimitScript;
        this.properties = properties;
    }

    public void checkLogin(String remoteAddress, String email) {
        check("auth:login:" + safe(remoteAddress) + ":" + normalize(email), properties.getLoginLimit(), properties.getLoginWindowSeconds());
    }

    public void checkSignup(String remoteAddress) {
        check("auth:signup:" + safe(remoteAddress), properties.getSignupLimit(), properties.getSignupWindowSeconds());
    }

    public void checkRefresh(String remoteAddress) {
        check("auth:refresh:" + safe(remoteAddress), properties.getRefreshLimit(), properties.getRefreshWindowSeconds());
    }

    public void checkAvatarUpload(String userId) {
        check("profile:avatar:" + safe(userId), properties.getAvatarUploadLimit(), properties.getAvatarUploadWindowSeconds());
    }

    public void checkCandidate(String userId, String examId, CandidateOperation operation) {
        int limit;
        long window;
        String name;
        switch (operation) {
            case START -> { limit = properties.getCandidateStartLimit(); window = properties.getCandidateStartWindowSeconds(); name = "start"; }
            case ACTION -> { limit = properties.getCandidateActionLimit(); window = properties.getCandidateActionWindowSeconds(); name = "action"; }
            case SUBMIT -> { limit = properties.getCandidateSubmitLimit(); window = properties.getCandidateSubmitWindowSeconds(); name = "submit"; }
            default -> throw new IllegalStateException("Unsupported candidate rate-limit operation");
        }
        check("candidate:" + name + ":" + safe(userId) + ":" + safe(examId), limit, window);
    }

    private void check(String key, int limit, long windowSeconds) {
        if (!properties.isEnabled()) return;
        try {
            List<Long> result = redis.execute(script, List.of(key), Long.toString(windowSeconds * 1000));
            if (result == null || result.size() < 2 || result.get(0) == null || result.get(1) == null) {
                throw unavailable();
            }
            long count = result.get(0);
            long ttlMs = result.get(1);
            if (count > limit) throw new RateLimitExceededException(Math.max(1, (ttlMs + 999) / 1000));
        } catch (RateLimitExceededException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw unavailable();
        }
    }

    private static RuntimeException unavailable() {
        return new com.assessly.shared.ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "RATE_LIMIT_UNAVAILABLE", "Request protection is temporarily unavailable");
    }

    private static String safe(String value) { return value == null || value.isBlank() ? "unknown" : value.trim(); }
    private static String normalize(String value) { return value == null ? "unknown" : value.trim().toLowerCase(Locale.ROOT); }

    public enum CandidateOperation { START, ACTION, SUBMIT }
}
