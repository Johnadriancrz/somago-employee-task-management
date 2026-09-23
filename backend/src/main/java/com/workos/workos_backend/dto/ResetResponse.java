package com.workos.workos_backend.dto;

import java.util.List;
import java.util.Map;

/**
 * Response shape for {@code POST /api/reset} (BACKEND.md's Reset table),
 * matching the Next.js route handler's actual return value exactly
 * (workos-app/src/app/api/reset/route.ts): workspaces, boards, and
 * tasksByBoard per BACKEND.md's documented type, plus people — the route
 * also resets and returns People, even though BACKEND.md's table type
 * omits it from the response shape.
 */
public record ResetResponse(
        List<WorkspaceResponse> workspaces,
        List<BoardResponse> boards,
        Map<String, List<TaskResponse>> tasksByBoard,
        List<PersonResponse> people) {
}
