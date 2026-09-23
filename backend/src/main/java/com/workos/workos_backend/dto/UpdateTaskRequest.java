package com.workos.workos_backend.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/tasks/:taskId body: {@code Partial<Task>} (BACKEND.md) — a
 * merge-patch. Any field left out of the request JSON is deserialized as
 * {@code null} here and left unchanged by TaskService, same convention as
 * {@code UpdateBoardRequest}. There is no supported way to explicitly clear
 * {@code tag}/{@code blocker}/{@code note} back to unset by sending
 * {@code null} (indistinguishable from omitting the key) — but sending an
 * empty string for any of them does clear it, since {@code ""} is a real,
 * non-null value distinct from an omitted key.
 *
 * <p>Field-level permission enforcement (owner-only "core" fields vs.
 * owner-or-assignee "progress" fields, per workos-app/src/lib/permissions.ts
 * and BACKEND.md's Phase 2C task-permission rules) happens in
 * {@code TaskService}, not here — this record only describes the wire shape.
 */
public record UpdateTaskRequest(

        @Size(max = 255, message = "title must be 255 characters or fewer")
        String title,

        String group,

        String status,

        String ownerId,

        List<String> assigneeIds,

        @Size(max = 100, message = "tag must be 100 characters or fewer")
        String tag,

        @Min(value = 1, message = "priority must be between 1 and 5")
        @Max(value = 5, message = "priority must be between 1 and 5")
        Integer priority,

        @Size(max = 50, message = "dueDate must be 50 characters or fewer")
        String dueDate,

        String start,

        String end,

        @Min(value = 0, message = "progress must be between 0 and 100")
        @Max(value = 100, message = "progress must be between 0 and 100")
        Integer progress,

        @Valid
        List<SubtaskInput> subtasks,

        @Valid
        List<AttachmentInput> attachments,

        String blocker,

        String note,

        String dependsOn,

        Long updatedAt) {
}
