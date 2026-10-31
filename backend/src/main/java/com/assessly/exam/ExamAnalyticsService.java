package com.assessly.exam;

import com.assessly.attempt.AttemptAnswerEntity;
import com.assessly.attempt.AttemptAnswerRepository;
import com.assessly.attempt.AttemptEntity;
import com.assessly.attempt.AttemptQuestionEntity;
import com.assessly.attempt.AttemptQuestionRepository;
import com.assessly.attempt.AttemptRepository;
import com.assessly.question.QuestionEntity;
import com.assessly.question.QuestionRepository;
import com.assessly.result.ResultEntity;
import com.assessly.result.ResultRepository;
import com.assessly.shared.ApiException;
import com.assessly.user.AuthRole;
import com.assessly.user.UserEntity;
import com.assessly.user.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ExamAnalyticsService {
    private static final TypeReference<List<Integer>> INDICES = new TypeReference<>() { };

    private final ExamRepository exams;
    private final ExamCandidateRepository roster;
    private final ExamQuestionRepository examQuestions;
    private final AttemptRepository attempts;
    private final AttemptQuestionRepository attemptQuestions;
    private final AttemptAnswerRepository answers;
    private final QuestionRepository questions;
    private final ResultRepository results;
    private final UserRepository users;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    public ExamAnalyticsService(ExamRepository exams, ExamCandidateRepository roster,
                                ExamQuestionRepository examQuestions, AttemptRepository attempts,
                                AttemptQuestionRepository attemptQuestions, AttemptAnswerRepository answers,
                                QuestionRepository questions, ResultRepository results, UserRepository users,
                                ObjectMapper objectMapper) {
        this.exams = exams;
        this.roster = roster;
        this.examQuestions = examQuestions;
        this.attempts = attempts;
        this.attemptQuestions = attemptQuestions;
        this.answers = answers;
        this.questions = questions;
        this.results = results;
        this.users = users;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ExamDtos.CandidateDirectoryResponse> listCandidates() {
        return users.findAllByRoleAndActiveTrueOrderByFullName(AuthRole.ROLE_CANDIDATE).stream()
                .map(user -> new ExamDtos.CandidateDirectoryResponse(user.getId(), user.getFullName(),
                        user.getEmail(), user.getRollNumber(), user.isActive()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExamDtos.AdminResultResponse> listResults(UUID adminId, UUID examId) {
        ExamEntity exam = ownedExam(adminId, examId);
        Instant now = clock.instant();
        List<UUID> candidateIds = roster.findByIdExamId(examId).stream()
                .map(ExamCandidateEntity::getCandidateId).toList();
        Map<UUID, UserEntity> candidateMap = users.findAllById(candidateIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, user -> user));
        Map<UUID, AttemptEntity> attemptMap = attempts.findByExamId(examId).stream()
                .collect(Collectors.toMap(AttemptEntity::getCandidateId, attempt -> attempt));
        Map<UUID, ResultEntity> resultMap = results.findByExamId(examId).stream()
                .collect(Collectors.toMap(ResultEntity::getCandidateId, result -> result));

        return candidateIds.stream().map(candidateId -> {
            UserEntity candidate = candidateMap.get(candidateId);
            AttemptEntity attempt = attemptMap.get(candidateId);
            ResultEntity result = resultMap.get(candidateId);
            boolean eligible = result != null && !now.isBefore(exam.getWindowEndTime());
            return new ExamDtos.AdminResultResponse(candidateId, candidate == null ? "Unavailable" : candidate.getFullName(),
                    candidate == null ? null : candidate.getEmail(), candidate == null ? null : candidate.getRollNumber(),
                    attempt == null ? "NOT_STARTED" : attempt.getStatus(), result == null ? null : result.getSubmittedAt(),
                    result == null ? null : result.getTotalMarks(), result == null ? null : result.getMaximumMarks(),
                    result == null ? null : result.getPercentage(), result == null ? 0 : result.getCorrectCount(),
                    result == null ? 0 : result.getIncorrectCount(), result == null ? 0 : result.getUnattemptedCount(),
                    result != null && result.isPublished(), eligible);
        }).toList();
    }

    @Transactional(readOnly = true)
    public ExamDtos.AnalyticsResponse analytics(UUID adminId, UUID examId) {
        ownedExam(adminId, examId);
        List<ExamCandidateEntity> rosterEntries = roster.findByIdExamId(examId);
        List<AttemptEntity> examAttempts = attempts.findByExamId(examId);
        List<ResultEntity> gradedResults = results.findByExamId(examId);

        int started = examAttempts.size();
        int submitted = (int) examAttempts.stream().filter(attempt -> "SUBMITTED".equals(attempt.getStatus())).count();
        int expired = (int) examAttempts.stream().filter(attempt -> "EXPIRED".equals(attempt.getStatus())).count();
        int notStarted = Math.max(0, rosterEntries.size() - started);
        int published = (int) gradedResults.stream().filter(ResultEntity::isPublished).count();
        List<BigDecimal> percentages = gradedResults.stream().map(ResultEntity::getPercentage)
                .sorted().toList();

        List<ExamDtos.ScoreBucket> distribution = scoreDistribution(percentages);
        List<ExamDtos.QuestionAnalyticsResponse> questionAnalytics = questionAnalytics(examId, gradedResults);
        return new ExamDtos.AnalyticsResponse(examId, rosterEntries.size(), started, submitted, expired, notStarted,
                gradedResults.size(), published, average(percentages), median(percentages),
                percentages.isEmpty() ? null : percentages.get(percentages.size() - 1),
                percentages.isEmpty() ? null : percentages.get(0), distribution, questionAnalytics);
    }

    private List<ExamDtos.QuestionAnalyticsResponse> questionAnalytics(UUID examId, List<ResultEntity> gradedResults) {
        List<QuestionEntity> examQuestionsInOrder = examQuestions.findByIdExamIdOrderBySequenceNumber(examId).stream()
                .map(ExamQuestionEntity::getQuestionId)
                .map(questionId -> questions.findById(questionId).orElse(null))
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, QuestionCounts> counts = examQuestionsInOrder.stream()
                .collect(Collectors.toMap(QuestionEntity::getId, question -> new QuestionCounts()));

        for (ResultEntity result : gradedResults) {
            Map<UUID, AttemptQuestionEntity> paper = attemptQuestions.findByIdAttemptId(result.getAttemptId()).stream()
                    .collect(Collectors.toMap(AttemptQuestionEntity::getQuestionId, question -> question));
            Map<UUID, AttemptAnswerEntity> answerMap = answers.findByIdAttemptId(result.getAttemptId()).stream()
                    .collect(Collectors.toMap(AttemptAnswerEntity::getQuestionId, answer -> answer));
            for (QuestionEntity question : examQuestionsInOrder) {
                QuestionCounts questionCounts = counts.get(question.getId());
                AttemptAnswerEntity answer = answerMap.get(question.getId());
                if (answer == null || readIndices(answer.getSelectedOptionIndices()).isEmpty()) {
                    questionCounts.unattempted++;
                    continue;
                }
                questionCounts.attempted++;
                AttemptQuestionEntity paperQuestion = paper.get(question.getId());
                if (paperQuestion != null && isCorrect(question, paperQuestion, answer)) questionCounts.correct++;
                else questionCounts.incorrect++;
            }
        }

        return examQuestionsInOrder.stream().map(question -> {
            QuestionCounts value = counts.get(question.getId());
            BigDecimal accuracy = gradedResults.isEmpty() ? BigDecimal.ZERO
                    : BigDecimal.valueOf(value.correct * 100.0 / gradedResults.size()).setScale(2, RoundingMode.HALF_UP);
            return new ExamDtos.QuestionAnalyticsResponse(question.getId(), question.getContentMarkdown(),
                    gradedResults.size(), value.attempted, value.correct, value.incorrect, value.unattempted, accuracy);
        }).toList();
    }

    private boolean isCorrect(QuestionEntity question, AttemptQuestionEntity paperQuestion, AttemptAnswerEntity answer) {
        List<Integer> displayedOrder = readIndices(paperQuestion.getDisplayedOptionIndices());
        List<Integer> selectedDisplayed = readIndices(answer.getSelectedOptionIndices());
        if (selectedDisplayed.stream().anyMatch(index -> index < 0 || index >= displayedOrder.size())) return false;
        Set<Integer> selectedCanonical = selectedDisplayed.stream().map(displayedOrder::get).collect(Collectors.toSet());
        return selectedCanonical.equals(new HashSet<>(readIndices(question.getCorrectOptionIndices())));
    }

    private List<ExamDtos.ScoreBucket> scoreDistribution(List<BigDecimal> percentages) {
        int[] counts = new int[5];
        for (BigDecimal percentage : percentages) {
            BigDecimal normalized = percentage.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
            int bucket = normalized.compareTo(BigDecimal.valueOf(20)) <= 0 ? 0
                    : normalized.compareTo(BigDecimal.valueOf(40)) <= 0 ? 1
                    : normalized.compareTo(BigDecimal.valueOf(60)) <= 0 ? 2
                    : normalized.compareTo(BigDecimal.valueOf(80)) <= 0 ? 3 : 4;
            counts[bucket]++;
        }
        return List.of(new ExamDtos.ScoreBucket("0–20%", counts[0]), new ExamDtos.ScoreBucket("21–40%", counts[1]),
                new ExamDtos.ScoreBucket("41–60%", counts[2]), new ExamDtos.ScoreBucket("61–80%", counts[3]),
                new ExamDtos.ScoreBucket("81–100%", counts[4]));
    }

    private static BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) return null;
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) return null;
        int middle = values.size() / 2;
        if (values.size() % 2 == 1) return values.get(middle).setScale(2, RoundingMode.HALF_UP);
        return values.get(middle - 1).add(values.get(middle)).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private List<Integer> readIndices(String value) {
        try { return objectMapper.readValue(value, INDICES); }
        catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "ASSESSMENT_DATA_INVALID", "Assessment data is invalid");
        }
    }

    private ExamEntity ownedExam(UUID adminId, UUID examId) {
        return exams.findByIdAndCreatedBy(examId, adminId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EXAM_NOT_FOUND", "Exam not found"));
    }

    private static final class QuestionCounts {
        private int attempted;
        private int correct;
        private int incorrect;
        private int unattempted;
    }
}
