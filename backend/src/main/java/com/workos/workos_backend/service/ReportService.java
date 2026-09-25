package com.workos.workos_backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.authorization.AccessRoleChecker;
import com.workos.workos_backend.authorization.AccessRoles;
import com.workos.workos_backend.entity.BoardMeta;
import com.workos.workos_backend.entity.Task;
import com.workos.workos_backend.entity.TaskStatus;
import com.workos.workos_backend.repository.TaskRepository;

/**
 * Business logic for GET /api/reports/completed-tasks (the Reports page's
 * "across every board in this workspace" section). A completed task is
 * exactly {@code TaskStatus.DONE} — never inferred from dueDate/end/subtasks,
 * since the Task model has no dedicated completion timestamp.
 *
 * <p>Scope is always the actor's visible boards ({@link
 * BoardService#listVisibleBoards}) — the same workspace-membership boundary
 * every other Board/Task endpoint already enforces, per the product decision
 * that CEO/Operation Manager do not get a cross-workspace bypass here. Within
 * that boundary:
 * <ul>
 *   <li>CEO and Operation Manager see every completed task.</li>
 *   <li>Every other role sees only completed tasks where they are the owner
 *       or an assignee.</li>
 * </ul>
 * The role check re-reads the actor's persisted {@code accessRole} via
 * {@link AccessRoleChecker} — never trusts client input — so a non-privileged
 * actor cannot widen their results through any request parameter.
 */
@Service
public class ReportService {

    private final BoardService boardService;
    private final TaskRepository taskRepository;
    private final AccessRoleChecker accessRoleChecker;

    public ReportService(BoardService boardService, TaskRepository taskRepository,
            AccessRoleChecker accessRoleChecker) {
        this.boardService = boardService;
        this.taskRepository = taskRepository;
        this.accessRoleChecker = accessRoleChecker;
    }

    @Transactional(readOnly = true)
    public List<Task> listCompletedTasks(String actorId) {
        List<String> boardIds = boardService.listVisibleBoards(actorId).stream()
                .map(BoardMeta::getId)
                .toList();
        if (boardIds.isEmpty()) {
            return List.of();
        }

        List<Task> completed = taskRepository.findByBoardIdInAndStatusOrderByEndDesc(boardIds, TaskStatus.DONE);

        if (accessRoleChecker.actorHasAnyRole(actorId, AccessRoles.CEO, AccessRoles.OPERATION_MANAGER)) {
            return completed;
        }
        return completed.stream()
                .filter(task -> isOwnerOrAssignee(task, actorId))
                .toList();
    }

    private static boolean isOwnerOrAssignee(Task task, String actorId) {
        if (task.getOwner().getId().equals(actorId)) {
            return true;
        }
        return task.getAssignees().stream().anyMatch(person -> person.getId().equals(actorId));
    }
}
