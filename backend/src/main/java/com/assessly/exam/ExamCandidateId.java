package com.assessly.exam;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class ExamCandidateId implements Serializable {
    private UUID examId;
    private UUID candidateId;

    protected ExamCandidateId() { }

    public ExamCandidateId(UUID examId, UUID candidateId) {
        this.examId = examId;
        this.candidateId = candidateId;
    }

    public UUID getExamId() { return examId; }
    public UUID getCandidateId() { return candidateId; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExamCandidateId that)) return false;
        return examId.equals(that.examId) && candidateId.equals(that.candidateId);
    }

    @Override public int hashCode() { return 31 * examId.hashCode() + candidateId.hashCode(); }
}
