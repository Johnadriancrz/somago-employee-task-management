package com.workos.workos_backend.dto;

import java.util.List;

import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.Task;

/**
 * GET /api/reports/completed-tasks response row — a thin projection of
 * {@link Task}, carrying only the fields the existing Reports UI (workos-app
 * {@code (app)/dashboards/page.tsx}) actually renders. Deliberately not
 * {@link TaskResponse}: subtasks, attachments, blocker/note, progress, etc.
 * are irrelevant to a completed-task report row and would leak more of the
 * task than Reports needs.
 */
public record CompletedTaskResponse(
        String taskId,
        String title,
        String boardId,
        String boardName,
        String ownerId,
        List<String> assigneeIds,
        String dueDate,
        String end) {

    public static CompletedTaskResponse from(Task task) {
        return new CompletedTaskResponse(
                task.getId(),
                task.getTitle(),
                task.getBoard().getId(),
                task.getBoard().getName(),
                task.getOwner().getId(),
                task.getAssignees().stream().map(Person::getId).toList(),
                task.getDueDate(),
                task.getEnd().toString());
    }
}
