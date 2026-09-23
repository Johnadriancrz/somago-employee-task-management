package com.workos.workos_backend.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/tasks body: {@code { boardId: BoardId } & Omit<Task, "id">}
 * (BACKEND.md). {@code group}/{@code status} are raw strings here (not the
 * enum types), same reasoning as {@code CreateBoardRequest.icon} — an
 * unsupported value becomes a clear 400 rather than a generic JSON-parse
 * failure; TaskService converts them via {@code fromValue}.
 *
 * <p>{@code ownerId} is accepted (the frontend's {@code Omit<Task, "id">}
 * shape includes it) but never trusted — TaskService always derives the
 * real owner from the server-resolved acting Person, per the approved
 * constraint that a client can never assert its own identity.
 */
public record CreateTaskRequest(

        @NotBlank(message = "boardId is required")
        String boardId,

        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title must be 255 characters or fewer")
        String title,

        @NotBlank(message = "group is required")
        String group,

        @NotBlank(message = "status is required")
        String status,

        /** Accepted for contract-shape compatibility only — never used, see class Javadoc. */
        String ownerId,

        List<String> assigneeIds,

        @Size(max = 100, message = "tag must be 100 characters or fewer")
        String tag,

        @Min(value = 1, message = "priority must be between 1 and 5")
        @Max(value = 5, message = "priority must be between 1 and 5")
        int priority,

        @NotBlank(message = "dueDate is required")
        @Size(max = 50, message = "dueDate must be 50 characters or fewer")
        String dueDate,

        @NotBlank(message = "start is required")
        String start,

        @NotBlank(message = "end is required")
        String end,

        @Min(value = 0, message = "progress must be between 0 and 100")
        @Max(value = 100, message = "progress must be between 0 and 100")
        int progress,

        @Valid
        List<SubtaskInput> subtasks,

        @Valid
        List<AttachmentInput> attachments,

        String blocker,

        String note,

        String dependsOn) {
}
