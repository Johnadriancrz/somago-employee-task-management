package com.workos.workos_backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.workos.workos_backend.actor.ActingPersonResolver;
import com.workos.workos_backend.dto.CompletedTaskResponse;
import com.workos.workos_backend.service.ReportService;

/**
 * GET /api/reports/completed-tasks — see BACKEND.md's Reports section. The
 * acting person always comes from {@link ActingPersonResolver} (never a
 * request parameter or body field); a missing/invalid session is rejected
 * with 401 by the same {@code ActingPersonResolver}/{@code
 * GlobalExceptionHandler} path every other authenticated endpoint uses.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportsController {

    private final ReportService reportService;
    private final ActingPersonResolver actingPersonResolver;

    public ReportsController(ReportService reportService, ActingPersonResolver actingPersonResolver) {
        this.reportService = reportService;
        this.actingPersonResolver = actingPersonResolver;
    }

    @GetMapping("/completed-tasks")
    public List<CompletedTaskResponse> completedTasks() {
        String actorId = actingPersonResolver.currentPersonId();
        return reportService.listCompletedTasks(actorId).stream().map(CompletedTaskResponse::from).toList();
    }
}
