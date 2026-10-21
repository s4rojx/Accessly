package com.assessly.question;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<QuestionEntity, UUID> {
    List<QuestionEntity> findAllByCreatedByAndArchivedFalseOrderByCreatedAtDesc(UUID createdBy);
    List<QuestionEntity> findAllByIdInAndArchivedFalse(List<UUID> ids);
}
