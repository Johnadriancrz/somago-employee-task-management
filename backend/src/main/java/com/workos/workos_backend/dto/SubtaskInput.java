package com.workos.workos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One entry of {@code Task.subtasks} in a create/update request body, matching
 * the frontend's {@code Subtask} type exactly (workos-app/src/lib/types.ts).
 * {@code id} is client-assigned (see TaskDetailPanel.tsx's newSubtaskId()),
 * not server-generated.
 */
public record SubtaskInput(

        @NotBlank(message = "subtask id is required")
        String id,

        @NotBlank(message = "subtask title is required")
        @Size(max = 255, message = "subtask title must be 255 characters or fewer")
        String title,

        boolean done) {
}
