package com.assessly.infrastructure.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for Phase 4 Redis behaviour.
 * All values have safe defaults; override via environment variables or application.yml.
 */
@ConfigurationProperties(prefix = "assessly.redis")
public record RedisAttemptProperties(
        /** Minutes before window start to trigger scheduled pre-warming. */
        int prewarmMinutesBefore,
        /** Periodic PostgreSQL sync interval in milliseconds (Decision A). */
        long syncIntervalMs,
        /** Timer reaper polling interval in milliseconds. */
        long timerReaperIntervalMs,
        /** Grace window in milliseconds before the reaper fires after effectiveEnd. */
        long timerGraceMs
) {
    public RedisAttemptProperties {
        if (prewarmMinutesBefore <= 0) prewarmMinutesBefore = 15;
        if (syncIntervalMs <= 0) syncIntervalMs = 30_000L;
        if (timerReaperIntervalMs <= 0) timerReaperIntervalMs = 1_000L;
        if (timerGraceMs < 0) timerGraceMs = 5_000L;
    }
}
