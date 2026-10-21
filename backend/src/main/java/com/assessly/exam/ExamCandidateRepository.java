package com.assessly.exam;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamCandidateRepository extends JpaRepository<ExamCandidateEntity, ExamCandidateId> {
    List<ExamCandidateEntity> findByIdExamId(UUID examId);
    List<ExamCandidateEntity> findByIdCandidateId(UUID candidateId);
    void deleteByIdExamId(UUID examId);
    boolean existsByIdExamIdAndIdCandidateId(UUID examId, UUID candidateId);

    @Query(value = "select * from exam_allowed_candidates where exam_id = :examId and candidate_id = :candidateId for update", nativeQuery = true)
    Optional<ExamCandidateEntity> findByExamIdAndCandidateIdForUpdate(@Param("examId") UUID examId,
                                                                       @Param("candidateId") UUID candidateId);
}
