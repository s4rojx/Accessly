package com.assessly.exam;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "exam_questions")
public class ExamQuestionEntity {
    @EmbeddedId
    private ExamQuestionId id;
    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    protected ExamQuestionEntity() { }

    public ExamQuestionEntity(UUID examId, UUID questionId, int sequenceNumber) {
        this.id = new ExamQuestionId(examId, questionId);
        this.sequenceNumber = sequenceNumber;
    }

    public UUID getExamId() { return id.examId(); }
    public UUID getQuestionId() { return id.questionId(); }
    public int getSequenceNumber() { return sequenceNumber; }
}
