package com.assessly.infrastructure.redis;

import com.assessly.attempt.AttemptEntity;
import com.assessly.attempt.AttemptRepository;
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
 * Periodically flushes active Redis attempt state to PostgreSQL (Decision A).
 *
 * <p>Fires every 30 seconds (configurable via {@code assessly.redis.sync-interval-ms}).
 * For each IN_PROGRESS attempt with live Redis state, calls
 * {@link AttemptService#syncRedisStateToPg(UUID, UUID)} which performs a non-destructive
 * upsert into {@code attempt_answers}.
 *
 * <p>This guarantees at most ~30 seconds of answer loss if Redis crashes completely
 * between syncs — dramatically better than the unbounded loss of a Redis-only strategy.
 * Final submission always performs a reconciliation regardless of the sync cadence.
 */
@Service
public class AttemptStateSyncService {

    private static final Logger log = LoggerFactory.getLogger(AttemptStateSyncService.class);

    private final AttemptRepository attemptRepository;
    private final ExamRepository examRepository;
    private final AttemptService attemptService;

    public AttemptStateSyncService(AttemptRepository attemptRepository,
                                    ExamRepository examRepository,
                                    AttemptService attemptService) {
        this.attemptRepository = attemptRepository;
        this.examRepository = examRepository;
        this.attemptService = attemptService;
    }

    @Scheduled(fixedRateString = "${assessly.redis.sync-interval-ms:30000}")
    public void syncActiveAttempts() {
        Instant now = Instant.now();
        // Only sync exams whose window is currently active (avoid unnecessary PG reads)
        List<ExamEntity> activeExams = examRepository.findActivePublishedExams(now);

        int totalFlushed = 0;
        for (ExamEntity exam : activeExams) {
            List<AttemptEntity> inProgress = attemptRepository.findByExamIdAndStatus(exam.getId(), "IN_PROGRESS");
            for (AttemptEntity attempt : inProgress) {
                try {
                    attemptService.syncRedisStateToPg(attempt.getCandidateId(), exam.getId());
                    totalFlushed++;
                } catch (Exception e) {
                    log.warn("Periodic sync failed for student {} exam {}: {}",
                            attempt.getCandidateId(), exam.getId(), e.getMessage());
                }
            }
        }
        if (totalFlushed > 0) {
            log.debug("Periodic sync: flushed {} active attempts to PostgreSQL", totalFlushed);
        }
    }
}
