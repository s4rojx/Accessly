package com.assessly.attempt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface AttemptRepository extends JpaRepository<AttemptEntity, UUID> {
    Optional<AttemptEntity> findByExamIdAndCandidateId(UUID examId, UUID candidateId);
    List<AttemptEntity> findByExamIdAndStatus(UUID examId, String status);

    @Query(value = "select * from attempts where exam_id = :examId and candidate_id = :candidateId for update", nativeQuery = true)
    Optional<AttemptEntity> findByExamIdAndCandidateIdForUpdate(@Param("examId") UUID examId,
                                                                 @Param("candidateId") UUID candidateId);
}
