package com.workos.workos_backend.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.workos.workos_backend.dto.BoardResponse;
import com.workos.workos_backend.dto.PersonResponse;
import com.workos.workos_backend.dto.ResetResponse;
import com.workos.workos_backend.dto.TaskResponse;
import com.workos.workos_backend.dto.WorkspaceResponse;
import com.workos.workos_backend.entity.Task;
import com.workos.workos_backend.service.AuthService;
import com.workos.workos_backend.service.ResetService;

/**
 * Implements the Reset endpoint from BACKEND.md ("Reset demo data" button).
 * LOCAL DEVELOPMENT ONLY — see {@link ResetService}'s Javadoc for why this is
 * scoped to the {@code local-dev} profile; outside it, no bean of this type
 * exists and the route 404s via GlobalExceptionHandler's NoHandlerFoundException
 * mapping, the same as any other unmapped path.
 */
@RestController
@RequestMapping("/api/reset")
@Profile("local-dev")
public class ResetController {

    private final ResetService resetService;
    private final AuthService authService;

    public ResetController(ResetService resetService, AuthService authService) {
        this.resetService = resetService;
        this.authService = authService;
    }

    /**
     * Requires a valid, unexpired employee session — deliberately checked
     * via {@link AuthService#currentPerson(String)} directly (same as
     * {@link AuthController#changePassword}) rather than {@code
     * ActingPersonResolver}, since the {@code local-dev}-only {@link
     * com.workos.workos_backend.actor.LocalDevActingPersonResolver} would
     * resolve a fixed identity regardless of session and defeat the check.
     * The resolved identity is intentionally unused beyond authentication —
     * reset is not scoped per-person — and is never taken from client input.
     */
    @PostMapping
    public ResetResponse reset(@CookieValue(name = AuthService.COOKIE_NAME, required = false) String token) {
        authService.currentPerson(token);
        ResetService.ResetResult result = resetService.reset();

        Map<String, List<TaskResponse>> tasksByBoard = new LinkedHashMap<>();
        for (Map.Entry<String, List<Task>> entry : result.tasksByBoard().entrySet()) {
            tasksByBoard.put(entry.getKey(), entry.getValue().stream().map(TaskResponse::from).toList());
        }

        return new ResetResponse(
                result.workspaces().stream().map(WorkspaceResponse::from).toList(),
                result.boards().stream().map(BoardResponse::from).toList(),
                tasksByBoard,
                result.people().stream().map(PersonResponse::from).toList());
    }
}
