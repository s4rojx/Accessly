package com.assessly.exam;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exams")
public class ExamEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(nullable = false, length = 150)
    private String subject;
    @Column(nullable = false)
    private String description;
    @Column(name = "window_start_time", nullable = false)
    private Instant windowStartTime;
    @Column(name = "window_end_time", nullable = false)
    private Instant windowEndTime;
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;
    @Column(name = "shuffle_questions", nullable = false)
    private boolean shuffleQuestions;
    @Column(name = "shuffle_options", nullable = false)
    private boolean shuffleOptions;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExamEntity() { }

    public static ExamEntity draft(String title, String subject, String description, Instant start, Instant end,
                                   int durationMinutes, boolean shuffleQuestions, boolean shuffleOptions, UUID createdBy) {
        ExamEntity entity = new ExamEntity();
        entity.id = UUID.randomUUID();
        entity.title = title;
        entity.subject = subject;
        entity.description = description;
        entity.windowStartTime = start;
        entity.windowEndTime = end;
        entity.durationMinutes = durationMinutes;
        entity.shuffleQuestions = shuffleQuestions;
        entity.shuffleOptions = shuffleOptions;
        entity.status = "DRAFT";
        entity.createdBy = createdBy;
        entity.createdAt = Instant.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void update(String title, String subject, String description, Instant start, Instant end, int durationMinutes,
                       boolean shuffleQuestions, boolean shuffleOptions) {
        this.title = title;
        this.subject = subject;
        this.description = description;
        this.windowStartTime = start;
        this.windowEndTime = end;
        this.durationMinutes = durationMinutes;
        this.shuffleQuestions = shuffleQuestions;
        this.shuffleOptions = shuffleOptions;
        this.updatedAt = Instant.now();
    }

    public void publish() { this.status = "PUBLISHED"; this.updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getSubject() { return subject; }
    public String getDescription() { return description; }
    public Instant getWindowStartTime() { return windowStartTime; }
    public Instant getWindowEndTime() { return windowEndTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public boolean isShuffleQuestions() { return shuffleQuestions; }
    public boolean isShuffleOptions() { return shuffleOptions; }
    public String getStatus() { return status; }
    public UUID getCreatedBy() { return createdBy; }
    public boolean isPublished() { return "PUBLISHED".equals(status); }
}
