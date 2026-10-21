package com.assessly.exam;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamRepository extends JpaRepository<ExamEntity, UUID> {
    List<ExamEntity> findAllByCreatedByOrderByCreatedAtDesc(UUID createdBy);
    Optional<ExamEntity> findByIdAndCreatedBy(UUID id, UUID createdBy);

    /** Finds published exams whose window has not yet ended. Used by the timer reaper and prewarm scheduler. */
    @Query("SELECT e FROM ExamEntity e WHERE e.status = 'PUBLISHED' AND e.windowEndTime >= :now")
    List<ExamEntity> findActivePublishedExams(@Param("now") Instant now);

    /**
     * Finds published exams starting within the next {@code windowMinutes} minutes.
     * Used by the prewarm scheduler to pre-load Redis before the exam window opens.
     */
    @Query("SELECT e FROM ExamEntity e WHERE e.status = 'PUBLISHED' AND e.windowStartTime <= :cutoff AND e.windowEndTime >= :now")
    List<ExamEntity> findExamsNeedingPrewarm(@Param("now") Instant now, @Param("cutoff") Instant cutoff);
}
