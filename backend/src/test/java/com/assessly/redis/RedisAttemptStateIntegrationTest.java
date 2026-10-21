package com.assessly.redis;

import com.assessly.AbstractIntegrationTest;
import com.assessly.attempt.AttemptAnswerRepository;
import com.assessly.attempt.AttemptDtos;
import com.assessly.attempt.AttemptRepository;
import com.assessly.attempt.AttemptService;
import com.assessly.auth.AuthService;
import com.assessly.auth.LoginRequest;
import com.assessly.auth.SignupRequest;
import com.assessly.infrastructure.redis.AttemptStateSyncService;
import com.assessly.infrastructure.redis.ExamPreWarmerService;
import com.assessly.infrastructure.redis.ExamTimerReaperService;
import com.assessly.infrastructure.redis.RedisAttemptStateService;
import com.assessly.infrastructure.redis.RedisKeySchema;
import com.assessly.result.ResultRepository;
import com.assessly.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 Redis integration tests.
 *
 * Verifies:
 *   1. Atomic Lua answer mutations write to Redis and return correct state.
 *   2. Stale-sequence-number rejection returns IGNORED_STALE_PACKET idempotently (200 OK).
 *   3. Transparent re-hydration from PostgreSQL when Redis is flushed mid-exam.
 *   4. Periodic 30-second sync (AttemptStateSyncService) flushes Redis → PG correctly.
 *   5. SESSION_CONFLICT: second concurrent /start with a different session is rejected.
 *   6. Timer Reaper: expired attempt is force-submitted with status EXPIRED.
 *   7. Pre-warmer + secret exclusion: public cache omits correctOptionIndices; answer key includes it.
 *
 * Each test builds its own fixture via the live HTTP/service stack (zero mock data).
 */
class RedisAttemptStateIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired AttemptService attemptService;
    @Autowired AttemptRepository attemptRepository;
    @Autowired AttemptAnswerRepository answerRepository;
    @Autowired RedisAttemptStateService redisState;
    @Autowired AttemptStateSyncService syncService;
    @Autowired ExamTimerReaperService examTimerReaperService;
    @Autowired ExamPreWarmerService examPreWarmerService;
    @Autowired ResultRepository resultRepository;
    @Autowired StringRedisTemplate stringRedisTemplate;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;

    // ── 1. Atomic Lua mutation: action writes to Redis, state is immediately readable ──

    @Test
    void atomicLuaMutation_actionWritesToRedis_stateReadableBeforePgFlush() throws Exception {
        Fixture f = fixture("redis-lua-mutation");

        String attempt = mockMvc.perform(post("/api/v1/student/exams/" + f.examId() + "/start")
                        .header("Authorization", "Bearer " + f.candidateToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String questionId = json(attempt).get("questions").get(0).get("id").asText();
        UUID candidateId = f.candidateId();
        UUID examId = UUID.fromString(f.examId());

        // SAVE_AND_NEXT with seq=1
        mockMvc.perform(post("/api/v1/student/exams/" + f.examId() + "/action")
                        .header("Authorization", "Bearer " + f.candidateToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SAVE_AND_NEXT\",\"questionId\":\"" + questionId + "\",\"displayedOptionIndices\":[1],\"clientSeq\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ANSWERED"));

        // Verify state was written to Redis (before any periodic sync to PG)
        String redisStateJson = redisState.getQuestionState(examId, candidateId, UUID.fromString(questionId));
        assertThat(redisStateJson).isNotNull();
        assertThat(redisStateJson).contains("\"h\":\"ANSWERED\"");
        assertThat(redisStateJson).contains("\"seq\":1");
    }

    // ── 2. Stale sequence rejection is idempotent (200, not 409) ─────────────

    @Test
    void idempotentResend_duplicateClientSeq_returns200WithAcceptedState() throws Exception {
        Fixture f = fixture("redis-stale-idempotent");

        String attempt = mockMvc.perform(post("/api/v1/student/exams/" + f.examId() + "/start")
                        .header("Authorization", "Bearer " + f.candidateToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String questionId = json(attempt).get("questions").get(0).get("id").asText();

        // First SAVE_AND_NEXT with seq=1 — accepted
        mockMvc.perform(post("/api/v1/student/exams/" + f.examId() + "/action")
                        .header("Authorization", "Bearer " + f.candidateToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SAVE_AND_NEXT\",\"questionId\":\"" + questionId + "\",\"displayedOptionIndices\":[1],\"clientSeq\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ANSWERED"));

        // Exact resend with seq=1 — must return 200 (idempotent), not 409 STALE_ACTION
        mockMvc.perform(post("/api/v1/student/exams/" + f.examId() + "/action")
                        .header("Authorization", "Bearer " + f.candidateToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SAVE_AND_NEXT\",\"questionId\":\"" + questionId + "\",\"displayedOptionIndices\":[1],\"clientSeq\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ANSWERED"));

        // Genuinely stale (seq=0 after seq=1 accepted) — must return 409
        mockMvc.perform(post("/api/v1/student/exams/" + f.examId() + "/action")
                        .header("Authorization", "Bearer " + f.candidateToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SAVE_AND_NEXT\",\"questionId\":\"" + questionId + "\",\"displayedOptionIndices\":[0],\"clientSeq\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_ACTION"));
    }

    // ── 3. Transparent re-hydration after Redis FLUSHALL ─────────────────────

    @Test
    void transparentRehydration_afterRedisFlush_continuesAcceptingActions() throws Exception {
        Fixture f = fixture("redis-rehydration");

        String attempt = mockMvc.perform(post("/api/v1/student/exams/" + f.examId() + "/start")
                        .header("Authorization", "Bearer " + f.candidateToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String questionId = json(attempt).get("questions").get(0).get("id").asText();
        UUID candidateId = f.candidateId();
        UUID examId = UUID.fromString(f.examId());

        // Answer seq=1 (written to Redis)
        mockMvc.perform(post("/api/v1/student/exams/" + f.examId() + "/action")
                        .header("Authorization", "Bearer " + f.candidateToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SAVE_AND_NEXT\",\"questionId\":\"" + questionId + "\",\"displayedOptionIndices\":[1],\"clientSeq\":1}"))
                .andExpect(status().isOk());

        // Force-sync to PG so the answer is durable before the FLUSHALL
        syncService.syncActiveAttempts();

        // Simulate Redis crash by flushing all keys in the test container
        stringRedisTemplate.getConnectionFactory().getConnection().flushAll();

        // seq=2 should re-hydrate transparently and succeed
        mockMvc.perform(post("/api/v1/student/exams/" + f.examId() + "/action")
                        .header("Authorization", "Bearer " + f.candidateToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"MARK_REVIEW_AND_NEXT\",\"questionId\":\"" + questionId + "\",\"displayedOptionIndices\":[1],\"clientSeq\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ANSWERED_AND_MARKED_FOR_REVIEW"));

        // After re-hydration, final submission must still reconcile both answers to PG
        com.assessly.attempt.AttemptEntity attemptEntity = attemptRepository
                .findByExamIdAndCandidateId(examId, candidateId).orElseThrow();
        concludeExam(f.examId());

        mockMvc.perform(post("/api/v1/student/exams/" + f.examId() + "/submit")
                        .header("Authorization", "Bearer " + f.candidateToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        // Answer must be in PG after submission
        assertThat(answerRepository.findByIdAttemptId(attemptEntity.getId())).isNotEmpty();
    }

    // ── 4. Periodic sync flushes Redis → PG without disturbing live attempt ──

    @Test
    void periodicSync_flushesRedisStateToPg_withoutInterruptingLiveAttempt() throws Exception {
        Fixture f = fixture("redis-periodic-sync");

        String attempt = mockMvc.perform(post("/api/v1/student/exams/" + f.examId() + "/start")
                        .header("Authorization", "Bearer " + f.candidateToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String questionId = json(attempt).get("questions").get(0).get("id").asText();
        UUID candidateId = f.candidateId();
        UUID examId = UUID.fromString(f.examId());

        // Answer to Redis (not yet in PG)
        mockMvc.perform(post("/api/v1/student/exams/" + f.examId() + "/action")
                        .header("Authorization", "Bearer " + f.candidateToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SAVE_AND_NEXT\",\"questionId\":\"" + questionId + "\",\"displayedOptionIndices\":[1],\"clientSeq\":1}"))
                .andExpect(status().isOk());

        com.assessly.attempt.AttemptEntity attemptEntity = attemptRepository
                .findByExamIdAndCandidateId(examId, candidateId).orElseThrow();

        // Verify PG has no answer yet (hot path writes only to Redis)
        assertThat(answerRepository.findByIdAttemptId(attemptEntity.getId())).isEmpty();

        // Trigger periodic sync
        syncService.syncActiveAttempts();

        // Now PG must have the answer
        assertThat(answerRepository.findByIdAttemptId(attemptEntity.getId())).isNotEmpty();

        // Attempt is still IN_PROGRESS (sync must not close it)
        attemptEntity = attemptRepository.findByExamIdAndCandidateId(examId, candidateId).orElseThrow();
        assertThat(attemptEntity.getStatus()).isEqualTo("IN_PROGRESS");

        // Further actions must still succeed after sync
        mockMvc.perform(post("/api/v1/student/exams/" + f.examId() + "/action")
                        .header("Authorization", "Bearer " + f.candidateToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"CLEAR_RESPONSE\",\"questionId\":\"" + questionId + "\",\"displayedOptionIndices\":[],\"clientSeq\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NOT_ANSWERED"));
    }

    // ── 5. SESSION_CONFLICT: second /start from different session is rejected ─

    @Test
    void sessionConflict_secondStartFromDifferentSession_returns409() throws Exception {
        Fixture f = fixture("redis-session-conflict");

        // First /start — seeds sess:{candidateId} in Redis
        mockMvc.perform(post("/api/v1/student/exams/" + f.examId() + "/start")
                        .header("Authorization", "Bearer " + f.candidateToken()))
                .andExpect(status().isOk());

        // Overwrite the session key with a different session ID to simulate a second device
        String sessionKey = RedisKeySchema.sessionKey(f.candidateId());
        stringRedisTemplate.opsForValue().set(sessionKey, "different-device-session-id", 900, TimeUnit.SECONDS);

        // Manually clear the attempt row so /start treats this as a first-time start
        // (without this, /start would return the existing attempt on the resume path)
        jdbcTemplate.update(
                "DELETE FROM attempt_questions WHERE attempt_id IN (SELECT id FROM attempts WHERE exam_id = ? AND candidate_id = ?)",
                UUID.fromString(f.examId()), f.candidateId());
        jdbcTemplate.update(
                "DELETE FROM attempts WHERE exam_id = ? AND candidate_id = ?",
                UUID.fromString(f.examId()), f.candidateId());

        // Second /start with same candidate token — must see SESSION_CONFLICT (different session value)
        mockMvc.perform(post("/api/v1/student/exams/" + f.examId() + "/start")
                        .header("Authorization", "Bearer " + f.candidateToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_CONFLICT"));
    }

    // ── 6. Timer Reaper: expired attempt is force-submitted ───────────────────

    @Test
    void timerReaper_expiredAttempt_isForceSubmittedWithStatusExpired() throws Exception {
        Fixture f = fixture("redis-timer-reaper");

        // Start attempt (seeds active_timers ZSET)
        String attempt = mockMvc.perform(post("/api/v1/student/exams/" + f.examId() + "/start")
                        .header("Authorization", "Bearer " + f.candidateToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String questionId = json(attempt).get("questions").get(0).get("id").asText();
        UUID candidateId = f.candidateId();
        UUID examId = UUID.fromString(f.examId());

        // Answer a question
        mockMvc.perform(post("/api/v1/student/exams/" + f.examId() + "/action")
                        .header("Authorization", "Bearer " + f.candidateToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SAVE_AND_NEXT\",\"questionId\":\"" + questionId + "\",\"displayedOptionIndices\":[1],\"clientSeq\":1}"))
                .andExpect(status().isOk());

        // Flush answers to PG so grading has data
        syncService.syncActiveAttempts();

        // Manipulate the ZSET score so the reaper sees the attempt as expired:
        // Set score to now - 15 seconds (well past the grace window)
        long expiredScore = Instant.now().toEpochMilli() - 15_000L;
        stringRedisTemplate.opsForZSet().add(
                RedisKeySchema.activeTimers(examId),
                candidateId.toString(),
                expiredScore);

        // Fire the reaper
        examTimerReaperService.reapExpiredAttempts();

        // Verify: attempt status is EXPIRED
        com.assessly.attempt.AttemptEntity attemptEntity = attemptRepository
                .findByExamIdAndCandidateId(examId, candidateId).orElseThrow();
        assertThat(attemptEntity.getStatus()).isEqualTo("EXPIRED");

        // Verify: answers were reconciled to PG
        assertThat(answerRepository.findByIdAttemptId(attemptEntity.getId())).isNotEmpty();

        // Verify: result was graded
        assertThat(resultRepository.findByAttemptId(attemptEntity.getId())).isPresent();
    }

    // ── 7. Pre-warmer + secret exclusion ─────────────────────────────────────

    @Test
    void prewarm_publicCacheOmitsCorrectOptionIndices_answerKeyIncludesIt() throws Exception {
        Fixture f = fixture("redis-prewarm-secret");
        UUID examId = UUID.fromString(f.examId());

        // Trigger prewarm via admin endpoint
        mockMvc.perform(post("/api/v1/admin/exams/" + f.examId() + "/prewarm")
                        .header("Authorization", "Bearer " + f.adminToken()))
                .andExpect(status().isNoContent());

        // Verify public question cache is populated
        Map<Object, Object> publicEntries = stringRedisTemplate.opsForHash()
                .entries(RedisKeySchema.examPublicQuestions(examId));
        assertThat(publicEntries).isNotEmpty();

        // CRITICAL: public cache must NOT contain correctOptionIndices
        for (Object value : publicEntries.values()) {
            assertThat(value.toString()).doesNotContain("correctOptionIndices");
        }

        // Verify answer key cache is populated
        Map<Object, Object> answerKeyEntries = stringRedisTemplate.opsForHash()
                .entries(RedisKeySchema.examAnswerKey(examId));
        assertThat(answerKeyEntries).isNotEmpty();

        // Answer key MUST contain correctOptionIndices (for server-side grading)
        for (Object value : answerKeyEntries.values()) {
            assertThat(value.toString()).contains("correctOptionIndices");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private com.fasterxml.jackson.databind.JsonNode json(String body) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
    }

    private void concludeExam(String examId) {
        Instant end = Instant.now().minusSeconds(1);
        Instant start = end.minus(61, ChronoUnit.MINUTES);
        jdbcTemplate.update("update exams set window_start_time = ?, window_end_time = ? where id = ?",
                java.sql.Timestamp.from(start), java.sql.Timestamp.from(end), UUID.fromString(examId));
    }

    private Fixture fixture(String suffix) throws Exception {
        String adminEmail = "admin-phase4-redis@example.com";
        if (!userRepository.existsByRole(com.assessly.user.AuthRole.ROLE_ADMIN)) {
            authService.bootstrapAdmin(adminEmail, "Phase Four Admin", "admin-password-123");
        }
        String adminToken = authService.login(new LoginRequest(adminEmail, "admin-password-123")).response().accessToken();
        var candidate = authService.signup(new SignupRequest("Phase Four Candidate",
                "candidate-" + suffix + "@example.com", "P4-" + suffix, "candidate-password-123"));
        String questionBody = "{\"type\":\"MCQ_SINGLE\",\"category\":\"Redis\",\"difficulty\":\"MEDIUM\",\"prompt\":\"What is Redis?\","
                + "\"options\":[{\"index\":0,\"text\":\"A database\"},{\"index\":1,\"text\":\"A cache\"}],"
                + "\"correctOptionIndices\":[1],\"positiveMarks\":2.0,\"negativeMarks\":0.5}";
        String question = mockMvc.perform(post("/api/v1/admin/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(questionBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String questionId = json(question).get("id").asText();
        String start = Instant.now().minus(10, ChronoUnit.MINUTES).toString();
        String end = Instant.now().plus(2, ChronoUnit.HOURS).toString();
        String exam = mockMvc.perform(post("/api/v1/admin/exams")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Phase 4 Redis Test\",\"subject\":\"Redis\",\"description\":\"Redis state engine test\","
                                + "\"windowStartTime\":\"" + start + "\",\"windowEndTime\":\"" + end + "\","
                                + "\"durationMinutes\":60,\"questionIds\":[\"" + questionId + "\"],\"shuffleQuestions\":false,\"shuffleOptions\":false}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String examId = json(exam).get("id").asText();
        mockMvc.perform(put("/api/v1/admin/exams/" + examId + "/roster")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateIds\":[\"" + candidate.id() + "\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/exams/" + examId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        String candidateToken = authService.login(new LoginRequest(
                "candidate-" + suffix + "@example.com", "candidate-password-123")).response().accessToken();
        return new Fixture(adminToken, candidateToken, examId, candidate.id());
    }

    private record Fixture(String adminToken, String candidateToken, String examId, UUID candidateId) { }
}
