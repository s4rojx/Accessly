package com.assessly.attempt;

import com.assessly.question.QuestionDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AttemptDtos {
    private AttemptDtos() { }

    public record ActionRequest(
            @NotBlank String action,
            @NotNull UUID questionId,
            @NotNull @Size(max = 10) List<@PositiveOrZero Integer> displayedOptionIndices,
            @PositiveOrZero long clientSeq) { }

    public record AttemptQuestionResponse(UUID id, String type, String category, String difficulty, String prompt,
                                          List<QuestionDtos.OptionResponse> options, HeatmapState state,
                                          List<Integer> selectedOptionIndices, long clientSeq) { }

    public record AttemptResponse(UUID id, UUID examId, Instant serverTime, Instant effectiveEndTime,
                                  String status, List<AttemptQuestionResponse> questions) { }

    public record ActionResponse(UUID attemptId, UUID questionId, Instant serverTime, Instant effectiveEndTime,
                                 String action, HeatmapState state, List<Integer> selectedOptionIndices,
                                 long clientSeq) { }

    public record SubmissionReceipt(UUID submissionId, Instant acceptedAt, String status) { }

    public record ResultResponse(UUID id, UUID examId, Instant submittedAt, BigDecimal totalMarks,
                                 BigDecimal maximumMarks, BigDecimal percentage, int correct, int incorrect,
                                 int unattempted, int timeTakenMinutes, boolean published) { }

    public record PublicationResponse(UUID examId, int publishedCount, Instant publishedAt) { }
}
