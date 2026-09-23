package com.workos.workos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/boards body: {@code Omit<BoardMeta, "id">} with {@code workspaceId}
 * required (BACKEND.md). {@code icon} is a raw string here (not the
 * {@code BoardIcon} enum) so an unsupported value can be reported as a clear
 * 400 rather than a generic JSON-parse failure; the service layer converts
 * it via {@code BoardIcon.fromValue}.
 */
public record CreateBoardRequest(

        @NotBlank(message = "workspaceId is required")
        String workspaceId,

        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be 255 characters or fewer")
        String name,

        @Size(max = 1000, message = "description must be 1000 characters or fewer")
        String description,

        String icon) {
}
