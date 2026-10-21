package com.assessly.result;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "results")
public class ResultEntity {
    @Id
    private UUID id;
    @Column(name = "attempt_id", nullable = false, unique = true)
    private UUID attemptId;
    @Column(name = "exam_id", nullable = false)
    private UUID examId;
    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;
    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
    @Column(name = "total_marks", nullable = false, precision = 9, scale = 2)
    private BigDecimal totalMarks;
    @Column(name = "maximum_marks", nullable = false, precision = 9, scale = 2)
    private BigDecimal maximumMarks;
    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal percentage;
    @Column(name = "correct_count", nullable = false)
    private int correctCount;
    @Column(name = "incorrect_count", nullable = false)
    private int incorrectCount;
    @Column(name = "unattempted_count", nullable = false)
    private int unattemptedCount;
    @Column(name = "time_taken_minutes", nullable = false)
    private int timeTakenMinutes;
    @Column(nullable = false)
    private boolean published;
    @Column(name = "graded_at", nullable = false)
    private Instant gradedAt;
    @Column(name = "published_at")
    private Instant publishedAt;

    protected ResultEntity() { }

    public static ResultEntity graded(UUID attemptId, UUID examId, UUID candidateId, Instant submittedAt,
                                      BigDecimal totalMarks, BigDecimal maximumMarks, BigDecimal percentage,
                                      int correctCount, int incorrectCount, int unattemptedCount,
                                      int timeTakenMinutes, Instant gradedAt) {
        ResultEntity entity = new ResultEntity();
        entity.id = UUID.randomUUID();
        entity.attemptId = attemptId;
        entity.examId = examId;
        entity.candidateId = candidateId;
        entity.submittedAt = submittedAt;
        entity.totalMarks = totalMarks;
        entity.maximumMarks = maximumMarks;
        entity.percentage = percentage;
        entity.correctCount = correctCount;
        entity.incorrectCount = incorrectCount;
        entity.unattemptedCount = unattemptedCount;
        entity.timeTakenMinutes = timeTakenMinutes;
        entity.gradedAt = gradedAt;
        entity.published = false;
        return entity;
    }

    public void publish(Instant at) {
        this.published = true;
        this.publishedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getAttemptId() { return attemptId; }
    public UUID getExamId() { return examId; }
    public UUID getCandidateId() { return candidateId; }
    public Instant getSubmittedAt() { return submittedAt; }
    public BigDecimal getTotalMarks() { return totalMarks; }
    public BigDecimal getMaximumMarks() { return maximumMarks; }
    public BigDecimal getPercentage() { return percentage; }
    public int getCorrectCount() { return correctCount; }
    public int getIncorrectCount() { return incorrectCount; }
    public int getUnattemptedCount() { return unattemptedCount; }
    public int getTimeTakenMinutes() { return timeTakenMinutes; }
    public boolean isPublished() { return published; }
}
