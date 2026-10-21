package com.assessly.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Primary façade for all Redis attempt-state operations in Phase 4.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Seeding attempt state into Redis when a candidate starts or resumes an exam.</li>
 *   <li>Executing the {@code save_question_action.lua} Lua script for every answer action.</li>
 *   <li>Executing the {@code lock_final_submit.lua} Lua script for final submission.</li>
 *   <li>Registering and removing entries in the per-exam active-timer sorted set.</li>
 *   <li>Providing the transparent re-hydration entry point used when Redis loses state.</li>
 * </ul>
 *
 * <p>All Redis keys follow the hash-tag schema defined in {@link RedisKeySchema}.
 */
@Service
public class RedisAttemptStateService {

    private static final Logger log = LoggerFactory.getLogger(RedisAttemptStateService.class);

    /** Redis error reply from save_question_action.lua when the meta key is absent. */
    public static final String ERR_ATTEMPT_NOT_FOUND = "ATTEMPT_NOT_FOUND";
    /** Redis error reply from save_question_action.lua when status ≠ IN_PROGRESS. */
    public static final String ERR_ATTEMPT_ALREADY_SUBMITTED = "ATTEMPT_ALREADY_SUBMITTED";
    /** Redis error reply from save_question_action.lua when the server timer has expired. */
    public static final String ERR_EXAM_TIME_EXPIRED = "EXAM_TIME_EXPIRED";
    /** Redis error reply from lock_final_submit.lua when attempt is already closed. */
    public static final String ERR_ALREADY_SUBMITTED = "ALREADY_SUBMITTED";
    /** Returned by save_question_action.lua for out-of-order packets. */
    public static final String IGNORED_STALE_PACKET = "IGNORED_STALE_PACKET";
    /** Returned by save_question_action.lua on success. */
    public static final String OK = "OK";

    /** TTL padding beyond the exam window end (48 hours). */
    private static final long ATTEMPT_TTL_PADDING_SECONDS = 48L * 3600L;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<String> saveQuestionActionScript;
    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript<List> lockFinalSubmitScript;
    private final ObjectMapper objectMapper;

    @SuppressWarnings("rawtypes")
    public RedisAttemptStateService(StringRedisTemplate redis,
                                    DefaultRedisScript<String> saveQuestionActionScript,
                                    DefaultRedisScript<List> lockFinalSubmitScript,
                                    ObjectMapper objectMapper) {
        this.redis = redis;
        this.saveQuestionActionScript = saveQuestionActionScript;
        this.lockFinalSubmitScript = lockFinalSubmitScript;
        this.objectMapper = objectMapper;
    }

    // ── Attempt Seeding ───────────────────────────────────────────────────────

    /**
     * Seeds the attempt metadata and initial (empty) state hashes into Redis.
     * Called during {@code /start} after the attempt row is committed to PostgreSQL.
     * Also registers the student in the exam's active-timer ZSET.
     *
     * @param attemptId      the PostgreSQL attempt UUID
     * @param examId         the exam UUID
     * @param studentId      the candidate UUID
     * @param effectiveEnd   server-authoritative deadline
     * @param startedAt      when the attempt was started
     * @param windowEnd      exam window end (used to compute TTL)
     */
    public void seedAttempt(UUID attemptId, UUID examId, UUID studentId,
                             Instant effectiveEnd, Instant startedAt, Instant windowEnd) {
        String metaKey = RedisKeySchema.attemptMeta(examId, studentId);
        String stateKey = RedisKeySchema.attemptState(examId, studentId);
        String timersKey = RedisKeySchema.activeTimers(examId);

        long effectiveEndEpoch = effectiveEnd.toEpochMilli();
        long ttlSeconds = windowEnd.getEpochSecond() + ATTEMPT_TTL_PADDING_SECONDS - Instant.now().getEpochSecond();
        if (ttlSeconds < 3600) ttlSeconds = 3600; // floor at 1 hour for safety

        redis.opsForHash().put(metaKey, "attemptId", attemptId.toString());
        redis.opsForHash().put(metaKey, "status", "IN_PROGRESS");
        redis.opsForHash().put(metaKey, "startedAt", String.valueOf(startedAt.toEpochMilli()));
        redis.opsForHash().put(metaKey, "effectiveEndEpoch", String.valueOf(effectiveEndEpoch));
        redis.expire(metaKey, ttlSeconds, TimeUnit.SECONDS);

        // Ensure state hash exists (even if empty) so the Lua script can HGET it safely
        redis.opsForHash().put(stateKey, "_init", "1");
        redis.expire(stateKey, ttlSeconds, TimeUnit.SECONDS);

        // Register in active-timer ZSET; score = effectiveEndEpochMs
        redis.opsForZSet().add(timersKey, studentId.toString(), effectiveEndEpoch);
        redis.expire(timersKey, ttlSeconds, TimeUnit.SECONDS);

        log.debug("Seeded Redis attempt for student {} in exam {}", studentId, examId);
    }

