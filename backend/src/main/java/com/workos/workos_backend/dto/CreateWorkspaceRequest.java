package com.workos.workos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/workspaces body: {@code { name: string; initials?: string } } (BACKEND.md). */
public record CreateWorkspaceRequest(

        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be 255 characters or fewer")
        String name,

        @Size(max = 8, message = "initials must be 8 characters or fewer")
        String initials) {
}
