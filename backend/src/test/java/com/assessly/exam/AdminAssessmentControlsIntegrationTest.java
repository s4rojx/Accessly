package com.assessly.exam;

import com.assessly.AbstractIntegrationTest;
import com.assessly.auth.AuthService;
import com.assessly.auth.LoginRequest;
import com.assessly.auth.SignupRequest;
import com.assessly.user.AuthRole;
import com.assessly.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAssessmentControlsIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    @Test
    void adminCanListCandidatesAndManageDraftSettingsAndRoster() throws Exception {
        Fixture fixture = fixture("controls-roster", false, false);

        mockMvc.perform(get("/api/v1/admin/candidates").header("Authorization", bearer(fixture.adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'candidate-controls-roster@example.com')].passwordHash").doesNotExist());
        mockMvc.perform(get("/api/v1/admin/candidates").header("Authorization", bearer(fixture.candidateToken())))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/admin/exams/" + fixture.examId() + "/roster")
                        .header("Authorization", bearer(fixture.adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateIds\":[\"" + fixture.candidateId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateIds[0]").value(fixture.candidateId().toString()));
        mockMvc.perform(get("/api/v1/admin/exams/" + fixture.examId() + "/roster")
                        .header("Authorization", bearer(fixture.adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateIds[0]").value(fixture.candidateId().toString()));

        mockMvc.perform(put("/api/v1/admin/exams/" + fixture.examId())
                        .header("Authorization", bearer(fixture.adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upsert(fixture.questionId(), "Updated assessment", fixture.start(), fixture.end(), false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated assessment"));
    }

    @Test
    void publishedExamSettingsRosterAndUnauthorizedPrewarmAreProtected() throws Exception {
        Fixture fixture = fixture("controls-immutable", false, false);
        mockMvc.perform(put("/api/v1/admin/exams/" + fixture.examId() + "/roster")
                        .header("Authorization", bearer(fixture.adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateIds\":[\"" + fixture.candidateId() + "\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/exams/" + fixture.examId() + "/publish")
                        .header("Authorization", bearer(fixture.adminToken())))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/admin/exams/" + fixture.examId())
                        .header("Authorization", bearer(fixture.adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upsert(fixture.questionId(), "Should fail", fixture.start(), fixture.end(), false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PUBLISHED_EXAM_IMMUTABLE"));
        mockMvc.perform(put("/api/v1/admin/exams/" + fixture.examId() + "/roster")
                        .header("Authorization", bearer(fixture.adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateIds\":[\"" + fixture.candidateId() + "\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PUBLISHED_EXAM_ROSTER_IMMUTABLE"));
        mockMvc.perform(post("/api/v1/admin/exams/" + UUID.randomUUID() + "/prewarm")
                        .header("Authorization", bearer(fixture.adminToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXAM_NOT_FOUND"));
    }

    @Test
    void adminCanReviewAnalyticsAndPublishEligibleResultsIdempotently() throws Exception {
        Fixture fixture = fixture("controls-results", true, true);
        String attemptJson = mockMvc.perform(post("/api/v1/student/exams/" + fixture.examId() + "/start")
                        .header("Authorization", bearer(fixture.candidateToken())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode attempt = objectMapper.readTree(attemptJson);
        String questionId = attempt.get("questions").get(0).get("id").asText();
        int correctDisplayedIndex = -1;
        for (JsonNode option : attempt.get("questions").get(0).get("options")) {
            if ("Correct answer".equals(option.get("text").asText())) correctDisplayedIndex = option.get("index").asInt();
        }
        if (correctDisplayedIndex < 0) throw new AssertionError("The shuffled question did not expose its correct option");

        mockMvc.perform(post("/api/v1/student/exams/" + fixture.examId() + "/action")
                        .header("Authorization", bearer(fixture.candidateToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SAVE_AND_NEXT\",\"questionId\":\"" + questionId + "\",\"displayedOptionIndices\":[" + correctDisplayedIndex + "],\"clientSeq\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/student/exams/" + fixture.examId() + "/submit")
                        .header("Authorization", bearer(fixture.candidateToken())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/exams/" + fixture.examId() + "/results")
                        .header("Authorization", bearer(fixture.adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].percentage").value(100.0))
                .andExpect(jsonPath("$[0].published").value(false))
                .andExpect(jsonPath("$[0].eligible").value(false));
        mockMvc.perform(get("/api/v1/admin/exams/" + fixture.examId() + "/analytics")
                        .header("Authorization", bearer(fixture.adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rosterSize").value(1))
                .andExpect(jsonPath("$.gradedResults").value(1))
                .andExpect(jsonPath("$.averagePercentage").value(100.0))
                .andExpect(jsonPath("$.medianPercentage").value(100.0))
                .andExpect(jsonPath("$.scoreDistribution[4].count").value(1))
                .andExpect(jsonPath("$.questionCorrectness[0].accuracyPercentage").value(100.0))
                .andExpect(jsonPath("$.questionCorrectness[0].correctOptionIndices").doesNotExist());
        mockMvc.perform(get("/api/v1/student/exams/" + fixture.examId() + "/result")
                        .header("Authorization", bearer(fixture.candidateToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESULT_NOT_PUBLISHED"));

        concludeExam(fixture.examId());
        mockMvc.perform(get("/api/v1/admin/exams/" + fixture.examId() + "/results")
                        .header("Authorization", bearer(fixture.adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eligible").value(true));
        mockMvc.perform(post("/api/v1/admin/exams/" + fixture.examId() + "/publish-results")
                        .header("Authorization", bearer(fixture.adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedCount").value(1));
        mockMvc.perform(post("/api/v1/admin/exams/" + fixture.examId() + "/publish-results")
                        .header("Authorization", bearer(fixture.adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedCount").value(0));
        mockMvc.perform(get("/api/v1/student/exams/" + fixture.examId() + "/result")
                        .header("Authorization", bearer(fixture.candidateToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true));
    }

    private Fixture fixture(String suffix, boolean publish, boolean shuffleOptions) throws Exception {
        String adminEmail = "admin-controls@example.com";
        if (!users.existsByRole(AuthRole.ROLE_ADMIN)) authService.bootstrapAdmin(adminEmail, "Controls Admin", "admin-password-123");
        String adminToken = authService.login(new LoginRequest(adminEmail, "admin-password-123")).response().accessToken();
        String candidateEmail = "candidate-" + suffix + "@example.com";
        var candidate = authService.signup(new SignupRequest("Candidate " + suffix, candidateEmail, "CTRL-" + suffix, "candidate-password-123"));
        String questionBody = "{\"type\":\"MCQ_SINGLE\",\"category\":\"Controls\",\"difficulty\":\"MEDIUM\",\"prompt\":\"Which answer is correct?\",\"options\":[{\"index\":0,\"text\":\"Incorrect answer\"},{\"index\":1,\"text\":\"Correct answer\"}],\"correctOptionIndices\":[1],\"positiveMarks\":2.0,\"negativeMarks\":0.5}";
        String questionJson = mockMvc.perform(post("/api/v1/admin/questions").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(questionBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String questionId = objectMapper.readTree(questionJson).get("id").asText();
        String start = Instant.now().minus(10, ChronoUnit.MINUTES).toString();
        String end = Instant.now().plus(2, ChronoUnit.HOURS).toString();
        String examJson = mockMvc.perform(post("/api/v1/admin/exams").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upsert(questionId, "Controls exam " + suffix, start, end, shuffleOptions)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String examId = objectMapper.readTree(examJson).get("id").asText();
        mockMvc.perform(put("/api/v1/admin/exams/" + examId + "/roster").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateIds\":[\"" + candidate.id() + "\"]}"))
                .andExpect(status().isOk());
        if (publish) {
            mockMvc.perform(post("/api/v1/admin/exams/" + examId + "/publish").header("Authorization", bearer(adminToken)))
                    .andExpect(status().isOk());
        }
        String candidateToken = authService.login(new LoginRequest(candidateEmail, "candidate-password-123")).response().accessToken();
        return new Fixture(adminToken, candidateToken, UUID.fromString(candidate.id().toString()), examId, questionId, start, end);
    }

    private String upsert(String questionId, String title, String start, String end, boolean shuffleOptions) {
        return "{\"title\":\"" + title + "\",\"subject\":\"Computer science\",\"description\":\"Assessment controls test\",\"windowStartTime\":\"" + start + "\",\"windowEndTime\":\"" + end + "\",\"durationMinutes\":60,\"questionIds\":[\"" + questionId + "\"],\"shuffleQuestions\":false,\"shuffleOptions\":" + shuffleOptions + "}";
    }

    private void concludeExam(String examId) {
        Instant end = Instant.now().minusSeconds(1);
        Instant start = end.minus(61, ChronoUnit.MINUTES);
        jdbcTemplate.update("update exams set window_start_time = ?, window_end_time = ? where id = ?",
                java.sql.Timestamp.from(start), java.sql.Timestamp.from(end), UUID.fromString(examId));
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private record Fixture(String adminToken, String candidateToken, UUID candidateId, String examId,
                           String questionId, String start, String end) { }
}
