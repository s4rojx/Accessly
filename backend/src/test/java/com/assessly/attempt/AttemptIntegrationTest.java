package com.assessly.attempt;

import com.assessly.AbstractIntegrationTest;
import com.assessly.auth.AuthService;
import com.assessly.auth.LoginRequest;
import com.assessly.auth.SignupRequest;
import com.assessly.result.ResultEntity;
import com.assessly.result.ResultRepository;
import com.assessly.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttemptIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired AttemptService attemptService;
    @Autowired AttemptRepository attempts;
    @Autowired ResultRepository results;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository users;

    @Test
    void persistsStateTransitionsGradesAndBlocksUnpublishedResults() throws Exception {
        Fixture fixture = fixture("attempt-lifecycle");
        String attempt = mockMvc.perform(post("/api/v1/student/exams/" + fixture.examId() + "/start")
                        .header("Authorization", "Bearer " + fixture.candidateToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.serverTime").exists())
                .andExpect(jsonPath("$.effectiveEndTime").exists())
                .andExpect(jsonPath("$.questions[0].state").value("NOT_VISITED"))
                .andExpect(jsonPath("$.questions[0].correctOptionIndices").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String questionId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(attempt).get("questions").get(0).get("id").asText();

        action(fixture, questionId, "VISIT_QUESTION", "[]", 1)
                .andExpect(jsonPath("$.state").value("NOT_ANSWERED"));
        action(fixture, questionId, "SAVE_AND_NEXT", "[1]", 2)
                .andExpect(jsonPath("$.state").value("ANSWERED"));
        action(fixture, questionId, "MARK_REVIEW_AND_NEXT", "[1]", 3)
                .andExpect(jsonPath("$.state").value("ANSWERED_AND_MARKED_FOR_REVIEW"));
        action(fixture, questionId, "MARK_REVIEW_AND_NEXT", "[1]", 3)
                .andExpect(jsonPath("$.state").value("ANSWERED_AND_MARKED_FOR_REVIEW"));
        action(fixture, questionId, "CLEAR_RESPONSE", "[]", 4)
                .andExpect(jsonPath("$.state").value("NOT_ANSWERED"));
        action(fixture, questionId, "MARK_REVIEW_AND_NEXT", "[]", 5)
                .andExpect(jsonPath("$.state").value("MARKED_FOR_REVIEW"));
        action(fixture, questionId, "SAVE_AND_NEXT", "[1]", 6)
                .andExpect(jsonPath("$.state").value("ANSWERED"));
        mockMvc.perform(post("/api/v1/student/exams/" + fixture.examId() + "/action")
                        .header("Authorization", "Bearer " + fixture.candidateToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SAVE_AND_NEXT\",\"questionId\":\"" + questionId + "\",\"displayedOptionIndices\":[0],\"clientSeq\":2}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_ACTION"));
        mockMvc.perform(get("/api/v1/student/exams/" + fixture.examId() + "/result")
                        .header("Authorization", "Bearer " + fixture.candidateToken()))
                .andExpect(status().isNotFound());

        String submission = mockMvc.perform(post("/api/v1/student/exams/" + fixture.examId() + "/submit")
                        .header("Authorization", "Bearer " + fixture.candidateToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submissionId").exists()).andReturn().getResponse().getContentAsString();
        String submissionId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(submission).get("submissionId").asText();
        mockMvc.perform(post("/api/v1/student/exams/" + fixture.examId() + "/submit")
                        .header("Authorization", "Bearer " + fixture.candidateToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.submissionId").value(submissionId));
        mockMvc.perform(get("/api/v1/student/exams/" + fixture.examId() + "/result")
                        .header("Authorization", "Bearer " + fixture.candidateToken()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("RESULT_NOT_PUBLISHED"));
        concludeExam(fixture.examId());
        mockMvc.perform(post("/api/v1/admin/exams/" + fixture.examId() + "/publish-results")
                        .header("Authorization", "Bearer " + fixture.adminToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publishedCount").value(1));
        mockMvc.perform(get("/api/v1/student/exams/" + fixture.examId() + "/result")
                        .header("Authorization", "Bearer " + fixture.candidateToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.correct").value(1))
                .andExpect(jsonPath("$.totalMarks").value(2.0)).andExpect(jsonPath("$.published").value(true));
    }

    @Test
    void rejectsInvalidOptionsAndEnforcesExamWindow() throws Exception {
        Fixture fixture = fixture("attempt-validation");
        String attempt = mockMvc.perform(post("/api/v1/student/exams/" + fixture.examId() + "/start")
                        .header("Authorization", "Bearer " + fixture.candidateToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String questionId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(attempt).get("questions").get(0).get("id").asText();
        mockMvc.perform(post("/api/v1/student/exams/" + fixture.examId() + "/action")
                        .header("Authorization", "Bearer " + fixture.candidateToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SAVE_AND_NEXT\",\"questionId\":\"" + questionId + "\",\"displayedOptionIndices\":[9],\"clientSeq\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OPTION_INVALID"));
    }

    @Test
    void expiredActionPersistsExpiredAttemptAndResult() throws Exception {
        Fixture fixture = fixture("attempt-expired-action");
        String attempt = mockMvc.perform(post("/api/v1/student/exams/" + fixture.examId() + "/start")
                        .header("Authorization", "Bearer " + fixture.candidateToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(attempt);
        java.util.UUID attemptId = java.util.UUID.fromString(json.get("id").asText());
        String questionId = json.get("questions").get(0).get("id").asText();
        jdbcTemplate.update("update attempts set started_at = ?, effective_end_time = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)),
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), attemptId);

        mockMvc.perform(post("/api/v1/student/exams/" + fixture.examId() + "/action")
                        .header("Authorization", "Bearer " + fixture.candidateToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"VISIT_QUESTION\",\"questionId\":\"" + questionId + "\",\"displayedOptionIndices\":[],\"clientSeq\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTEMPT_EXPIRED"));

        assertThat(attempts.findById(attemptId).orElseThrow().getStatus()).isEqualTo("EXPIRED");
        assertThat(results.findByAttemptId(attemptId)).isPresent();
    }

    @Test
    void resultPublicationWaitsForExamConclusionAndFinalizesAbandonedAttempts() throws Exception {
        Fixture openExam = fixture("publish-open");
        mockMvc.perform(post("/api/v1/admin/exams/" + openExam.examId() + "/publish-results")
                        .header("Authorization", "Bearer " + openExam.adminToken()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EXAM_NOT_CONCLUDED"));

        Fixture abandonedExam = fixture("publish-abandoned");
        mockMvc.perform(post("/api/v1/student/exams/" + abandonedExam.examId() + "/start")
                        .header("Authorization", "Bearer " + abandonedExam.candidateToken()))
                .andExpect(status().isOk());
        java.util.UUID abandonedExamId = java.util.UUID.fromString(abandonedExam.examId());
        concludeExam(abandonedExam.examId());

        mockMvc.perform(post("/api/v1/admin/exams/" + abandonedExam.examId() + "/publish-results")
                        .header("Authorization", "Bearer " + abandonedExam.adminToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publishedCount").value(1));

        AttemptEntity persistedAttempt = attempts.findByExamIdAndCandidateId(abandonedExamId, abandonedExam.candidateId()).orElseThrow();
        assertThat(persistedAttempt.getStatus()).isEqualTo("EXPIRED");
        ResultEntity persistedResult = results.findByExamIdAndCandidateId(abandonedExamId, abandonedExam.candidateId()).orElseThrow();
        assertThat(persistedResult.isPublished()).isTrue();
    }

    @Test
    void concurrentStartsReturnOneAttempt() throws Exception {
        Fixture fixture = fixture("attempt-start-concurrent");
        java.util.UUID examId = java.util.UUID.fromString(fixture.examId());
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AttemptDtos.AttemptResponse> first = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return attemptService.start(fixture.candidateId(), examId);
            });
            Future<AttemptDtos.AttemptResponse> second = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return attemptService.start(fixture.candidateId(), examId);
            });

            AttemptDtos.AttemptResponse firstAttempt = first.get(20, TimeUnit.SECONDS);
            AttemptDtos.AttemptResponse secondAttempt = second.get(20, TimeUnit.SECONDS);
            assertThat(firstAttempt.id()).isEqualTo(secondAttempt.id());
            assertThat(attempts.findByExamIdAndCandidateId(examId, fixture.candidateId())).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentSubmissionsProduceOneReceipt() throws Exception {
        Fixture fixture = fixture("attempt-concurrent");
        mockMvc.perform(post("/api/v1/student/exams/" + fixture.examId() + "/start")
                        .header("Authorization", "Bearer " + fixture.candidateToken()))
                .andExpect(status().isOk());

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AttemptDtos.SubmissionReceipt> first = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return attemptService.submit(fixture.candidateId(), java.util.UUID.fromString(fixture.examId()));
            });
            Future<AttemptDtos.SubmissionReceipt> second = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return attemptService.submit(fixture.candidateId(), java.util.UUID.fromString(fixture.examId()));
            });

            AttemptDtos.SubmissionReceipt firstReceipt = first.get(20, TimeUnit.SECONDS);
            AttemptDtos.SubmissionReceipt secondReceipt = second.get(20, TimeUnit.SECONDS);
            assertThat(firstReceipt.submissionId()).isEqualTo(secondReceipt.submissionId());
            assertThat(firstReceipt.status()).isEqualTo("SUBMITTED");
            assertThat(secondReceipt.status()).isEqualTo("SUBMITTED");
        } finally {
            executor.shutdownNow();
        }
    }

    private org.springframework.test.web.servlet.ResultActions action(Fixture fixture, String questionId, String action,
                                                                        String selected, long sequence) throws Exception {
        return mockMvc.perform(post("/api/v1/student/exams/" + fixture.examId() + "/action")
                .header("Authorization", "Bearer " + fixture.candidateToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"" + action + "\",\"questionId\":\"" + questionId + "\",\"displayedOptionIndices\":" + selected + ",\"clientSeq\":" + sequence + "}"))
                .andExpect(status().isOk());
    }

    private void concludeExam(String examId) {
        Instant end = Instant.now().minusSeconds(1);
        Instant start = end.minus(61, ChronoUnit.MINUTES);
        jdbcTemplate.update("update exams set window_start_time = ?, window_end_time = ? where id = ?",
                java.sql.Timestamp.from(start), java.sql.Timestamp.from(end), java.util.UUID.fromString(examId));
    }

    private Fixture fixture(String suffix) throws Exception {
        String adminEmail = "admin-phase3@example.com";
        if (!users.existsByRole(com.assessly.user.AuthRole.ROLE_ADMIN)) {
            authService.bootstrapAdmin(adminEmail, "Phase Three Admin", "admin-password-123");
        }
        String adminToken = authService.login(new LoginRequest(adminEmail, "admin-password-123")).response().accessToken();
        var candidate = authService.signup(new SignupRequest("Phase Three Candidate", "candidate-" + suffix + "@example.com", "P3-" + suffix, "candidate-password-123"));
        String questionBody = "{\"type\":\"MCQ_SINGLE\",\"category\":\"Spring Boot\",\"difficulty\":\"MEDIUM\",\"prompt\":\"Which feature exposes health information?\",\"options\":[{\"index\":0,\"text\":\"Batch\"},{\"index\":1,\"text\":\"Actuator\"}],\"correctOptionIndices\":[1],\"positiveMarks\":2.0,\"negativeMarks\":0.5}";
        String question = mockMvc.perform(post("/api/v1/admin/questions").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(questionBody)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String questionId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(question).get("id").asText();
        String start = Instant.now().minus(10, ChronoUnit.MINUTES).toString();
        String end = Instant.now().plus(2, ChronoUnit.HOURS).toString();
        String exam = mockMvc.perform(post("/api/v1/admin/exams").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Phase Three Exam\",\"subject\":\"Spring Boot\",\"description\":\"Attempt lifecycle test\",\"windowStartTime\":\"" + start + "\",\"windowEndTime\":\"" + end + "\",\"durationMinutes\":60,\"questionIds\":[\"" + questionId + "\"],\"shuffleQuestions\":false,\"shuffleOptions\":false}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String examId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(exam).get("id").asText();
        mockMvc.perform(put("/api/v1/admin/exams/" + examId + "/roster").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"candidateIds\":[\"" + candidate.id() + "\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/exams/" + examId + "/publish").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        String candidateToken = authService.login(new LoginRequest("candidate-" + suffix + "@example.com", "candidate-password-123")).response().accessToken();
        return new Fixture(adminToken, candidateToken, examId, candidate.id());
    }

    private record Fixture(String adminToken, String candidateToken, String examId, java.util.UUID candidateId) { }
}
