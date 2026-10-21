package com.assessly.attempt;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AttemptController {
    private final AttemptService service;

    public AttemptController(AttemptService service) { this.service = service; }

    @PostMapping("/student/exams/{examId}/start")
    @PreAuthorize("hasRole('CANDIDATE')")
    public AttemptDtos.AttemptResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID examId) {
        return service.start(userId(jwt), examId);
    }

    @PostMapping("/student/exams/{examId}/action")
    @PreAuthorize("hasRole('CANDIDATE')")
    public AttemptDtos.ActionResponse action(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID examId,
                                             @Valid @RequestBody AttemptDtos.ActionRequest request) {
        return service.action(userId(jwt), examId, request);
    }

    @PostMapping("/student/exams/{examId}/submit")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('CANDIDATE')")
    public AttemptDtos.SubmissionReceipt submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID examId) {
        return service.submit(userId(jwt), examId);
    }

    @GetMapping("/student/exams/{examId}/result")
    @PreAuthorize("hasRole('CANDIDATE')")
    public AttemptDtos.ResultResponse result(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID examId) {
        return service.getResult(userId(jwt), examId);
    }

    @PostMapping("/admin/exams/{examId}/publish-results")
    @PreAuthorize("hasRole('ADMIN')")
    public AttemptDtos.PublicationResponse publishResults(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID examId) {
        return service.publishResults(userId(jwt), examId);
    }

    private static UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
