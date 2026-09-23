package com.workos.workos_backend.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/workspaces/:workspaceId/members body: {@code { personId: string } } (BACKEND.md). */
public record AddWorkspaceMemberRequest(

        @NotBlank(message = "personId is required")
        String personId) {
}
