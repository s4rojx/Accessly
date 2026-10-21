package com.assessly.infrastructure.redis;

import com.assessly.exam.ExamEntity;
import com.assessly.exam.ExamQuestionEntity;
import com.assessly.exam.ExamQuestionRepository;
import com.assessly.exam.ExamRepository;
import com.assessly.question.QuestionEntity;
import com.assessly.question.QuestionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Proactively loads published-exam data into Redis before the exam window opens.
 *
 * Two activation paths:
 *   1. {@link #scheduledPrewarm()} — fires every 60 s and covers exams starting within
 *      the configured look-ahead window (default 15 min).
 *   2. {@link #prewarmExam(UUID)} — triggered manually via the admin prewarm endpoint
 *      or on a cache miss during an active exam.
 *
 * Single-flight protection: a distributed {@code SET NX PX 5000} lock on
 * {@code lock:prewarm:{examId}} ensures only one Spring instance queries PostgreSQL
 * when many concurrent requests arrive simultaneously (stampede protection).
 */
@Service
public class ExamPreWarmerService {

    private static final Logger log = LoggerFactory.getLogger(ExamPreWarmerService.class);
    private static final long PREWARM_LOCK_TTL_MS = 5_000L;
    private static final long SPIN_WAIT_MS = 25L;
    private static final int MAX_SPIN_ATTEMPTS = 200; // 200 × 25 ms = 5 s max wait
    private static final String INSTANCE_ID = UUID.randomUUID().toString();

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final QuestionRepository questionRepository;
    private final StringRedisTemplate redis;
    private final RedisAttemptProperties properties;
    private final ObjectMapper objectMapper;

    public ExamPreWarmerService(ExamRepository examRepository,
                                ExamQuestionRepository examQuestionRepository,
                                QuestionRepository questionRepository,
                                StringRedisTemplate redis,
                                RedisAttemptProperties properties,
                                ObjectMapper objectMapper) {
        this.examRepository = examRepository;
        this.examQuestionRepository = examQuestionRepository;
        this.questionRepository = questionRepository;
        this.redis = redis;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** Scheduled check: pre-warm any exam whose window opens within the configured look-ahead. */
    @Scheduled(fixedRate = 60_000)
    public void scheduledPrewarm() {
        Instant now = Instant.now();
        Instant cutoff = now.plusSeconds(properties.prewarmMinutesBefore() * 60L);
        List<ExamEntity> upcoming = examRepository.findExamsNeedingPrewarm(now, cutoff);
        for (ExamEntity exam : upcoming) {
            try {
                prewarmExam(exam.getId());
            } catch (Exception ex) {
                log.warn("Prewarm failed for exam {}: {}", exam.getId(), ex.getMessage());
            }
        }
    }

    /**
     * Prewarms a single exam.
     * On cache hit (exam meta already present), returns immediately — no PG query.
     * On cache miss, acquires the single-flight lock, queries PG once, and populates Redis.
     * Concurrent callers without the lock spin-wait until the winner populates the cache.
     */
    public void prewarmExam(UUID examId) {
        String metaKey = RedisKeySchema.examMeta(examId);

        // Fast path: already cached
        if (Boolean.TRUE.equals(redis.hasKey(metaKey))) {
            return;
        }

        String lockKey = RedisKeySchema.prewarmLock(examId);
        Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, INSTANCE_ID, PREWARM_LOCK_TTL_MS, TimeUnit.MILLISECONDS);

        if (Boolean.TRUE.equals(acquired)) {
            // Winner: load from PG and populate Redis
            try {
                loadExamIntoRedis(examId);
            } finally {
                redis.delete(lockKey);
            }
        } else {
            // Loser: spin-wait for the winner to populate the cache
            for (int i = 0; i < MAX_SPIN_ATTEMPTS; i++) {
                if (Boolean.TRUE.equals(redis.hasKey(metaKey))) return;
                try {
                    Thread.sleep(SPIN_WAIT_MS); // virtual-thread friendly in Java 21
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            log.warn("Prewarm spin-wait timed out for exam {}; Redis may still be unpopulated", examId);
        }
    }

    private void loadExamIntoRedis(UUID examId) {
        ExamEntity exam = examRepository.findById(examId).orElse(null);
        if (exam == null) {
            log.warn("Prewarm requested for unknown exam {}", examId);
            return;
        }

        List<ExamQuestionEntity> links = examQuestionRepository.findByIdExamIdOrderBySequenceNumber(examId);
        List<UUID> questionIds = links.stream().map(ExamQuestionEntity::getQuestionId).toList();
        Map<UUID, QuestionEntity> questionMap = questionRepository.findAllById(questionIds)
                .stream().collect(Collectors.toMap(QuestionEntity::getId, q -> q));

        // Compute TTL: windowEnd + 24h for public data
        long windowEndEpoch = exam.getWindowEndTime().toEpochMilli();
        long publicTtlSeconds = Duration.between(Instant.now(), exam.getWindowEndTime()).getSeconds() + 86_400L;
        long answerKeyTtlSeconds = Duration.between(Instant.now(), exam.getWindowEndTime()).getSeconds() + 604_800L; // +7d

        // Populate exam:meta
        String metaKey = RedisKeySchema.examMeta(examId);
        redis.opsForHash().put(metaKey, "status", exam.getStatus());
        redis.opsForHash().put(metaKey, "windowEndEpoch", String.valueOf(windowEndEpoch));
        redis.opsForHash().put(metaKey, "durationMinutes", String.valueOf(exam.getDurationMinutes()));
        redis.expire(metaKey, publicTtlSeconds, TimeUnit.SECONDS);

        // Populate public question cache (no answer keys)
        String pubKey = RedisKeySchema.examPublicQuestions(examId);
        String answerKeyKey = RedisKeySchema.examAnswerKey(examId);
        for (ExamQuestionEntity link : links) {
            QuestionEntity q = questionMap.get(link.getQuestionId());
            if (q == null) continue;
            String questionId = q.getId().toString();
            redis.opsForHash().put(pubKey, questionId, buildPublicQuestionJson(q));
            redis.opsForHash().put(answerKeyKey, questionId, buildAnswerKeyJson(q));
        }
        if (!links.isEmpty()) {
            redis.expire(pubKey, publicTtlSeconds, TimeUnit.SECONDS);
            redis.expire(answerKeyKey, answerKeyTtlSeconds, TimeUnit.SECONDS);
        }

        log.info("Prewarmed exam {} ({} questions) into Redis", examId, links.size());
    }

    private String buildPublicQuestionJson(QuestionEntity q) {
        try {
            // Strip correct_option_indices — only public-safe fields
            var node = objectMapper.createObjectNode();
            node.put("id", q.getId().toString());
            node.put("type", q.getType());
            node.put("category", q.getCategory());
            node.put("difficulty", q.getDifficulty());
            node.put("prompt", q.getContentMarkdown());
            node.set("options", objectMapper.readTree(q.getCanonicalOptions()));
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize public question " + q.getId(), e);
        }
    }

    private String buildAnswerKeyJson(QuestionEntity q) {
        try {
            var node = objectMapper.createObjectNode();
            node.set("correctOptionIndices", objectMapper.readTree(q.getCorrectOptionIndices()));
            node.put("positiveMarks", q.getPositiveMarks().toPlainString());
            node.put("negativeMarks", q.getNegativeMarks().toPlainString());
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize answer key for question " + q.getId(), e);
        }
    }
}
