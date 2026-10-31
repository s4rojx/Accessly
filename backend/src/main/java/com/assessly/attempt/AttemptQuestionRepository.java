package com.assessly.attempt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AttemptQuestionRepository extends JpaRepository<AttemptQuestionEntity, AttemptQuestionId> {
    List<AttemptQuestionEntity> findByIdAttemptId(UUID attemptId);
    List<AttemptQuestionEntity> findByIdAttemptIdOrderBySequenceNumber(UUID attemptId);
}
