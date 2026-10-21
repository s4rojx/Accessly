package com.assessly.question;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class QuestionDtos {
    private QuestionDtos() { }

    public record OptionRequest(@NotNull Integer index, @NotBlank @Size(max = 2000) String text) { }

    public record UpsertRequest(
            @NotBlank String type,
            @NotBlank @Size(max = 100) String category,
            @NotBlank String difficulty,
            @NotBlank String prompt,
            @NotEmpty @Size(min = 2, max = 10) List<@Valid OptionRequest> options,
            @NotEmpty List<Integer> correctOptionIndices,
            @NotNull @DecimalMin("0.01") BigDecimal positiveMarks,
            @NotNull @DecimalMin("0.00") BigDecimal negativeMarks) { }

    public record OptionResponse(int index, String text) { }

    public record AdminResponse(UUID id, String type, String category, String difficulty, String prompt,
                                List<OptionResponse> options, List<Integer> correctOptionIndices,
                                BigDecimal positiveMarks, BigDecimal negativeMarks) { }

    public record CandidateResponse(UUID id, String type, String category, String difficulty, String prompt,
                                    List<OptionResponse> options) { }
}
