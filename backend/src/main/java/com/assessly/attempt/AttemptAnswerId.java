package com.assessly.attempt;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class AttemptAnswerId implements Serializable {
    private UUID attemptId;
    private UUID questionId;

    protected AttemptAnswerId() { }

    public AttemptAnswerId(UUID attemptId, UUID questionId) {
        this.attemptId = attemptId;
        this.questionId = questionId;
    }

    public UUID getAttemptId() { return attemptId; }
    public UUID getQuestionId() { return questionId; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AttemptAnswerId that)) return false;
        return attemptId.equals(that.attemptId) && questionId.equals(that.questionId);
    }

    @Override public int hashCode() { return java.util.Objects.hash(attemptId, questionId); }
}
