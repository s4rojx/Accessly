package com.assessly.question;

import com.assessly.exam.ExamEntity;
import com.assessly.exam.ExamQuestionRepository;
import com.assessly.exam.ExamRepository;
import com.assessly.shared.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class QuestionService {
    private static final TypeReference<List<QuestionDtos.OptionResponse>> OPTIONS = new TypeReference<>() { };
    private static final TypeReference<List<Integer>> INDICES = new TypeReference<>() { };

    private final QuestionRepository questions;
    private final ExamRepository exams;
    private final ExamQuestionRepository examQuestions;
    private final ObjectMapper objectMapper;

    public QuestionService(QuestionRepository questions, ExamRepository exams,
                           ExamQuestionRepository examQuestions, ObjectMapper objectMapper) {
        this.questions = questions;
        this.exams = exams;
        this.examQuestions = examQuestions;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<QuestionDtos.AdminResponse> list(UUID adminId) {
        return questions.findAllByCreatedByAndArchivedFalseOrderByCreatedAtDesc(adminId).stream()
                .map(this::toAdmin).toList();
    }

    @Transactional
    public QuestionDtos.AdminResponse create(UUID adminId, QuestionDtos.UpsertRequest request) {
        Validated validated = validate(request);
        QuestionEntity entity = QuestionEntity.create(validated.type(), request.category().trim(), validated.difficulty(),
                request.prompt().trim(), write(validated.options()), write(validated.correctIndices()), request.positiveMarks(),
                request.negativeMarks(), adminId);
        return toAdmin(questions.save(entity));
    }

    @Transactional
    public QuestionDtos.AdminResponse update(UUID adminId, UUID questionId, QuestionDtos.UpsertRequest request) {
        QuestionEntity entity = ownedQuestion(adminId, questionId);
        if (examQuestions.findByIdQuestionId(questionId).stream().map(link -> exams.findById(link.getExamId()))
                .flatMap(java.util.Optional::stream).anyMatch(ExamEntity::isPublished)) {
            throw conflict("PUBLISHED_EXAM_QUESTION_IMMUTABLE", "Questions in published exams cannot be changed");
        }
        Validated validated = validate(request);
        entity.update(validated.type(), request.category().trim(), validated.difficulty(), request.prompt().trim(),
                write(validated.options()), write(validated.correctIndices()), request.positiveMarks(), request.negativeMarks());
        return toAdmin(questions.save(entity));
    }

    @Transactional(readOnly = true)
    public QuestionEntity ownedQuestion(UUID adminId, UUID questionId) {
        return questions.findById(questionId).filter(q -> q.getCreatedBy().equals(adminId) && !q.isArchived())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "Question not found"));
    }

    @Transactional(readOnly = true)
    public List<QuestionEntity> ownedQuestions(UUID adminId, List<UUID> ids) {
        List<QuestionEntity> found = questions.findAllByIdInAndArchivedFalse(ids);
        if (found.size() != new HashSet<>(ids).size() || found.stream().anyMatch(q -> !q.getCreatedBy().equals(adminId))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUESTION_SELECTION", "Question selection is invalid");
        }
        return found;
    }

    public QuestionDtos.AdminResponse toAdmin(QuestionEntity entity) {
        return new QuestionDtos.AdminResponse(entity.getId(), entity.getType(), entity.getCategory(), entity.getDifficulty(),
                entity.getContentMarkdown(), readOptions(entity.getCanonicalOptions()), readIndices(entity.getCorrectOptionIndices()),
                entity.getPositiveMarks(), entity.getNegativeMarks());
    }

    public QuestionDtos.CandidateResponse toCandidate(QuestionEntity entity) {
        return new QuestionDtos.CandidateResponse(entity.getId(), entity.getType(), entity.getCategory(), entity.getDifficulty(),
                entity.getContentMarkdown(), readOptions(entity.getCanonicalOptions()));
    }

    private Validated validate(QuestionDtos.UpsertRequest request) {
        String type = request.type().trim();
        String difficulty = request.difficulty().trim();
        if (!type.equals("MCQ_SINGLE") && !type.equals("MCQ_MULTI")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "QUESTION_TYPE_UNSUPPORTED", "Only MCQ questions are supported");
        }
        if (!List.of("EASY", "MEDIUM", "HARD").contains(difficulty)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "QUESTION_DIFFICULTY_INVALID", "Question difficulty is invalid");
        }
        List<QuestionDtos.OptionRequest> options = request.options().stream().sorted(Comparator.comparing(QuestionDtos.OptionRequest::index)).toList();
        List<Integer> optionIndices = options.stream().map(QuestionDtos.OptionRequest::index).toList();
        if (!optionIndices.equals(java.util.stream.IntStream.range(0, options.size()).boxed().toList())
                || new HashSet<>(optionIndices).size() != optionIndices.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "QUESTION_OPTIONS_INVALID", "Option indices must be unique and contiguous");
        }
        List<Integer> correct = request.correctOptionIndices().stream().distinct().sorted().toList();
        if (type.equals("MCQ_SINGLE") && correct.size() != 1 || correct.isEmpty() || correct.stream().anyMatch(index -> !optionIndices.contains(index))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "QUESTION_ANSWER_KEY_INVALID", "Correct option indices are invalid");
        }
        return new Validated(type, difficulty, options.stream().map(option -> new QuestionDtos.OptionResponse(option.index(), option.text().trim())).toList(), correct);
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize question data", exception); }
    }

    private List<QuestionDtos.OptionResponse> readOptions(String value) {
        try { return objectMapper.readValue(value, OPTIONS); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to read question options", exception); }
    }

    private List<Integer> readIndices(String value) {
        try { return objectMapper.readValue(value, INDICES); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to read question answer data", exception); }
    }

    private static ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    private record Validated(String type, String difficulty, List<QuestionDtos.OptionResponse> options, List<Integer> correctIndices) { }
}
