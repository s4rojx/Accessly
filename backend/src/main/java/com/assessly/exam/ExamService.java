package com.assessly.exam;

import com.assessly.question.QuestionEntity;
import com.assessly.question.QuestionDtos;
import com.assessly.question.QuestionService;
import com.assessly.shared.ApiException;
import com.assessly.user.AuthRole;
import com.assessly.user.UserEntity;
import com.assessly.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ExamService {
    private final ExamRepository exams;
    private final ExamQuestionRepository examQuestions;
    private final ExamCandidateRepository candidates;
    private final QuestionService questionService;
    private final UserRepository users;
    private final Clock clock = Clock.systemUTC();

    public ExamService(ExamRepository exams, ExamQuestionRepository examQuestions, ExamCandidateRepository candidates,
                       QuestionService questionService, UserRepository users) {
        this.exams = exams;
        this.examQuestions = examQuestions;
        this.candidates = candidates;
        this.questionService = questionService;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<ExamDtos.AdminResponse> listAdmin(UUID adminId) {
        return exams.findAllByCreatedByOrderByCreatedAtDesc(adminId).stream().map(this::toAdmin).toList();
    }

    @Transactional(readOnly = true)
    public ExamDtos.AdminResponse getAdmin(UUID adminId, UUID examId) {
        return toAdmin(ownedExam(adminId, examId));
    }

    @Transactional
    public ExamDtos.AdminResponse create(UUID adminId, ExamDtos.UpsertRequest request) {
        validateSchedule(request.windowStartTime(), request.windowEndTime(), request.durationMinutes());
        ExamEntity exam = ExamEntity.draft(clean(request.title()), clean(request.subject()), clean(request.description()),
                request.windowStartTime(), request.windowEndTime(), request.durationMinutes(), request.shuffleQuestions(),
                request.shuffleOptions(), adminId);
        ExamEntity saved = exams.save(exam);
        replaceQuestions(saved, adminId, request.questionIds(), false);
        return toAdmin(saved);
    }

    @Transactional
    public ExamDtos.AdminResponse update(UUID adminId, UUID examId, ExamDtos.UpsertRequest request) {
        ExamEntity exam = ownedExam(adminId, examId);
        if (exam.isPublished()) throw conflict("PUBLISHED_EXAM_IMMUTABLE", "Published exams cannot be changed");
        validateSchedule(request.windowStartTime(), request.windowEndTime(), request.durationMinutes());
        exam.update(clean(request.title()), clean(request.subject()), clean(request.description()), request.windowStartTime(),
                request.windowEndTime(), request.durationMinutes(), request.shuffleQuestions(), request.shuffleOptions());
        replaceQuestions(exam, adminId, request.questionIds(), false);
        return toAdmin(exams.save(exam));
    }

    @Transactional
    public ExamDtos.AdminResponse publish(UUID adminId, UUID examId) {
        ExamEntity exam = ownedExam(adminId, examId);
        if (exam.isPublished()) return toAdmin(exam);
        List<QuestionEntity> selected = selectedQuestions(exam);
        if (selected.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "EXAM_REQUIRES_QUESTIONS", "An exam needs at least one question");
        if (!exam.getWindowEndTime().isAfter(clock.instant())) throw new ApiException(HttpStatus.BAD_REQUEST, "EXAM_WINDOW_EXPIRED", "Exam window must end in the future");
        exam.publish();
        return toAdmin(exams.save(exam));
    }

    @Transactional
    public ExamDtos.RosterResponse replaceRoster(UUID adminId, UUID examId, ExamDtos.RosterRequest request) {
        ExamEntity exam = ownedExam(adminId, examId);
        if (exam.isPublished()) throw conflict("PUBLISHED_EXAM_ROSTER_IMMUTABLE", "Published exam rosters cannot be changed");
        List<UUID> uniqueIds = request.candidateIds().stream().distinct().toList();
        if (uniqueIds.size() != request.candidateIds().size()) throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_CANDIDATE", "Candidate IDs must be unique");
        List<UserEntity> found = users.findAllById(uniqueIds);
        if (found.size() != uniqueIds.size() || found.stream().anyMatch(user -> user.getRole() != AuthRole.ROLE_CANDIDATE || !user.isActive())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CANDIDATE_ROSTER", "Roster contains an invalid candidate");
        }
        candidates.deleteByIdExamId(examId);
        candidates.saveAll(uniqueIds.stream().map(candidateId -> new ExamCandidateEntity(examId, candidateId)).toList());
        return new ExamDtos.RosterResponse(examId, uniqueIds);
    }

    @Transactional(readOnly = true)
    public ExamDtos.RosterResponse getRoster(UUID adminId, UUID examId) {
        ownedExam(adminId, examId);
        return new ExamDtos.RosterResponse(examId, candidates.findByIdExamId(examId).stream().map(ExamCandidateEntity::getCandidateId).toList());
    }

    @Transactional(readOnly = true)
    public List<ExamDtos.CandidateResponse> listCandidate(UUID candidateId) {
        return candidates.findByIdCandidateId(candidateId).stream().map(ExamCandidateEntity::getExamId).map(exams::findById)
                .flatMap(java.util.Optional::stream).filter(ExamEntity::isPublished).map(exam -> toCandidate(exam, candidateId)).toList();
    }

    @Transactional(readOnly = true)
    public ExamDtos.CandidateResponse getCandidate(UUID candidateId, UUID examId) {
        ExamEntity exam = exams.findById(examId).filter(ExamEntity::isPublished).orElseThrow(() -> notFound("EXAM_NOT_FOUND", "Exam not found"));
        if (!candidates.existsByIdExamIdAndIdCandidateId(examId, candidateId)) throw notFound("EXAM_NOT_FOUND", "Exam not found");
        return toCandidate(exam, candidateId);
    }

    private ExamEntity ownedExam(UUID adminId, UUID examId) {
        return exams.findByIdAndCreatedBy(examId, adminId).orElseThrow(() -> notFound("EXAM_NOT_FOUND", "Exam not found"));
    }

    private void replaceQuestions(ExamEntity exam, UUID adminId, List<UUID> ids, boolean requireQuestions) {
        List<UUID> uniqueIds = ids.stream().distinct().toList();
        if (uniqueIds.size() != ids.size()) throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_QUESTION", "Question IDs must be unique");
        if (requireQuestions && uniqueIds.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "EXAM_REQUIRES_QUESTIONS", "An exam needs at least one question");
        questionService.ownedQuestions(adminId, uniqueIds);
        examQuestions.deleteByIdExamId(exam.getId());
        examQuestions.saveAll(uniqueIds.stream().map(id -> new ExamQuestionEntity(exam.getId(), id, uniqueIds.indexOf(id))).toList());
    }

    private List<QuestionEntity> selectedQuestions(ExamEntity exam) {
        List<UUID> ids = examQuestions.findByIdExamIdOrderBySequenceNumber(exam.getId()).stream().map(ExamQuestionEntity::getQuestionId).toList();
        return questionService.ownedQuestions(exam.getCreatedBy(), ids);
    }

    private ExamDtos.AdminResponse toAdmin(ExamEntity exam) {
        List<QuestionEntity> selected = selectedQuestions(exam);
        List<QuestionDtos.AdminResponse> questions = selected.stream().map(questionService::toAdmin).toList();
        return new ExamDtos.AdminResponse(exam.getId(), exam.getTitle(), exam.getSubject(), exam.getDescription(), exam.getWindowStartTime(),
                exam.getWindowEndTime(), exam.getDurationMinutes(), maximumMarks(selected), questions, exam.getStatus(),
                exam.isShuffleQuestions(), exam.isShuffleOptions(), candidates.findByIdExamId(exam.getId()).size());
    }

    private ExamDtos.CandidateResponse toCandidate(ExamEntity exam, UUID candidateId) {
        List<QuestionEntity> selected = selectedQuestions(exam);
        return new ExamDtos.CandidateResponse(exam.getId(), exam.getTitle(), exam.getSubject(), exam.getDescription(), exam.getWindowStartTime(),
                exam.getWindowEndTime(), exam.getDurationMinutes(), maximumMarks(selected), selected.stream().map(questionService::toCandidate).toList(),
                status(exam), exam.isShuffleQuestions(), exam.isShuffleOptions(), false, "NOT_STARTED", true);
    }

    private String status(ExamEntity exam) {
        Instant now = clock.instant();
        if (now.isBefore(exam.getWindowStartTime())) return "UPCOMING";
        if (now.isBefore(exam.getWindowEndTime())) return "AVAILABLE";
        return "COMPLETED";
    }

    private static int maximumMarks(List<QuestionEntity> selected) {
        return selected.stream().map(QuestionEntity::getPositiveMarks).reduce(BigDecimal.ZERO, BigDecimal::add).intValueExact();
    }

    private static void validateSchedule(Instant start, Instant end, int durationMinutes) {
        if (!end.isAfter(start) || durationMinutes <= 0 || Duration.between(start, end).toMinutes() < durationMinutes) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXAM_SCHEDULE_INVALID", "Exam schedule or duration is invalid");
        }
    }

    private static String clean(String value) { return value.trim(); }
    private static ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    private static ApiException notFound(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }
}
