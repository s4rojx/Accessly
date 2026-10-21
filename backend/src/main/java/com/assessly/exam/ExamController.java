package com.assessly.exam;

import com.assessly.infrastructure.redis.ExamPreWarmerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ExamController {
    private final ExamService service;
    private final ExamPreWarmerService preWarmer;

    public ExamController(ExamService service, ExamPreWarmerService preWarmer) {
        this.service = service;
        this.preWarmer = preWarmer;
    }

    @GetMapping("/admin/exams")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ExamDtos.AdminResponse> listAdmin(@AuthenticationPrincipal Jwt jwt) { return service.listAdmin(userId(jwt)); }

    @GetMapping("/admin/exams/{examId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ExamDtos.AdminResponse getAdmin(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID examId) { return service.getAdmin(userId(jwt), examId); }

    @PostMapping("/admin/exams")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ExamDtos.AdminResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ExamDtos.UpsertRequest request) { return service.create(userId(jwt), request); }

    @PutMapping("/admin/exams/{examId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ExamDtos.AdminResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID examId, @Valid @RequestBody ExamDtos.UpsertRequest request) { return service.update(userId(jwt), examId, request); }

    @PostMapping("/admin/exams/{examId}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ExamDtos.AdminResponse publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID examId) { return service.publish(userId(jwt), examId); }

    /** Redis Phase 4: Pre-warms exam question cache. Safe to call multiple times (idempotent). */
    @PostMapping("/admin/exams/{examId}/prewarm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void prewarm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID examId) {
        preWarmer.prewarmExam(examId);
    }

    @GetMapping("/admin/exams/{examId}/roster")
    @PreAuthorize("hasRole('ADMIN')")
    public ExamDtos.RosterResponse getRoster(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID examId) { return service.getRoster(userId(jwt), examId); }

    @PutMapping("/admin/exams/{examId}/roster")
    @PreAuthorize("hasRole('ADMIN')")
    public ExamDtos.RosterResponse replaceRoster(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID examId, @Valid @RequestBody ExamDtos.RosterRequest request) { return service.replaceRoster(userId(jwt), examId, request); }

    @GetMapping("/student/exams")
    @PreAuthorize("hasRole('CANDIDATE')")
    public List<ExamDtos.CandidateResponse> listCandidate(@AuthenticationPrincipal Jwt jwt) { return service.listCandidate(userId(jwt)); }

    @GetMapping("/student/exams/{examId}")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ExamDtos.CandidateResponse getCandidate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID examId) { return service.getCandidate(userId(jwt), examId); }

    private static UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
