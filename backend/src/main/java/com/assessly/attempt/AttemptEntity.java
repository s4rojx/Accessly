package com.assessly.attempt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attempts")
public class AttemptEntity {
    @Id
    private UUID id;
    @Column(name = "exam_id", nullable = false)
    private UUID examId;
    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "effective_end_time", nullable = false)
    private Instant effectiveEndTime;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "submitted_at")
    private Instant submittedAt;
    @Column(name = "submission_id")
    private UUID submissionId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AttemptEntity() { }

    public static AttemptEntity start(UUID examId, UUID candidateId, Instant startedAt, Instant effectiveEndTime) {
        AttemptEntity entity = new AttemptEntity();
        entity.id = UUID.randomUUID();
        entity.examId = examId;
        entity.candidateId = candidateId;
        entity.startedAt = startedAt;
        entity.effectiveEndTime = effectiveEndTime;
        entity.status = "IN_PROGRESS";
        entity.createdAt = startedAt;
        entity.updatedAt = startedAt;
        return entity;
    }

    public void complete(String finalStatus, Instant submittedAt, UUID submissionId) {
        this.status = finalStatus;
        this.submittedAt = submittedAt;
        this.submissionId = submissionId;
        this.updatedAt = submittedAt;
    }

    public UUID getId() { return id; }
    public UUID getExamId() { return examId; }
    public UUID getCandidateId() { return candidateId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEffectiveEndTime() { return effectiveEndTime; }
    public String getStatus() { return status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public UUID getSubmissionId() { return submissionId; }
}
