package com.assessly.infrastructure.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Enforces single-session per user via a Redis string key.
 *
 * <p>On {@code /start}: attempts SET NX with the new sessionId. If the key already
 * holds a different sessionId, a second device is rejected with SESSION_CONFLICT.
 *
 * <p>On {@code /submit} or {@code /logout}: DEL the key to release the lock.
 */
@Service
public class SessionLockService {

    private final StringRedisTemplate redis;

    public SessionLockService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Tries to acquire the session lock for the given user.
     *
     * @param userId     the authenticated user UUID
     * @param sessionId  a unique identifier for this request/device (e.g. JWT ID or a fresh UUID)
     * @param ttlSeconds how long the lock lives (should match the JWT access-token TTL)
     * @return {@code true} if the lock was acquired or already held by the same sessionId,
     *         {@code false} if a different session already holds the lock
     */
    public boolean acquireSession(UUID userId, String sessionId, long ttlSeconds) {
        String key = RedisKeySchema.sessionKey(userId);
        // SET NX EX: succeeds only if key does not exist
        Boolean set = redis.opsForValue().setIfAbsent(key, sessionId, ttlSeconds, TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(set)) {
            return true; // lock acquired
        }
        // Key already exists — check if it belongs to the same session
        String existing = redis.opsForValue().get(key);
        return sessionId.equals(existing);
    }

    /**
     * Releases the session lock, if the key still belongs to the given sessionId.
     * No-op if the key is absent or owned by another session.
     */
    public void releaseSession(UUID userId, String sessionId) {
        String key = RedisKeySchema.sessionKey(userId);
        String existing = redis.opsForValue().get(key);
        if (sessionId.equals(existing)) {
            redis.delete(key);
        }
    }

    /**
     * Force-releases the session lock for the given user unconditionally.
     * Used during logout or final submission to ensure the lock is cleared.
     */
    public void forceReleaseSession(UUID userId) {
        redis.delete(RedisKeySchema.sessionKey(userId));
    }
}