    /**
     * Re-hydrates attempt state into Redis from previously flushed PostgreSQL data.
     * Called when {@code save_question_action.lua} returns {@code ATTEMPT_NOT_FOUND},
     * indicating Redis lost the attempt (eviction or restart).
     *
     * @param attemptId    the PostgreSQL attempt UUID
     * @param examId       the exam UUID
     * @param studentId    the candidate UUID
     * @param effectiveEnd server-authoritative deadline
     * @param startedAt    when the attempt was started
     * @param windowEnd    exam window end
     * @param savedAnswers map of questionId → serialized state JSON (from PG answers)
     */
    public void rehydrateAttempt(UUID attemptId, UUID examId, UUID studentId,
                                  Instant effectiveEnd, Instant startedAt, Instant windowEnd,
                                  Map<UUID, String> savedAnswers) {
        seedAttempt(attemptId, examId, studentId, effectiveEnd, startedAt, windowEnd);

        if (!savedAnswers.isEmpty()) {
            String stateKey = RedisKeySchema.attemptState(examId, studentId);
            savedAnswers.forEach((questionId, stateJson) ->
                    redis.opsForHash().put(stateKey, questionId.toString(), stateJson));
            log.debug("Re-hydrated {} answers for student {} in exam {}", savedAnswers.size(), studentId, examId);
        }
    }

    // ── Write Path: Question Action ───────────────────────────────────────────

    /**
     * Executes {@code save_question_action.lua} atomically.
     *
     * @param examId        exam UUID
     * @param studentId     candidate UUID
     * @param questionId    question UUID being acted on
     * @param clientSeq     monotonically increasing sequence number from client
     * @param heatmapState  new heatmap state string (e.g. "ANSWERED")
     * @param selectedJson  JSON array of selected canonical option indices
     * @param timeSpentMs   time spent on this question in ms (from client, best-effort)
     * @return              one of: OK, IGNORED_STALE_PACKET, ERR_* constants
     */
    public String saveAction(UUID examId, UUID studentId, UUID questionId,
                              long clientSeq, String heatmapState, String selectedJson, long timeSpentMs) {
        String newStateJson = buildStateJson(heatmapState, selectedJson, timeSpentMs, clientSeq);
        try {
            String result = redis.execute(saveQuestionActionScript,
                    List.of(RedisKeySchema.attemptMeta(examId, studentId),
                            RedisKeySchema.attemptState(examId, studentId)),
                    examId.toString(),
                    questionId.toString(),
                    String.valueOf(clientSeq),
                    newStateJson);
            return result != null ? result : ERR_ATTEMPT_NOT_FOUND;
        } catch (org.springframework.data.redis.RedisSystemException ex) {
            // Redis Lua error replies surface as RedisSystemException wrapping a message
            String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            if (msg != null && msg.contains(ERR_ATTEMPT_NOT_FOUND)) return ERR_ATTEMPT_NOT_FOUND;
            if (msg != null && msg.contains(ERR_ATTEMPT_ALREADY_SUBMITTED)) return ERR_ATTEMPT_ALREADY_SUBMITTED;
            if (msg != null && msg.contains(ERR_EXAM_TIME_EXPIRED)) return ERR_EXAM_TIME_EXPIRED;
            throw ex;
        }
    }

    // ── Write Path: Final Submission ──────────────────────────────────────────

    /**
     * Executes {@code lock_final_submit.lua} to atomically flip attempt status
     * and harvest all accumulated answer state.
     *
     * @param examId           exam UUID
     * @param studentId        candidate UUID
     * @param submissionSource 'USER_CLICK' or 'TIMER_REAPER'
     * @return map of questionId (UUID) → state JSON, or {@code null} if already submitted
     */
    public Map<UUID, String> lockAndSubmit(UUID examId, UUID studentId, String submissionSource) {
        long nowMs = Instant.now().toEpochMilli();
        try {
            Object result = redis.execute(lockFinalSubmitScript,
                    List.of(RedisKeySchema.attemptMeta(examId, studentId),
                            RedisKeySchema.attemptState(examId, studentId)),
                    String.valueOf(nowMs),
                    submissionSource);
            return parseHgetallResult(result);
        } catch (org.springframework.data.redis.RedisSystemException ex) {
            String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            if (msg != null && msg.contains(ERR_ALREADY_SUBMITTED)) return null;
            throw ex;
        }
    }

