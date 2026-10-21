package com.assessly.attempt;

import com.assessly.exam.ExamCandidateRepository;
import com.assessly.exam.ExamEntity;
import com.assessly.exam.ExamQuestionEntity;
import com.assessly.exam.ExamQuestionRepository;
import com.assessly.exam.ExamRepository;
import com.assessly.infrastructure.redis.RedisAttemptStateService;
import com.assessly.infrastructure.redis.SessionLockService;
import com.assessly.question.QuestionDtos;
import com.assessly.question.QuestionEntity;
import com.assessly.question.QuestionRepository;
import com.assessly.result.ResultEntity;
import com.assessly.result.ResultRepository;
import com.assessly.shared.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(AttemptService.class);
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
    private final RedisAttemptStateService redisState;
    private final SessionLockService sessionLock;
    private final Clock clock = Clock.systemUTC();

    public AttemptService(AttemptRepository attempts, AttemptQuestionRepository attemptQuestions,
                          AttemptAnswerRepository answers, ExamRepository exams,
                          ExamCandidateRepository roster, ExamQuestionRepository examQuestions,
                          QuestionRepository questions, ResultRepository results, ObjectMapper objectMapper,
                          RedisAttemptStateService redisState, SessionLockService sessionLock) {
        this.attempts = attempts;
        this.attemptQuestions = attemptQuestions;
        this.answers = answers;
        this.exams = exams;
        this.roster = roster;
        this.examQuestions = examQuestions;
        this.questions = questions;
        this.results = results;
        this.objectMapper = objectMapper;
        this.redisState = redisState;
        this.sessionLock = sessionLock;
    }

    // ── Start / Resume ────────────────────────────────────────────────────────

    @Transactional
    public AttemptDtos.AttemptResponse start(UUID candidateId, UUID examId) {
        ExamEntity exam = candidateExam(candidateId, examId);
        roster.findByExamIdAndCandidateIdForUpdate(examId, candidateId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ROSTER_ACCESS_DENIED", "You are not enrolled in this examination"));
        Instant now = clock.instant();

        AttemptEntity existing = attempts.findByExamIdAndCandidateId(examId, candidateId).orElse(null);
        if (existing != null) {
            if ("IN_PROGRESS".equals(existing.getStatus()) && !now.isBefore(existing.getEffectiveEndTime())) {
                expireAndGrade(existing, now);
            }
            // Resume path: re-seed Redis if evicted
            if ("IN_PROGRESS".equals(existing.getStatus())) {
                ensureRedisSeeded(existing, exam);
            }
            return toResponse(existing);
        }

        // Phase 4: single-session enforcement — only for first-time starts (not resumes)
        // Placed after the existing-attempt check so concurrent starts for the same candidate
        // safely race on the roster FOR UPDATE lock and converge on the one attempt row,
        // without the session lock rejecting the second concurrent start call.
        String sessionId = UUID.randomUUID().toString();
        long jwtTtlSeconds = 900L; // mirrors default access-token TTL (PT15M)
        if (!sessionLock.acquireSession(candidateId, sessionId, jwtTtlSeconds)) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_CONFLICT",
                    "Another active session is already in progress for this account");
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

        // Phase 4: seed attempt state into Redis after PG commit
        redisState.seedAttempt(attempt.getId(), examId, candidateId,
                effectiveEnd, now, exam.getWindowEndTime());

        return toResponse(attempt);
    }

    // ── Action (Redis hot path) ───────────────────────────────────────────────

    /**
     * Processes a candidate question action.
     *
     * Phase 4 write path:
     * 1. Validate attempt exists in PG (lightweight read, no FOR UPDATE lock).
     * 2. Validate question membership and action/option legality.
     * 3. Execute save_question_action.lua against Redis (atomic — no PG write on hot path).
     * 4. On ATTEMPT_NOT_FOUND: trigger transparent re-hydration and retry once.
     * 5. On EXAM_TIME_EXPIRED: expire the attempt in PG and report 409 ATTEMPT_EXPIRED.
     * 6. On IGNORED_STALE_PACKET: return the last-accepted PG state idempotently.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AttemptDtos.ActionResponse action(UUID candidateId, UUID examId, AttemptDtos.ActionRequest request) {
        candidateExam(candidateId, examId);
        // Phase 4: lightweight PG read without FOR UPDATE — Redis Lua is the concurrency gate
        AttemptEntity attempt = attempts.findByExamIdAndCandidateId(examId, candidateId)
                .orElseThrow(() -> notFound("ATTEMPT_NOT_STARTED", "No attempt has been started"));
        Instant now = clock.instant();

        if (!"IN_PROGRESS".equals(attempt.getStatus())) {
            throw conflict("ATTEMPT_CLOSED", "This attempt is no longer accepting actions");
        }
        // Bug 2 fix: check the PG-sourced wall-clock deadline first.
        // Tests manipulate effective_end_time via JDBC; the Redis effectiveEndEpoch is seeded
        // at /start and will not reflect that manipulation. The Java check catches both paths.
        if (!now.isBefore(attempt.getEffectiveEndTime())) {
            AttemptEntity locked = lockedAttempt(candidateId, examId);
            expireAndGrade(locked, now);
            throw conflict("ATTEMPT_EXPIRED", "The examination time has expired");
        }
        AttemptQuestionEntity paperQuestion = attemptQuestions.findById(new AttemptQuestionId(attempt.getId(), request.questionId()))
                .orElseThrow(() -> notFound("QUESTION_NOT_IN_ATTEMPT", "Question is not part of this attempt"));
        validateAction(request.action(), request.displayedOptionIndices(), paperQuestion, questionFor(paperQuestion));

        // Compute new heatmap state and canonical selections (same logic as Phase 3)
        AttemptAnswerEntity pgCurrent = answers.findById(new AttemptAnswerId(attempt.getId(), request.questionId())).orElse(null);
        List<Integer> selected = pgCurrent != null && "VISIT_QUESTION".equals(request.action())
                ? readIndices(pgCurrent.getSelectedOptionIndices())
                : List.copyOf(request.displayedOptionIndices().stream().distinct().sorted().toList());
        HeatmapState state = nextState(request.action(), selected, pgCurrent);
        String selectedJson = write(selected);

        // Phase 4: delegate mutation to Redis Lua script (no PG write on this path)
        String redisResult = executeRedisAction(attempt, examId, candidateId, request, state, selectedJson, now);

        if (RedisAttemptStateService.ERR_EXAM_TIME_EXPIRED.equals(redisResult)) {
            // Redis server clock says time is up — expire in PG and report conflict
            AttemptEntity locked = lockedAttempt(candidateId, examId);
            expireAndGrade(locked, now);
            throw conflict("ATTEMPT_EXPIRED", "The examination time has expired");
        }
        if (RedisAttemptStateService.ERR_ATTEMPT_ALREADY_SUBMITTED.equals(redisResult)) {
            throw conflict("ATTEMPT_CLOSED", "This attempt is no longer accepting actions");
        }
        if (RedisAttemptStateService.IGNORED_STALE_PACKET.equals(redisResult)) {
            // The Lua script uses <= for sequence comparison, so IGNORED_STALE_PACKET covers:
            //   (a) clientSeq == accepted.seq → idempotent resend → return 200 with state
            //   (b) clientSeq <  accepted.seq → genuine stale     → return 409 STALE_ACTION
            //
            // Distinguish (a) from (b) by comparing clientSeq against the currently-accepted
            // seq in Redis (or PG if already flushed by the periodic sync).
            long acceptedSeq = resolveAcceptedSeq(pgCurrent, examId, candidateId, request.questionId());
            if (acceptedSeq >= 0 && request.clientSeq() == acceptedSeq) {
                // (a) Exact resend — idempotent 200 with accepted state
                if (pgCurrent != null) {
                    return actionResponse(attempt, request.action(), pgCurrent);
                }
                String redisStateJson = redisState.getQuestionState(examId, candidateId, request.questionId());
                if (redisStateJson != null) {
                    return buildActionResponseFromRedisState(attempt, request.questionId(), request.action(), redisStateJson, request.clientSeq());
                }
            }
            // (b) Genuine stale packet — 409 STALE_ACTION
            throw conflict("STALE_ACTION", "A newer answer action has already been accepted");
        }

        return new AttemptDtos.ActionResponse(attempt.getId(), request.questionId(), now,
                attempt.getEffectiveEndTime(), request.action(), state, selected, request.clientSeq());
    }

    /**
     * Runs save_question_action.lua, with a single transparent re-hydration retry
     * if the attempt is absent in Redis (eviction / Redis restart).
     */
    private String executeRedisAction(AttemptEntity attempt, UUID examId, UUID candidateId,
                                       AttemptDtos.ActionRequest request, HeatmapState state,
                                       String selectedJson, Instant now) {
        String result = redisState.saveAction(examId, candidateId, request.questionId(),
                request.clientSeq(), state.name(), selectedJson, 0L);

        if (RedisAttemptStateService.ERR_ATTEMPT_NOT_FOUND.equals(result)) {
            log.info("Redis attempt absent for student {} exam {} — re-hydrating from PG", candidateId, examId);
            rehydrateFromPostgres(attempt, examId, candidateId);
            result = redisState.saveAction(examId, candidateId, request.questionId(),
                    request.clientSeq(), state.name(), selectedJson, 0L);
        }
        return result != null ? result : RedisAttemptStateService.ERR_ATTEMPT_NOT_FOUND;
    }

    /** Loads PG answer state into Redis for transparent recovery after Redis eviction or crash. */
    private void rehydrateFromPostgres(AttemptEntity attempt, UUID examId, UUID candidateId) {
        List<AttemptAnswerEntity> pgAnswers = answers.findByIdAttemptId(attempt.getId());
        Map<UUID, String> savedAnswers = new HashMap<>();
        for (AttemptAnswerEntity ans : pgAnswers) {
            savedAnswers.put(ans.getQuestionId(), buildRedisStateJsonFromPg(ans));
        }
        ExamEntity exam = exams.findById(examId)
                .orElseThrow(() -> notFound("EXAM_NOT_FOUND", "Exam not found"));
        redisState.rehydrateAttempt(attempt.getId(), examId, candidateId,
                attempt.getEffectiveEndTime(), attempt.getStartedAt(), exam.getWindowEndTime(), savedAnswers);
        log.info("Re-hydrated {} PG answers into Redis for student {} exam {}", savedAnswers.size(), candidateId, examId);
    }

    /** Serializes an AttemptAnswerEntity into the compact Redis state JSON format {h, c, t, seq}. */
    private String buildRedisStateJsonFromPg(AttemptAnswerEntity ans) {
        try {
            var node = objectMapper.createObjectNode();
            node.put("h", ans.getState().name());
            node.set("c", objectMapper.readTree(ans.getSelectedOptionIndices()));
            node.put("t", 0L);
            node.put("seq", ans.getClientSeq());
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.CONFLICT, "ASSESSMENT_DATA_INVALID", "Assessment data is invalid");
        }
    }

    // ── Submit (Redis → PG reconciliation) ───────────────────────────────────

    /**
     * Finalizes the exam attempt.
     *
     * Phase 4 path:
     * 1. Acquire PG FOR UPDATE lock on the attempt row (idempotency gate).
     * 2. Execute lock_final_submit.lua — atomically flip Redis status + harvest all answers.
     * 3. Reconcile harvested Redis answers → PostgreSQL attempt_answers (bulk upsert).
     * 4. Mark attempt SUBMITTED in PG and trigger grading.
     */
    @Transactional
    public AttemptDtos.SubmissionReceipt submit(UUID candidateId, UUID examId) {
        candidateExam(candidateId, examId);
        AttemptEntity attempt = lockedAttempt(candidateId, examId);
        if (!"IN_PROGRESS".equals(attempt.getStatus())) {
            return new AttemptDtos.SubmissionReceipt(attempt.getSubmissionId(), attempt.getSubmittedAt(), attempt.getStatus());
        }
        Instant now = clock.instant();
        String finalStatus = now.isBefore(attempt.getEffectiveEndTime()) ? "SUBMITTED" : "EXPIRED";

        // Phase 4: atomic Redis status flip + harvest
        Map<UUID, String> redisAnswers = redisState.lockAndSubmit(examId, candidateId, "USER_CLICK");
        if (redisAnswers == null) {
            // Redis already shows SUBMITTED — idempotent; return existing PG receipt
            return new AttemptDtos.SubmissionReceipt(attempt.getSubmissionId(), attempt.getSubmittedAt(), attempt.getStatus());
        }

        // Reconcile Redis answers → PG attempt_answers
        reconcileAnswersToPostgres(attempt, redisAnswers);

        UUID submissionId = UUID.randomUUID();
        attempt.complete(finalStatus, now, submissionId);
        attempts.save(attempt);
        grade(attempt, now);

        sessionLock.forceReleaseSession(candidateId);
        redisState.removeFromActiveTimers(examId, candidateId);

        return new AttemptDtos.SubmissionReceipt(submissionId, now, finalStatus);
    }

    /**
     * Bulk-upserts Redis answer state into PostgreSQL attempt_answers.
     * Converts the compact Redis JSON format {h, c, t, seq} into the canonical PG schema.
     * Called after lock_final_submit.lua and during the 30-second periodic sync.
     */
    private void reconcileAnswersToPostgres(AttemptEntity attempt, Map<UUID, String> redisAnswers) {
        if (redisAnswers.isEmpty()) return;
        List<AttemptQuestionEntity> paper = attemptQuestions.findByIdAttemptIdOrderBySequenceNumber(attempt.getId());
        Map<UUID, AttemptQuestionEntity> paperByQuestion = new HashMap<>();
        for (AttemptQuestionEntity pq : paper) paperByQuestion.put(pq.getQuestionId(), pq);

        Instant now = clock.instant();
        for (Map.Entry<UUID, String> entry : redisAnswers.entrySet()) {
            UUID questionId = entry.getKey();
            String stateJson = entry.getValue();
            AttemptQuestionEntity pq = paperByQuestion.get(questionId);
            if (pq == null) continue;
            try {
                JsonNode node = objectMapper.readTree(stateJson);
                String heatmap = node.path("h").asText("NOT_VISITED");
                if ("NOT_VISITED".equals(heatmap)) continue;
                JsonNode choicesNode = node.path("c");
                List<Integer> selectedDisplayed = new ArrayList<>();
                if (choicesNode.isArray()) {
                    for (JsonNode idx : choicesNode) selectedDisplayed.add(idx.asInt());
                }
                long clientSeq = node.path("seq").asLong(0);

                // Map displayed indices back to canonical option indices (Phase 3 PG schema)
                List<Integer> displayedOrder = readIndices(pq.getDisplayedOptionIndices());
                List<Integer> canonicalSelected = selectedDisplayed.stream()
                        .filter(i -> i >= 0 && i < displayedOrder.size())
                        .map(displayedOrder::get)
                        .distinct().sorted().toList();

                HeatmapState state = HeatmapState.valueOf(heatmap);
                AttemptAnswerId answerId = new AttemptAnswerId(attempt.getId(), questionId);
                AttemptAnswerEntity existing = answers.findById(answerId).orElse(null);
                if (existing == null) {
                    answers.save(new AttemptAnswerEntity(attempt.getId(), questionId,
                            write(selectedDisplayed), state, clientSeq, now));
                } else if (clientSeq > existing.getClientSeq()) {
                    existing.update(write(selectedDisplayed), state, clientSeq, now);
                    answers.save(existing);
                }
            } catch (JsonProcessingException | IllegalArgumentException e) {
                log.warn("Skipping unparseable Redis state for question {}: {}", questionId, e.getMessage());
            }
        }
    }

    // ── Timer-Reaper Submission ───────────────────────────────────────────────

    /**
     * Server-side forced submission for a student whose timer has expired.
     * Called by ExamTimerReaperService — each student gets its own @Transactional boundary.
     */
    @Transactional
    public void timerReaperSubmit(UUID candidateId, UUID examId) {
        AttemptEntity attempt = attempts.findByExamIdAndCandidateId(examId, candidateId).orElse(null);
        if (attempt == null || !"IN_PROGRESS".equals(attempt.getStatus())) return;
        Instant now = clock.instant();

        Map<UUID, String> redisAnswers = redisState.lockAndSubmit(examId, candidateId, "TIMER_REAPER");
        if (redisAnswers == null) return; // user beat the reaper — already submitted

        reconcileAnswersToPostgres(attempt, redisAnswers);

        UUID submissionId = UUID.randomUUID();
        attempt.complete("EXPIRED", now, submissionId);
        attempts.save(attempt);
        grade(attempt, now);

        sessionLock.forceReleaseSession(candidateId);
        redisState.removeFromActiveTimers(examId, candidateId);
        log.info("Timer reaper submitted attempt for student {} exam {}", candidateId, examId);
    }

    // ── Periodic PG Sync (Decision A: 30s flush) ─────────────────────────────

    /**
     * Non-destructive flush of current Redis state to PostgreSQL.
     * Does NOT modify Redis status — allows the attempt to continue.
     * Called by AttemptStateSyncService every 30 seconds.
     */
    @Transactional
    public void syncRedisStateToPg(UUID candidateId, UUID examId) {
        AttemptEntity attempt = attempts.findByExamIdAndCandidateId(examId, candidateId).orElse(null);
        if (attempt == null || !"IN_PROGRESS".equals(attempt.getStatus())) return;
        if (!redisState.attemptExists(examId, candidateId)) return;

        Map<UUID, String> currentState = redisState.readCurrentState(examId, candidateId);
        if (currentState.isEmpty()) return;
        reconcileAnswersToPostgres(attempt, currentState);
        log.debug("Periodic sync: flushed {} answers for student {} exam {}", currentState.size(), candidateId, examId);
    }

    // ── Result Retrieval & Publication ────────────────────────────────────────

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
        if (!now.isAfter(exam.getWindowEndTime())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXAM_NOT_CONCLUDED", "Results can be published only after the examination window ends");
        }
        for (AttemptEntity attempt : attempts.findByExamIdAndStatus(examId, "IN_PROGRESS")) {
            expireAndGrade(attempt, now);
        }
        List<ResultEntity> examResults = results.findByExamId(examId);
        int newlyPublished = (int) examResults.stream().filter(result -> !result.isPublished()).peek(result -> result.publish(now)).count();
        results.saveAll(examResults);
        return new AttemptDtos.PublicationResponse(examId, newlyPublished, now);
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Ensures a resuming attempt is live in Redis.
     * If Redis evicted the state, re-hydrates from PG transparently.
     */
    private void ensureRedisSeeded(AttemptEntity attempt, ExamEntity exam) {
        if (!redisState.attemptExists(attempt.getExamId(), attempt.getCandidateId())) {
            rehydrateFromPostgres(attempt, attempt.getExamId(), attempt.getCandidateId());
        }
    }

    private AttemptEntity lockedAttempt(UUID candidateId, UUID examId) {
        return attempts.findByExamIdAndCandidateIdForUpdate(examId, candidateId)
                .orElseThrow(() -> notFound("ATTEMPT_NOT_STARTED", "No attempt has been started"));
    }

    private AttemptDtos.AttemptResponse toResponse(AttemptEntity attempt) {
        List<AttemptQuestionEntity> paper = attemptQuestions.findByIdAttemptIdOrderBySequenceNumber(attempt.getId());
        UUID examId = attempt.getExamId();

        // Cache-aside: try to load question metadata from Redis public question cache.
        // Falls back to PostgreSQL per-question if the prewarm cache is cold.
        Map<UUID, String> cachedPublicQuestions = new HashMap<>();
        Map<Object, Object> redisPublicEntries = null;
        try {
            redisPublicEntries = redisState.getExamPublicQuestions(examId);
        } catch (Exception ex) {
            log.debug("Redis public question cache unavailable for exam {}: {}", examId, ex.getMessage());
        }
        if (redisPublicEntries != null && !redisPublicEntries.isEmpty()) {
            for (Map.Entry<Object, Object> e : redisPublicEntries.entrySet()) {
                try {
                    cachedPublicQuestions.put(UUID.fromString(e.getKey().toString()), e.getValue().toString());
                } catch (IllegalArgumentException ignored) { }
            }
        }

        Map<UUID, AttemptAnswerEntity> answerMap = answers.findByIdAttemptId(attempt.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(AttemptAnswerEntity::getQuestionId, answer -> answer));
        List<AttemptDtos.AttemptQuestionResponse> response = paper.stream().map(paperQuestion -> {
            UUID questionId = paperQuestion.getQuestionId();
            String cachedJson = cachedPublicQuestions.get(questionId);
            AttemptAnswerEntity answer = answerMap.get(questionId);
            HeatmapState state = answer == null ? HeatmapState.NOT_VISITED : answer.getState();
            List<Integer> selected = answer == null ? List.of() : readIndices(answer.getSelectedOptionIndices());

            if (cachedJson != null) {
                // Cache hit: parse public question JSON from Redis
                try {
                    JsonNode node = objectMapper.readTree(cachedJson);
                    String type = node.path("type").asText();
                    String category = node.path("category").asText();
                    String difficulty = node.path("difficulty").asText();
                    String prompt = node.path("prompt").asText();
                    // CRITICAL: options from cache are canonical; apply displayed-order shuffle from paper
                    List<QuestionDtos.OptionResponse> canonicalOptions = objectMapper.readValue(
                            objectMapper.writeValueAsString(node.path("options")), OPTIONS);
                    Map<Integer, String> textByIndex = canonicalOptions.stream()
                            .collect(java.util.stream.Collectors.toMap(QuestionDtos.OptionResponse::index, QuestionDtos.OptionResponse::text));
                    List<Integer> order = readIndices(paperQuestion.getDisplayedOptionIndices());
                    List<QuestionDtos.OptionResponse> displayedOpts = new ArrayList<>();
                    for (int position = 0; position < order.size(); position++) {
                        String text = textByIndex.get(order.get(position));
                        if (text == null) throw new ApiException(HttpStatus.CONFLICT, "EXAM_QUESTION_INVALID", "The examination question is invalid");
                        displayedOpts.add(new QuestionDtos.OptionResponse(position, text));
                    }
                    return new AttemptDtos.AttemptQuestionResponse(questionId, type, category,
                            difficulty, prompt, displayedOpts, state, selected, answer == null ? 0 : answer.getClientSeq());
                } catch (JsonProcessingException e) {
                    log.debug("Failed to parse cached question JSON for {}, falling back to PG", questionId);
                    // fall through to PG path below
                }
            }

            // Cache miss (or parse failure): fall back to PostgreSQL
            QuestionEntity question = questions.findById(questionId)
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "EXAM_QUESTION_UNAVAILABLE", "The examination question is unavailable"));
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

    /**
     * Resolves the currently-accepted clientSeq for a question, consulting PG first
     * (if the answer was already flushed by the periodic sync) and then Redis state.
     * Returns -1 if the accepted seq cannot be determined.
     */
    private long resolveAcceptedSeq(AttemptAnswerEntity pgCurrent, UUID examId, UUID candidateId, UUID questionId) {
        if (pgCurrent != null) {
            return pgCurrent.getClientSeq();
        }
        String redisStateJson = redisState.getQuestionState(examId, candidateId, questionId);
        if (redisStateJson != null) {
            try {
                return objectMapper.readTree(redisStateJson).path("seq").asLong(-1);
            } catch (JsonProcessingException ignored) { }
        }
        return -1L;
    }

    /**
     * Builds an ActionResponse from a Redis state JSON blob when the answer has not
     * yet been flushed to PostgreSQL (the IGNORED_STALE_PACKET idempotent resend path).
     * Parses {h, c, seq} from the compact Redis format {"h":...,"c":[...],"t":...,"seq":...}.
     */
    private AttemptDtos.ActionResponse buildActionResponseFromRedisState(
            AttemptEntity attempt, UUID questionId, String action, String redisStateJson, long clientSeq) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(redisStateJson);
            HeatmapState state = HeatmapState.valueOf(node.path("h").asText("NOT_VISITED"));
            com.fasterxml.jackson.databind.JsonNode choicesNode = node.path("c");
            List<Integer> selected = new ArrayList<>();
            if (choicesNode.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode idx : choicesNode) selected.add(idx.asInt());
            }
            long acceptedSeq = node.path("seq").asLong(clientSeq);
            return new AttemptDtos.ActionResponse(attempt.getId(), questionId, clock.instant(),
                    attempt.getEffectiveEndTime(), action, state, selected, acceptedSeq);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw conflict("STALE_ACTION", "A newer answer action has already been accepted");
        }
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
            Set<Integer> selectedCanonical = selectedDisplayed.stream()
                    .map(index -> readIndices(paperQuestion.getDisplayedOptionIndices()).get(index))
                    .collect(java.util.stream.Collectors.toSet());
            Set<Integer> correctCanonical = new HashSet<>(readIndices(question.getCorrectOptionIndices()));
            if (selectedCanonical.equals(correctCanonical)) {
                correct++;
                total = total.add(question.getPositiveMarks());
            } else {
                incorrect++;
                total = total.subtract(question.getNegativeMarks());
            }
        }
        BigDecimal percentage = maximum.signum() == 0 ? BigDecimal.ZERO
                : total.multiply(BigDecimal.valueOf(100)).divide(maximum, 2, RoundingMode.HALF_UP);
        long elapsedSeconds = Math.max(0, Math.min(
                Duration.between(attempt.getStartedAt(), submittedAt).getSeconds(),
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
        catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "ASSESSMENT_DATA_INVALID", "Assessment data is invalid");
        }
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ASSESSMENT_SERIALIZATION_FAILED", "Assessment data could not be saved");
        }
    }

    private AttemptDtos.ResultResponse toResult(ResultEntity result) {
        return new AttemptDtos.ResultResponse(result.getId(), result.getExamId(), result.getSubmittedAt(), result.getTotalMarks(),
                result.getMaximumMarks(), result.getPercentage(), result.getCorrectCount(), result.getIncorrectCount(),
                result.getUnattemptedCount(), result.getTimeTakenMinutes(), result.isPublished());
    }

    private static ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    private static ApiException notFound(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }
}
