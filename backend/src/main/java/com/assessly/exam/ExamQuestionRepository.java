package com.assessly.exam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestionEntity, ExamQuestionId> {
    List<ExamQuestionEntity> findByIdExamIdOrderBySequenceNumber(UUID examId);
    List<ExamQuestionEntity> findByIdQuestionId(UUID questionId);
    void deleteByIdExamId(UUID examId);
}
