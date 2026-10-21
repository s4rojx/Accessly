package com.assessly.exam;

import com.assessly.AbstractIntegrationTest;
import com.assessly.auth.AuthService;
import com.assessly.auth.LoginRequest;
import com.assessly.auth.SignupRequest;
import com.assessly.user.AuthRole;
import com.assessly.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExamQuestionIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository users;

    @Test
    void administratorCreatesPublishesAndRosterRestrictsCandidateSafeExam() throws Exception {
        String adminEmail = "admin-phase2@example.com";
        if (!users.existsByRole(AuthRole.ROLE_ADMIN)) authService.bootstrapAdmin(adminEmail, "Phase Two Admin", "admin-password-123");
        var adminToken = authService.login(new LoginRequest(adminEmail, "admin-password-123")).response().accessToken();
        var candidate = authService.signup(new SignupRequest("Phase Two Candidate", "candidate-phase2@example.com", "P2-001", "candidate-password-123"));

        String questionBody = """
                {"type":"MCQ_SINGLE","category":"Spring Boot","difficulty":"MEDIUM","prompt":"Which feature exposes health information?","options":[{"index":0,"text":"Actuator"},{"index":1,"text":"Batch"}],"correctOptionIndices":[0],"positiveMarks":2.0,"negativeMarks":0.5}
                """;
        String questionResponse = mockMvc.perform(post("/api/v1/admin/questions").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(questionBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.correctOptionIndices[0]").value(0)).andReturn().getResponse().getContentAsString();
        String questionId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(questionResponse).get("id").asText();
        String start = Instant.now().plus(1, ChronoUnit.HOURS).toString();
        String end = Instant.now().plus(4, ChronoUnit.HOURS).toString();
        String examBody = """
                {"title":"Phase Two Exam","subject":"Spring Boot","description":"Contract test exam","windowStartTime":"%s","windowEndTime":"%s","durationMinutes":60,"questionIds":["%s"],"shuffleQuestions":true,"shuffleOptions":true}
                """.formatted(start, end, questionId);
        String examResponse = mockMvc.perform(post("/api/v1/admin/exams").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(examBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.maximumMarks").value(2)).andReturn().getResponse().getContentAsString();
        String examId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(examResponse).get("id").asText();

        mockMvc.perform(put("/api/v1/admin/exams/" + examId + "/roster").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"candidateIds\":[\"" + candidate.id() + "\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/exams/" + examId + "/publish").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));

        var candidateToken = authService.login(new LoginRequest("candidate-phase2@example.com", "candidate-password-123")).response().accessToken();
        mockMvc.perform(get("/api/v1/student/exams/" + examId).header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions[0].correctOptionIndices").doesNotExist())
                .andExpect(jsonPath("$.questions[0].positiveMarks").doesNotExist())
                .andExpect(jsonPath("$.questions[0].negativeMarks").doesNotExist())
                .andExpect(jsonPath("$.questions[0].options[0].index").value(0));
    }

    @Test
    void candidateCannotCreateQuestionsOrAccessAdminData() throws Exception {
        String email = "candidate-isolation@example.com";
        authService.signup(new SignupRequest("Candidate", email, "P2-002", "candidate-password-123"));
        var token = authService.login(new LoginRequest(email, "candidate-password-123")).response().accessToken();
        mockMvc.perform(get("/api/v1/admin/questions").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
