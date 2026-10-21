package com.assessly.attempt;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "attempt_answers")
public class AttemptAnswerEntity {
    @EmbeddedId
    private AttemptAnswerId id;
    @Column(name = "selected_option_indices", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String selectedOptionIndices;
    @Column(nullable = false, length = 32)
    private String state;
    @Column(name = "client_seq", nullable = false)
    private long clientSeq;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AttemptAnswerEntity() { }

    public AttemptAnswerEntity(java.util.UUID attemptId, java.util.UUID questionId, String selectedOptionIndices,
                               HeatmapState state, long clientSeq, Instant updatedAt) {
        this.id = new AttemptAnswerId(attemptId, questionId);
        this.selectedOptionIndices = selectedOptionIndices;
        this.state = state.name();
        this.clientSeq = clientSeq;
        this.updatedAt = updatedAt;
    }

    public void update(String selectedOptionIndices, HeatmapState state, long clientSeq, Instant updatedAt) {
        this.selectedOptionIndices = selectedOptionIndices;
        this.state = state.name();
        this.clientSeq = clientSeq;
        this.updatedAt = updatedAt;
    }

    public java.util.UUID getAttemptId() { return id.getAttemptId(); }
    public java.util.UUID getQuestionId() { return id.getQuestionId(); }
    public String getSelectedOptionIndices() { return selectedOptionIndices; }
    public HeatmapState getState() { return HeatmapState.valueOf(state); }
    public long getClientSeq() { return clientSeq; }
}
