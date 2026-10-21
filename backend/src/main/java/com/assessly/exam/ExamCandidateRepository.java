package com.assessly.exam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExamCandidateRepository extends JpaRepository<ExamCandidateEntity, ExamCandidateId> {
    List<ExamCandidateEntity> findByIdExamId(UUID examId);
    List<ExamCandidateEntity> findByIdCandidateId(UUID candidateId);
    void deleteByIdExamId(UUID examId);
    boolean existsByIdExamIdAndIdCandidateId(UUID examId, UUID candidateId);
}
