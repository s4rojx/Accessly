package com.assessly.exam;

import com.assessly.question.QuestionDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ExamDtos {
    private ExamDtos() { }

    public record UpsertRequest(
            @NotBlank String title,
            @NotBlank String subject,
            @NotBlank String description,
            @NotNull Instant windowStartTime,
            @NotNull Instant windowEndTime,
            @Positive int durationMinutes,
            @NotNull List<UUID> questionIds,
            boolean shuffleQuestions,
            boolean shuffleOptions) { }

    public record RosterRequest(@NotEmpty List<UUID> candidateIds) { }

    public record AdminResponse(UUID id, String title, String subject, String description, Instant windowStartTime,
                                Instant windowEndTime, int durationMinutes, int maximumMarks,
                                List<QuestionDtos.AdminResponse> questions, String status,
                                boolean shuffleQuestions, boolean shuffleOptions, int enrolled) { }

    public record CandidateResponse(UUID id, String title, String subject, String description, Instant windowStartTime,
                                    Instant windowEndTime, int durationMinutes, int maximumMarks,
                                    List<QuestionDtos.CandidateResponse> questions, String status,
                                    boolean shuffleQuestions, boolean shuffleOptions, boolean resultPublished,
                                    String attemptStatus, boolean enrolled) { }

    public record RosterResponse(UUID examId, List<UUID> candidateIds) { }
}
