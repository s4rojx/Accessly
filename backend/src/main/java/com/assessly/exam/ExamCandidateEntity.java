package com.assessly.exam;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "exam_allowed_candidates")
public class ExamCandidateEntity {
    @EmbeddedId
    private ExamCandidateId id;

    protected ExamCandidateEntity() { }

    public ExamCandidateEntity(UUID examId, UUID candidateId) { this.id = new ExamCandidateId(examId, candidateId); }
    public UUID getExamId() { return id.getExamId(); }
    public UUID getCandidateId() { return id.getCandidateId(); }
}
