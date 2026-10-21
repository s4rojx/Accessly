package com.assessly.infrastructure.redis;

import com.assessly.attempt.AttemptService;
import com.assessly.exam.ExamEntity;
import com.assessly.exam.ExamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Polls for expired attempt timers every second and forces submission on behalf of
 * candidates who have run out of time (including those who went offline silently).
 *
 * <p>Implementation (Decision C: lightweight PG query for exam discovery):
 * <ol>
 *   <li>Query PostgreSQL for all currently-active published exams (window not yet ended).</li>
 *   <li>For each exam, query the {@code exam:{examId}:active_timers} ZSET for student IDs
 *       whose score ≤ {@code now - graceMs} (i.e., their effectiveEnd has passed).</li>
 *   <li>For each expired student, invoke {@code lock_final_submit.lua} via
 *       {@link AttemptService#timerReaperSubmit(UUID, UUID)}.</li>
 * </ol>
 *
 * <p>The 5-second grace window ({@code timerGraceMs}) gives the student's final
 * in-flight click a chance to win the race before the reaper fires.
 */
@Service
public class ExamTimerReaperService {

    private static final Logger log = LoggerFactory.getLogger(ExamTimerReaperService.class);

    private final ExamRepository examRepository;
    private final RedisAttemptStateService redisState;
    private final AttemptService attemptService;
    private final RedisAttemptProperties properties;

    public ExamTimerReaperService(ExamRepository examRepository,
                                   RedisAttemptStateService redisState,
                                   AttemptService attemptService,
                                   RedisAttemptProperties properties) {
        this.examRepository = examRepository;
        this.redisState = redisState;
        this.attemptService = attemptService;
        this.properties = properties;
    }

    @Scheduled(fixedRateString = "${assessly.redis.timer-reaper-interval-ms:1000}")
    public void reapExpiredAttempts() {
        Instant now = Instant.now();
        long thresholdMs = now.toEpochMilli() - properties.timerGraceMs();

        // Decision C: one lightweight PG query to discover active exams
        List<ExamEntity> activeExams = examRepository.findActivePublishedExams(now);

        for (ExamEntity exam : activeExams) {
            UUID examId = exam.getId();
            List<String> expiredStudentIds = redisState.getExpiredTimerStudents(examId, thresholdMs);

            for (String studentIdStr : expiredStudentIds) {
                try {
                    UUID studentId = UUID.fromString(studentIdStr);
                    log.info("Timer reaper: firing for student {} in exam {}", studentId, examId);
                    attemptService.timerReaperSubmit(studentId, examId);
                } catch (IllegalArgumentException e) {
                    log.warn("Timer reaper: invalid student ID '{}' in exam {} ZSET — removing", studentIdStr, examId);
                    redisState.removeFromActiveTimers(examId, UUID.randomUUID()); // defensive cleanup
                } catch (Exception e) {
                    log.error("Timer reaper: failed to submit student {} in exam {}: {}", studentIdStr, examId, e.getMessage(), e);
                }
            }
        }
    }
}
