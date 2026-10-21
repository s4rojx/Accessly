package com.assessly.exam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamRepository extends JpaRepository<ExamEntity, UUID> {
    List<ExamEntity> findAllByCreatedByOrderByCreatedAtDesc(UUID createdBy);
    Optional<ExamEntity> findByIdAndCreatedBy(UUID id, UUID createdBy);
}
