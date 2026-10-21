package com.assessly.question;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "questions")
public class QuestionEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 32)
    private String type;
    @Column(nullable = false, length = 100)
    private String category;
    @Column(nullable = false, length = 32)
    private String difficulty;
    @Column(name = "content_markdown", nullable = false)
    private String contentMarkdown;
    @Column(name = "canonical_options", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String canonicalOptions;
    @Column(name = "correct_option_indices", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String correctOptionIndices;
    @Column(name = "positive_marks", nullable = false, precision = 7, scale = 2)
    private BigDecimal positiveMarks;
    @Column(name = "negative_marks", nullable = false, precision = 7, scale = 2)
    private BigDecimal negativeMarks;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "is_archived", nullable = false)
    private boolean archived;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected QuestionEntity() { }

    public static QuestionEntity create(String type, String category, String difficulty, String contentMarkdown,
                                         String canonicalOptions, String correctOptionIndices, BigDecimal positiveMarks,
                                         BigDecimal negativeMarks, UUID createdBy) {
        QuestionEntity entity = new QuestionEntity();
        entity.id = UUID.randomUUID();
        entity.type = type;
        entity.category = category;
        entity.difficulty = difficulty;
        entity.contentMarkdown = contentMarkdown;
        entity.canonicalOptions = canonicalOptions;
        entity.correctOptionIndices = correctOptionIndices;
        entity.positiveMarks = positiveMarks;
        entity.negativeMarks = negativeMarks;
        entity.createdBy = createdBy;
        entity.archived = false;
        entity.createdAt = Instant.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void update(String type, String category, String difficulty, String contentMarkdown,
                       String canonicalOptions, String correctOptionIndices, BigDecimal positiveMarks,
                       BigDecimal negativeMarks) {
        this.type = type;
        this.category = category;
        this.difficulty = difficulty;
        this.contentMarkdown = contentMarkdown;
        this.canonicalOptions = canonicalOptions;
        this.correctOptionIndices = correctOptionIndices;
        this.positiveMarks = positiveMarks;
        this.negativeMarks = negativeMarks;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getType() { return type; }
    public String getCategory() { return category; }
    public String getDifficulty() { return difficulty; }
    public String getContentMarkdown() { return contentMarkdown; }
    public String getCanonicalOptions() { return canonicalOptions; }
    public String getCorrectOptionIndices() { return correctOptionIndices; }
    public BigDecimal getPositiveMarks() { return positiveMarks; }
    public BigDecimal getNegativeMarks() { return negativeMarks; }
    public UUID getCreatedBy() { return createdBy; }
    public boolean isArchived() { return archived; }
}
