package com.assessly.attempt;

import com.assessly.exam.ExamCandidateRepository;
import com.assessly.exam.ExamEntity;
import com.assessly.exam.ExamQuestionEntity;
import com.assessly.exam.ExamQuestionRepository;
import com.assessly.exam.ExamRepository;
import com.assessly.question.QuestionDtos;
import com.assessly.question.QuestionEntity;
import com.assessly.question.QuestionRepository;
import com.assessly.result.ResultEntity;
import com.assessly.result.ResultRepository;
import com.assessly.shared.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AttemptService {
    private static final TypeReference<List<QuestionDtos.OptionResponse>> OPTIONS = new TypeReference<>() { };
    private static final TypeReference<List<Integer>> INDICES = new TypeReference<>() { };

    private final AttemptRepository attempts;
    private final AttemptQuestionRepository attemptQuestions;
    private final AttemptAnswerRepository answers;
    private final ExamRepository exams;
    private final ExamCandidateRepository roster;
    private final ExamQuestionRepository examQuestions;
    private final QuestionRepository questions;
    private final ResultRepository results;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    public AttemptService(AttemptRepository attempts, AttemptQuestionRepository attemptQuestions,
                          AttemptAnswerRepository answers, ExamRepository exams,
                          ExamCandidateRepository roster, ExamQuestionRepository examQuestions,
                          QuestionRepository questions, ResultRepository results, ObjectMapper objectMapper) {
        this.attempts = attempts;
        this.attemptQuestions = attemptQuestions;
        this.answers = answers;
        this.exams = exams;
        this.roster = roster;
        this.examQuestions = examQuestions;
        this.questions = questions;
        this.results = results;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AttemptDtos.AttemptResponse start(UUID candidateId, UUID examId) {
        ExamEntity exam = candidateExam(candidateId, examId);
        Instant now = clock.instant();
        AttemptEntity existing = attempts.findByExamIdAndCandidateId(examId, candidateId).orElse(null);
        if (existing != null) {
            if ("IN_PROGRESS".equals(existing.getStatus()) && !now.isBefore(existing.getEffectiveEndTime())) {
                expireAndGrade(existing, now);
            }
            return toResponse(existing);
        }
        if (now.isBefore(exam.getWindowStartTime())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EXAM_NOT_AVAILABLE", "The examination is not available yet");
        }
        if (!now.isBefore(exam.getWindowEndTime())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EXAM_WINDOW_CLOSED", "The examination window has closed");
        }
        Instant effectiveEnd = now.plusSeconds(exam.getDurationMinutes() * 60L);
        if (effectiveEnd.isAfter(exam.getWindowEndTime())) effectiveEnd = exam.getWindowEndTime();
        AttemptEntity attempt = attempts.save(AttemptEntity.start(examId, candidateId, now, effectiveEnd));
        List<ExamQuestionEntity> links = new ArrayList<>(examQuestions.findByIdExamIdOrderBySequenceNumber(examId));
        if (exam.isShuffleQuestions()) Collections.shuffle(links);
        List<AttemptQuestionEntity> paper = new ArrayList<>();
        for (int sequence = 0; sequence < links.size(); sequence++) {
            QuestionEntity question = questions.findById(links.get(sequence).getQuestionId())
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "EXAM_QUESTION_UNAVAILABLE", "The examination question is unavailable"));
            List<QuestionDtos.OptionResponse> options = readOptions(question.getCanonicalOptions());
            List<Integer> displayed = options.stream().map(QuestionDtos.OptionResponse::index).toList();
            List<Integer> order = new ArrayList<>(displayed);
            if (exam.isShuffleOptions()) Collections.shuffle(order);
            paper.add(new AttemptQuestionEntity(attempt.getId(), question.getId(), sequence, write(order)));
        }
        attemptQuestions.saveAll(paper);
        return toResponse(attempt);
    }

    @Transactional
    public AttemptDtos.ActionResponse action(UUID candidateId, UUID examId, AttemptDtos.ActionRequest request) {
        candidateExam(candidateId, examId);
        AttemptEntity attempt = lockedAttempt(candidateId, examId);
        Instant now = clock.instant();
        if (!"IN_PROGRESS".equals(attempt.getStatus())) {
            throw conflict("ATTEMPT_CLOSED", "This attempt is no longer accepting actions");
        }
        if (!now.isBefore(attempt.getEffectiveEndTime())) {
            expireAndGrade(attempt, now);
            throw conflict("ATTEMPT_EXPIRED", "The examination time has expired");
        }
        AttemptQuestionEntity paperQuestion = attemptQuestions.findById(new AttemptQuestionId(attempt.getId(), request.questionId()))
                .orElseThrow(() -> notFound("QUESTION_NOT_IN_ATTEMPT", "Question is not part of this attempt"));
        AttemptAnswerEntity current = answers.findById(new AttemptAnswerId(attempt.getId(), request.questionId())).orElse(null);
        if (current != null && request.clientSeq() < current.getClientSeq()) {
            throw conflict("STALE_ACTION", "A newer answer action has already been accepted");
        }
        if (current != null && request.clientSeq() == current.getClientSeq()) {
            return actionResponse(attempt, request.action(), current);
        }
        validateAction(request.action(), request.displayedOptionIndices(), paperQuestion, questionFor(paperQuestion));
        List<Integer> selected = current != null && "VISIT_QUESTION".equals(request.action())
                ? readIndices(current.getSelectedOptionIndices())
                : List.copyOf(request.displayedOptionIndices().stream().distinct().sorted().toList());
        HeatmapState state = nextState(request.action(), selected, current);
        Instant updatedAt = now;
        if (current == null) {
            current = new AttemptAnswerEntity(attempt.getId(), request.questionId(), write(selected), state,
                    request.clientSeq(), updatedAt);
        } else {
            current.update(write(selected), state, request.clientSeq(), updatedAt);
        }
        answers.save(current);
        return new AttemptDtos.ActionResponse(attempt.getId(), request.questionId(), now,
                attempt.getEffectiveEndTime(), request.action(), state, selected, request.clientSeq());
    }

    @Transactional
    public AttemptDtos.SubmissionReceipt submit(UUID candidateId, UUID examId) {
        candidateExam(candidateId, examId);
        AttemptEntity attempt = lockedAttempt(candidateId, examId);
        if (!"IN_PROGRESS".equals(attempt.getStatus())) {
            return new AttemptDtos.SubmissionReceipt(attempt.getSubmissionId(), attempt.getSubmittedAt(), attempt.getStatus());
        }
        Instant now = clock.instant();
        String finalStatus = now.isBefore(attempt.getEffectiveEndTime()) ? "SUBMITTED" : "EXPIRED";
        UUID submissionId = UUID.randomUUID();
        attempt.complete(finalStatus, now, submissionId);
        attempts.save(attempt);
        grade(attempt, now);
        return new AttemptDtos.SubmissionReceipt(submissionId, now, finalStatus);
    }

    @Transactional(readOnly = true)
    public AttemptDtos.ResultResponse getResult(UUID candidateId, UUID examId) {
        candidateExam(candidateId, examId);
        ResultEntity result = results.findByExamIdAndCandidateId(examId, candidateId)
                .orElseThrow(() -> notFound("RESULT_NOT_AVAILABLE", "No result is available for this examination"));
        if (!result.isPublished()) throw new ApiException(HttpStatus.FORBIDDEN, "RESULT_NOT_PUBLISHED", "The result has not been published");
        return toResult(result);
    }

    @Transactional
    public AttemptDtos.PublicationResponse publishResults(UUID adminId, UUID examId) {
        ExamEntity exam = exams.findByIdAndCreatedBy(examId, adminId)
                .orElseThrow(() -> notFound("EXAM_NOT_FOUND", "Exam not found"));
        if (!exam.isPublished()) throw new ApiException(HttpStatus.BAD_REQUEST, "EXAM_NOT_PUBLISHED", "Only published exams can publish results");
        Instant now = clock.instant();
        List<ResultEntity> examResults = results.findByExamId(examId);
        int newlyPublished = (int) examResults.stream().filter(result -> !result.isPublished()).peek(result -> result.publish(now)).count();
        results.saveAll(examResults);
        return new AttemptDtos.PublicationResponse(examId, newlyPublished, now);
    }

    private AttemptEntity lockedAttempt(UUID candidateId, UUID examId) {
        return attempts.findByExamIdAndCandidateIdForUpdate(examId, candidateId)
                .orElseThrow(() -> notFound("ATTEMPT_NOT_STARTED", "No attempt has been started"));
    }

    private AttemptDtos.AttemptResponse toResponse(AttemptEntity attempt) {
        List<AttemptQuestionEntity> paper = attemptQuestions.findByIdAttemptIdOrderBySequenceNumber(attempt.getId());
        Map<UUID, QuestionEntity> questionMap = questions.findAllById(paper.stream().map(AttemptQuestionEntity::getQuestionId).toList())
                .stream().collect(java.util.stream.Collectors.toMap(QuestionEntity::getId, question -> question));
        Map<UUID, AttemptAnswerEntity> answerMap = answers.findByIdAttemptId(attempt.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(AttemptAnswerEntity::getQuestionId, answer -> answer));
        List<AttemptDtos.AttemptQuestionResponse> response = paper.stream().map(paperQuestion -> {
            QuestionEntity question = questionMap.get(paperQuestion.getQuestionId());
            if (question == null) throw new ApiException(HttpStatus.CONFLICT, "EXAM_QUESTION_UNAVAILABLE", "The examination question is unavailable");
            AttemptAnswerEntity answer = answerMap.get(paperQuestion.getQuestionId());
            HeatmapState state = answer == null ? HeatmapState.NOT_VISITED : answer.getState();
            List<Integer> selected = answer == null ? List.of() : readIndices(answer.getSelectedOptionIndices());
            return new AttemptDtos.AttemptQuestionResponse(question.getId(), question.getType(), question.getCategory(),
                    question.getDifficulty(), question.getContentMarkdown(), displayedOptions(question, paperQuestion),
                    state, selected, answer == null ? 0 : answer.getClientSeq());
        }).toList();
        return new AttemptDtos.AttemptResponse(attempt.getId(), attempt.getExamId(), clock.instant(),
                attempt.getEffectiveEndTime(), attempt.getStatus(), response);
    }

    private List<QuestionDtos.OptionResponse> displayedOptions(QuestionEntity question, AttemptQuestionEntity paperQuestion) {
        Map<Integer, String> textByIndex = readOptions(question.getCanonicalOptions()).stream()
                .collect(java.util.stream.Collectors.toMap(QuestionDtos.OptionResponse::index, QuestionDtos.OptionResponse::text));
        List<Integer> order = readIndices(paperQuestion.getDisplayedOptionIndices());
        List<QuestionDtos.OptionResponse> result = new ArrayList<>();
        for (int position = 0; position < order.size(); position++) {
            String text = textByIndex.get(order.get(position));
            if (text == null) throw new ApiException(HttpStatus.CONFLICT, "EXAM_QUESTION_INVALID", "The examination question is invalid");
            result.add(new QuestionDtos.OptionResponse(position, text));
        }
        return result;
    }

    private void validateAction(String action, List<Integer> selected, AttemptQuestionEntity paperQuestion, QuestionEntity question) {
        if (!List.of("VISIT_QUESTION", "SAVE_AND_NEXT", "MARK_REVIEW_AND_NEXT", "CLEAR_RESPONSE").contains(action)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACTION_UNSUPPORTED", "The answer action is unsupported");
        }
        List<Integer> order = readIndices(paperQuestion.getDisplayedOptionIndices());
        if (selected.stream().distinct().count() != selected.size() || selected.stream().anyMatch(index -> index < 0 || index >= order.size())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OPTION_INVALID", "The selected option is invalid");
        }
        if ("MCQ_SINGLE".equals(question.getType()) && selected.size() > 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OPTION_INVALID", "A single-choice question accepts one option");
        }
    }

    private HeatmapState nextState(String action, List<Integer> selected, AttemptAnswerEntity current) {
        if ("VISIT_QUESTION".equals(action) && current != null) return current.getState();
        if ("MARK_REVIEW_AND_NEXT".equals(action)) {
            return selected.isEmpty() ? HeatmapState.MARKED_FOR_REVIEW : HeatmapState.ANSWERED_AND_MARKED_FOR_REVIEW;
        }
        return selected.isEmpty() ? HeatmapState.NOT_ANSWERED : HeatmapState.ANSWERED;
    }

    private AttemptDtos.ActionResponse actionResponse(AttemptEntity attempt, String action, AttemptAnswerEntity answer) {
        return new AttemptDtos.ActionResponse(attempt.getId(), answer.getQuestionId(), clock.instant(),
                attempt.getEffectiveEndTime(), action, answer.getState(), readIndices(answer.getSelectedOptionIndices()), answer.getClientSeq());
    }

    private QuestionEntity questionFor(AttemptQuestionEntity paperQuestion) {
        return questions.findById(paperQuestion.getQuestionId())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "EXAM_QUESTION_UNAVAILABLE", "The examination question is unavailable"));
    }

    private void expireAndGrade(AttemptEntity attempt, Instant at) {
        if (!"IN_PROGRESS".equals(attempt.getStatus())) return;
        attempt.complete("EXPIRED", at, UUID.randomUUID());
        attempts.save(attempt);
        grade(attempt, at);
    }

    private void grade(AttemptEntity attempt, Instant submittedAt) {
        if (results.findByAttemptId(attempt.getId()).isPresent()) return;
        List<AttemptQuestionEntity> paper = attemptQuestions.findByIdAttemptIdOrderBySequenceNumber(attempt.getId());
        Map<UUID, QuestionEntity> questionMap = questions.findAllById(paper.stream().map(AttemptQuestionEntity::getQuestionId).toList())
                .stream().collect(java.util.stream.Collectors.toMap(QuestionEntity::getId, question -> question));
        Map<UUID, AttemptAnswerEntity> answerMap = answers.findByIdAttemptId(attempt.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(AttemptAnswerEntity::getQuestionId, answer -> answer));
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal maximum = BigDecimal.ZERO;
        int correct = 0;
        int incorrect = 0;
        int unattempted = 0;
        for (AttemptQuestionEntity paperQuestion : paper) {
            QuestionEntity question = questionMap.get(paperQuestion.getQuestionId());
            if (question == null) throw new ApiException(HttpStatus.CONFLICT, "EXAM_QUESTION_UNAVAILABLE", "The examination question is unavailable");
            maximum = maximum.add(question.getPositiveMarks());
            AttemptAnswerEntity answer = answerMap.get(paperQuestion.getQuestionId());
            List<Integer> selectedDisplayed = answer == null ? List.of() : readIndices(answer.getSelectedOptionIndices());
            if (selectedDisplayed.isEmpty()) {
                unattempted++;
                continue;
            }
            Set<Integer> selectedCanonical = selectedDisplayed.stream().map(index -> readIndices(paperQuestion.getDisplayedOptionIndices()).get(index)).collect(java.util.stream.Collectors.toSet());
            Set<Integer> correctCanonical = new HashSet<>(readIndices(question.getCorrectOptionIndices()));
            if (selectedCanonical.equals(correctCanonical)) {
                correct++;
                total = total.add(question.getPositiveMarks());
            } else {
                incorrect++;
                total = total.subtract(question.getNegativeMarks());
            }
        }
        BigDecimal percentage = maximum.signum() == 0 ? BigDecimal.ZERO : total.multiply(BigDecimal.valueOf(100)).divide(maximum, 2, RoundingMode.HALF_UP);
        long elapsedSeconds = Math.max(0, Math.min(Duration.between(attempt.getStartedAt(), submittedAt).getSeconds(),
                Duration.between(attempt.getStartedAt(), attempt.getEffectiveEndTime()).getSeconds()));
        results.save(ResultEntity.graded(attempt.getId(), attempt.getExamId(), attempt.getCandidateId(), submittedAt,
                total, maximum, percentage, correct, incorrect, unattempted, (int) (elapsedSeconds / 60), submittedAt));
    }

    private ExamEntity candidateExam(UUID candidateId, UUID examId) {
        ExamEntity exam = exams.findById(examId).filter(ExamEntity::isPublished)
                .orElseThrow(() -> notFound("EXAM_NOT_FOUND", "Exam not found"));
        if (!roster.existsByIdExamIdAndIdCandidateId(examId, candidateId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ROSTER_ACCESS_DENIED", "You are not enrolled in this examination");
        }
        return exam;
    }

    private List<QuestionDtos.OptionResponse> readOptions(String value) { return read(value, OPTIONS); }
    private List<Integer> readIndices(String value) { return read(value, INDICES); }

    private <T> T read(String value, TypeReference<T> type) {
        try { return objectMapper.readValue(value, type); }
        catch (JsonProcessingException exception) { throw new ApiException(HttpStatus.CONFLICT, "ASSESSMENT_DATA_INVALID", "Assessment data is invalid"); }
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ASSESSMENT_SERIALIZATION_FAILED", "Assessment data could not be saved"); }
    }

    private AttemptDtos.ResultResponse toResult(ResultEntity result) {
        return new AttemptDtos.ResultResponse(result.getId(), result.getExamId(), result.getSubmittedAt(), result.getTotalMarks(),
                result.getMaximumMarks(), result.getPercentage(), result.getCorrectCount(), result.getIncorrectCount(),
                result.getUnattemptedCount(), result.getTimeTakenMinutes(), result.isPublished());
    }

    private static ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    private static ApiException notFound(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }
}
