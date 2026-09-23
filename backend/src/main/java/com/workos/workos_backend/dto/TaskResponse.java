package com.workos.workos_backend.dto;

import java.util.List;

import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.Task;

/**
 * Matches the frontend's {@code Task} type exactly (workos-app/src/lib/types.ts).
 * Optional array fields ({@code assigneeIds}, {@code subtasks},
 * {@code attachments}) are always serialized as arrays (empty when none)
 * rather than omitted, and optional scalar fields ({@code tag},
 * {@code blocker}, {@code note}, {@code dependsOn}, {@code updatedAt}) as
 * {@code null} when unset — the frontend already treats both forms
 * identically via {@code ??}/optional-chaining throughout store.tsx and its
 * components, so this is a safe normalization, not a contract change.
 */
public record TaskResponse(
        String id,
        String title,
        String group,
        String status,
        String ownerId,
        List<String> assigneeIds,
        String tag,
        int priority,
        String dueDate,
        String start,
        String end,
        int progress,
        List<SubtaskResponse> subtasks,
        List<AttachmentResponse> attachments,
        String blocker,
        String note,
        String dependsOn,
        Long updatedAt) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getGroup().getValue(),
                task.getStatus().getValue(),
                task.getOwner().getId(),
                task.getAssignees().stream().map(Person::getId).toList(),
                task.getTag(),
                task.getPriority(),
                task.getDueDate(),
                task.getStart().toString(),
                task.getEnd().toString(),
                task.getProgress(),
                task.getSubtasks().stream().map(SubtaskResponse::from).toList(),
                task.getAttachments().stream().map(AttachmentResponse::from).toList(),
                task.getBlocker(),
                task.getNote(),
                task.getDependsOn() != null ? task.getDependsOn().getId() : null,
                task.getUpdatedAt());
    }
}
