package com.assessly.attempt;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AttemptRepository extends JpaRepository<AttemptEntity, UUID> {
    Optional<AttemptEntity> findByExamIdAndCandidateId(UUID examId, UUID candidateId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from AttemptEntity attempt where attempt.id = :id")
    Optional<AttemptEntity> findByIdForUpdate(@Param("id") UUID id);
}
