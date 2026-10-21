package com.assessly.result;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResultRepository extends JpaRepository<ResultEntity, UUID> {
    Optional<ResultEntity> findByAttemptId(UUID attemptId);
    Optional<ResultEntity> findByExamIdAndCandidateId(UUID examId, UUID candidateId);
    List<ResultEntity> findByExamId(UUID examId);
}