    // ── Timer Registration ────────────────────────────────────────────────────

    /** Removes a student from the active-timer ZSET after submission. */
    public void removeFromActiveTimers(UUID examId, UUID studentId) {
        redis.opsForZSet().remove(RedisKeySchema.activeTimers(examId), studentId.toString());
    }

    /**
     * Returns all student IDs whose timer has expired (score ≤ thresholdMs).
     * Used by ExamTimerReaperService.
     */
    public List<String> getExpiredTimerStudents(UUID examId, long thresholdMs) {
        var result = redis.opsForZSet().rangeByScore(
                RedisKeySchema.activeTimers(examId), 0, thresholdMs);
        return result == null ? List.of() : new ArrayList<>(result);
    }

    // ── Exam Public Question Cache (cache-aside read) ─────────────────────────

    /**
     * Returns all entries from the public question cache for an exam.
     * Field keys are question UUID strings; values are public-safe question JSON
     * (no {@code correctOptionIndices}). Used for cache-aside reads in
     * {@code AttemptService.toResponse()}. Returns an empty map on cache miss.
     */
    public Map<Object, Object> getExamPublicQuestions(UUID examId) {
        return redis.opsForHash().entries(RedisKeySchema.examPublicQuestions(examId));
    }

    // ── State Snapshot (for periodic PG sync) ────────────────────────────────

    /**
     * Returns all current question state entries for an active attempt.
     * Used by AttemptStateSyncService for the 30-second periodic flush.
     */
    public Map<UUID, String> readCurrentState(UUID examId, UUID studentId) {
        Map<Object, Object> raw = redis.opsForHash()
                .entries(RedisKeySchema.attemptState(examId, studentId));
        Map<UUID, String> result = new HashMap<>();
        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            String field = entry.getKey().toString();
            if ("_init".equals(field)) continue; // skip sentinel
            try {
                result.put(UUID.fromString(field), entry.getValue().toString());
            } catch (IllegalArgumentException ignored) { }
        }
        return result;
    }

    /** Checks whether attempt meta exists in Redis (i.e. attempt is live in Redis). */
    public boolean attemptExists(UUID examId, UUID studentId) {
        return Boolean.TRUE.equals(redis.hasKey(RedisKeySchema.attemptMeta(examId, studentId)));
    }

    /**
     * Reads the current state JSON for a single question from the attempt state hash.
     * Used to construct an idempotent ActionResponse when IGNORED_STALE_PACKET is returned
     * and the answer has not yet been flushed to PostgreSQL.
     *
     * @return the state JSON string, or {@code null} if not present in Redis.
     */
    public String getQuestionState(UUID examId, UUID studentId, UUID questionId) {
        Object raw = redis.opsForHash().get(
                RedisKeySchema.attemptState(examId, studentId), questionId.toString());
        return raw != null ? raw.toString() : null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds the compact JSON state blob stored as a hash field value.
     * Format: {"h":"ANSWERED","c":[1],"t":14500,"seq":3}
     */
    private String buildStateJson(String heatmapState, String selectedJson, long timeSpentMs, long seq) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("h", heatmapState);
            node.set("c", objectMapper.readTree(selectedJson));
            node.put("t", timeSpentMs);
            node.put("seq", seq);
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Redis state JSON", e);
        }
    }

    /**
     * Parses the flat HGETALL list returned by lock_final_submit.lua into a map.
     * Redis returns: [key1, val1, key2, val2, ...]
     */
    @SuppressWarnings("unchecked")
    private Map<UUID, String> parseHgetallResult(Object result) {
        if (result == null) return Map.of();
        Map<UUID, String> map = new HashMap<>();
        if (result instanceof List<?> list) {
            for (int i = 0; i + 1 < list.size(); i += 2) {
                String field = list.get(i).toString();
                String value = list.get(i + 1).toString();
                if ("_init".equals(field)) continue;
                try {
                    map.put(UUID.fromString(field), value);
                } catch (IllegalArgumentException ignored) { }
            }
        }
        return map;
    }
}
