package com.assessly.infrastructure.redis;

import java.util.UUID;

/**
 * Central authority for all Redis key strings used by Phase 4.
 *
 * Hash-tag discipline:
 *   attempt-scoped keys use {examId:studentId} — guarantees co-location in Redis Cluster.
 *   exam-scoped keys use {examId} — all exam-level keys share one hash slot.
 *   user-scoped keys use {userId}.
 *
 * Both attempt keys accessed by save_question_action.lua and lock_final_submit.lua
 * carry the same {examId:studentId} tag, satisfying the Redis Cluster cross-slot rule.
 */
public final class RedisKeySchema {

    private RedisKeySchema() { }

    // ── Attempt-scoped (hash tag: {examId:studentId}) ────────────────────────

    /** Attempt metadata: status, effectiveEndEpoch, startedAt, attemptId. */
    public static String attemptMeta(UUID examId, UUID studentId) {
        return "attempt:{" + examId + ":" + studentId + "}:meta";
    }

    /** Per-question answer state. Field = questionId, Value = JSON state blob. */
    public static String attemptState(UUID examId, UUID studentId) {
        return "attempt:{" + examId + ":" + studentId + "}:state";
    }

    // ── Exam-scoped (hash tag: {examId}) ─────────────────────────────────────

    /** Exam metadata: status, windowEnd, duration. */
    public static String examMeta(UUID examId) {
        return "exam:{" + examId + "}:meta";
    }

    /** Public question cache (no answer keys). Field = questionId, Value = JSON. */
    public static String examPublicQuestions(UUID examId) {
        return "exam:{" + examId + "}:questions:public";
    }

    /** Answer key for grading workers. Field = questionId, Value = JSON indices + marks. */
    public static String examAnswerKey(UUID examId) {
        return "exam:{" + examId + "}:answerkey";
    }

    /**
     * Active-timer sorted set. Score = effectiveEndEpochMs, Member = studentId.
     * Used by ExamTimerReaperService.
     */
    public static String activeTimers(UUID examId) {
        return "exam:{" + examId + "}:active_timers";
    }

    /** Distributed single-flight prewarm lock. SET NX PX 5000. */
    public static String prewarmLock(UUID examId) {
        return "lock:prewarm:{" + examId + "}";
    }

    // ── User-scoped (hash tag: {userId}) ─────────────────────────────────────

    /** Single-session enforcement. Value = sessionId. TTL matches JWT TTL. */
    public static String sessionKey(UUID userId) {
        return "sess:{" + userId + "}";
    }
}
