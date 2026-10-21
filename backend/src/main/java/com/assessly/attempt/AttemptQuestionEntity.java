package com.assessly.attempt;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "attempt_questions")
public class AttemptQuestionEntity {
    @EmbeddedId
    private AttemptQuestionId id;
    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;
    @Column(name = "displayed_option_indices", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String displayedOptionIndices;

    protected AttemptQuestionEntity() { }

    public AttemptQuestionEntity(java.util.UUID attemptId, java.util.UUID questionId, int sequenceNumber,
                                 String displayedOptionIndices) {
        this.id = new AttemptQuestionId(attemptId, questionId);
        this.sequenceNumber = sequenceNumber;
        this.displayedOptionIndices = displayedOptionIndices;
    }

    public java.util.UUID getAttemptId() { return id.getAttemptId(); }
    public java.util.UUID getQuestionId() { return id.getQuestionId(); }
    public int getSequenceNumber() { return sequenceNumber; }
    public String getDisplayedOptionIndices() { return displayedOptionIndices; }
}
