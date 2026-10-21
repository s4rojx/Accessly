package com.assessly.attempt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswerEntity, AttemptAnswerId> {
    List<AttemptAnswerEntity> findByIdAttemptId(UUID attemptId);
    Optional<AttemptAnswerEntity> findByIdAttemptIdAndIdQuestionId(UUID attemptId, UUID questionId);
}
