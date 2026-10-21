package com.assessly.exam;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record ExamQuestionId(UUID examId, UUID questionId) implements Serializable { }
